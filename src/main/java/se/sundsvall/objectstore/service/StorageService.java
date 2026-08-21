package se.sundsvall.objectstore.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.ContentDisposition;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.objectstore.api.model.FileMetadata;
import se.sundsvall.objectstore.api.model.ObjectListing;
import se.sundsvall.objectstore.configuration.StorageProperties;
import se.sundsvall.objectstore.integration.db.StoredFileRepository;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;
import se.sundsvall.objectstore.integration.db.model.StoredFileSummary;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static java.lang.Math.min;
import static java.lang.Math.toIntExact;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.OffsetDateTime.now;
import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.ETAG;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static se.sundsvall.objectstore.service.mapper.StoredFileMapper.toFileMetadata;
import static se.sundsvall.objectstore.service.mapper.StoredFileMapper.toObjectListing;
import static se.sundsvall.objectstore.service.mapper.StoredFileMapper.toStoredFileEntity;

@Service
public class StorageService {

	private static final Logger LOG = LoggerFactory.getLogger(StorageService.class);

	private static final String ERROR_NOT_FOUND = "No object with id [%s] exists in bucket [%s]";
	private static final String ERROR_EMPTY_CONTENT = "Content of the uploaded file must not be empty";
	private static final String ERROR_ALREADY_EXISTS = "An object with id [%s] already exists in bucket [%s]";
	private static final String ERROR_UNSUPPORTED_PRECONDITION = "Only [%s] is supported in the If-None-Match header of a store";
	private static final String ERROR_TOO_LARGE = "Content of the uploaded file exceeds the maximum size of %d bytes";
	private static final String ERROR_READ_UPLOAD = "Could not read the content of the uploaded file";
	private static final String ERROR_READ_CONTENT = "Could not read content of object with id [%s] in bucket [%s]";
	private static final String ERROR_DIGEST = "Could not compute the digest of the uploaded file";
	private static final String ERROR_INVALID_CONTENT_TYPE = "Content-Type is not a media type that can be stored";

	private static final String DIGEST_ALGORITHM = "SHA-256";

	/**
	 * The only If-None-Match a store accepts, matching S3, where the wildcard is what turns a store into a create-only
	 * one. A specific entity tag is refused rather than ignored — a client that sends one is asking for a guarantee, and
	 * silently overwriting instead is the one answer it must not get.
	 */
	private static final String CREATE_ONLY = "*";

	/**
	 * The largest array the JVM will allocate, used to keep the bounded read of the request body from overflowing an int
	 * when the configured maximum object size is larger than an array can be.
	 */
	private static final long MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8L;

	/**
	 * The characters that are stripped from the name of the uploaded file: the directory it was picked from (some clients
	 * send the full path), control characters, and the quote and backslash that would otherwise let the name break out of
	 * the Content-Disposition header.
	 */
	private static final String FILE_NAME_DIRECTORY_REGEX = ".*[/\\\\]";
	private static final String FILE_NAME_ILLEGAL_CHARACTER_REGEX = "[\\p{Cntrl}\"\\\\]";
	private static final int FILE_NAME_MAX_LENGTH = 255;

	/**
	 * The longest content type that can be stored, matching the column holding it.
	 */
	private static final int CONTENT_TYPE_MAX_LENGTH = 255;

	private final StoredFileRepository storedFileRepository;
	private final StorageProperties storageProperties;
	private final Clock clock;

	public StorageService(final StoredFileRepository storedFileRepository, final StorageProperties storageProperties, final Clock clock) {
		this.storedFileRepository = storedFileRepository;
		this.storageProperties = storageProperties;
		this.clock = clock;
	}

	/**
	 * Stores an object under the id the client chose, mirroring the shape of the S3 PutObject call — the content is taken
	 * from the raw request body and storing to an id that already holds an object replaces it wholesale, so a client that
	 * retries a store it never saw the response to ends up with exactly one object rather than two. A client that wants
	 * the opposite guarantee sends a wildcard If-None-Match and gets a 412 rather than an overwrite.
	 * <p>
	 * Deliberately not transactional. Reading the body means waiting on the client, and holding a pooled database
	 * connection for as long as a client takes to upload is what lets a handful of slow ones stall the service. The write
	 * that follows carries its own transaction.
	 *
	 * @param  bucket             the bucket to store the object in
	 * @param  id                 the id to store the object under
	 * @param  contentType        the content type sent by the client, or null when it sent none
	 * @param  contentDisposition the Content-Disposition header the name of the file is taken from, or null
	 * @param  ifNoneMatch        the If-None-Match header sent by the client, or null. A wildcard makes the store
	 *                            create-only.
	 * @param  expiresAt          the point in time when the object expires, or null to apply the configured time to live
	 * @param  request            the request holding the content
	 * @return                    the metadata of the stored object, holding the digest of the content
	 */
	public FileMetadata store(final String bucket, final String id, final String contentType, final String contentDisposition,
		final String ifNoneMatch, final OffsetDateTime expiresAt, final HttpServletRequest request) {

		final var createOnly = isCreateOnly(ifNoneMatch);
		final var storedContentType = toContentType(contentType);
		final var canonicalId = toCanonicalId(id);

		// One reading of the clock for the whole store, so that the expiry an object is checked against, the moment it
		// records as its creation and the expiry it is given are all the same point in time rather than three.
		final var timestamp = now(clock);

		// Refusing here rather than after the read keeps a store that is going to be refused from pulling its content
		// across the wire. It is not what makes the refusal correct — the primary key does that, below.
		if (createOnly && storedFileRepository.existsUnexpired(bucket, canonicalId, timestamp)) {
			throw Problem.valueOf(PRECONDITION_FAILED, ERROR_ALREADY_EXISTS.formatted(canonicalId, bucket));
		}

		final var content = readContent(request);

		if (content.length == 0) {
			throw Problem.valueOf(BAD_REQUEST, ERROR_EMPTY_CONTENT);
		}

		final var entity = toStoredFileEntity(bucket, canonicalId, toFileName(contentDisposition), storedContentType, (long) content.length,
			toEtag(content), content, timestamp, toExpiry(expiresAt, timestamp));

		if (createOnly) {
			return toFileMetadata(createExclusively(entity, timestamp));
		}

		return toFileMetadata(storedFileRepository.save(entity));
	}

	/**
	 * Writes an object to the response. The metadata is fetched first and the content only once it is clear the client is
	 * getting it, so a request answered with a 304 never pulls the content out of the database.
	 * <p>
	 * Deliberately not transactional, for the same reason as a store: writing the response means waiting on the client,
	 * and the connection that read the object has no business being held open for it.
	 *
	 * @param bucket      the bucket holding the object
	 * @param id          the id identifying the object
	 * @param ifNoneMatch the If-None-Match header sent by the client, or null
	 * @param response    the response to write to
	 */
	public void readTo(final String bucket, final String id, final String ifNoneMatch, final HttpServletResponse response) {
		final var canonicalId = toCanonicalId(id);
		final var summary = findExisting(bucket, canonicalId);

		response.addHeader(ETAG, "\"%s\"".formatted(summary.etag()));

		if (isNotModified(ifNoneMatch, summary.etag())) {
			response.setStatus(SC_NOT_MODIFIED);
			return;
		}

		final var content = ofNullable(storedFileRepository.findContent(bucket, canonicalId))
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, ERROR_NOT_FOUND.formatted(canonicalId, bucket)));

		try {
			response.addHeader(CONTENT_TYPE, ofNullable(summary.contentType()).orElse(APPLICATION_OCTET_STREAM_VALUE));
			response.addHeader(CONTENT_DISPOSITION, toContentDisposition(summary));
			response.setContentLength(content.length);

			response.getOutputStream().write(content);
		} catch (final IOException e) {
			LOG.warn("Failed to write content of object with id [{}] in bucket [{}]", canonicalId, bucket, e);
			throw Problem.valueOf(INTERNAL_SERVER_ERROR, ERROR_READ_CONTENT.formatted(canonicalId, bucket));
		}
	}

	/**
	 * Lists a page of the objects in a bucket, ordered by id. One object beyond the page is fetched to tell a full page
	 * apart from a truncated one.
	 *
	 * @param  bucket            the bucket to list
	 * @param  continuationToken the token returned by the previous page, or null to start from the beginning
	 * @param  maxKeys           the maximum number of objects to return
	 * @return                   the page of objects
	 */
	@Transactional(readOnly = true)
	public ObjectListing list(final String bucket, final String continuationToken, final int maxKeys) {
		final var page = storedFileRepository.findPage(bucket, ofNullable(continuationToken).map(StorageService::toCanonicalId).orElse(""),
			now(clock), Limit.of(maxKeys + 1));

		return toObjectListing(page.stream().limit(maxKeys).toList(), page.size() > maxKeys);
	}

	/**
	 * Deletes an object. Deletion is idempotent, mirroring the behavior of S3 — deleting a non-existing object is not an
	 * error.
	 *
	 * @param bucket the bucket holding the object
	 * @param id     the id identifying the object
	 */
	@Transactional
	public void delete(final String bucket, final String id) {
		storedFileRepository.deleteByBucketAndId(bucket, toCanonicalId(id));
	}

	/**
	 * Stores an object that no other store may already have stored under the same id, translating the refusal the
	 * database hands back into the status a client sent a create-only precondition to get.
	 *
	 * @param  entity    the object to store
	 * @param  timestamp the point in time to compare the expiry of any object already stored under the id against
	 * @return           the stored object
	 */
	private StoredFileEntity createExclusively(final StoredFileEntity entity, final OffsetDateTime timestamp) {
		try {
			storedFileRepository.createExclusively(entity, timestamp);
		} catch (final DataIntegrityViolationException e) {
			throw Problem.valueOf(PRECONDITION_FAILED, ERROR_ALREADY_EXISTS.formatted(entity.getId(), entity.getBucket()));
		}

		return entity;
	}

	/**
	 * Reads the If-None-Match header of a store. The only value a store accepts is a wildcard, and anything else is
	 * refused rather than ignored.
	 *
	 * @param  ifNoneMatch the header sent by the client, or null
	 * @return             whether the client asked for the store to be create-only
	 */
	private static boolean isCreateOnly(final String ifNoneMatch) {
		return ofNullable(ifNoneMatch)
			.map(String::strip)
			.map(precondition -> {
				if (!CREATE_ONLY.equals(precondition)) {
					throw Problem.valueOf(BAD_REQUEST, ERROR_UNSUPPORTED_PRECONDITION.formatted(CREATE_ONLY));
				}
				return true;
			})
			.orElse(false);
	}

	/**
	 * Canonicalizes the id an object is addressed by. A UUID carries no case, so an id that differs only in case from one
	 * already stored names the same object and is lowercased to say so. Without it the two would be separate objects, and
	 * the object a client stored under an uppercase id would be invisible to a read that spelled the id in lowercase.
	 *
	 * @param  id the id sent by the client
	 * @return    the id the object is stored under
	 */
	private static String toCanonicalId(final String id) {
		return id.toLowerCase(Locale.ROOT);
	}

	/**
	 * Reads the Content-Type header of a store. The value is replayed as the content type of every later read, so one
	 * that cannot be stored whole is refused rather than truncated or dropped — a client that is told its object was
	 * stored is entitled to get the type it sent back, and both alternatives quietly hand back a different one.
	 *
	 * @param  contentType the header sent by the client, or null
	 * @return             the content type to store, or null when the client sent none
	 */
	private static String toContentType(final String contentType) {
		return ofNullable(contentType)
			.map(String::strip)
			.filter(not(String::isEmpty))
			.map(value -> {
				if ((value.length() > CONTENT_TYPE_MAX_LENGTH) || !isMediaType(value)) {
					throw Problem.valueOf(BAD_REQUEST, ERROR_INVALID_CONTENT_TYPE);
				}
				return value;
			})
			.orElse(null);
	}

	private static boolean isMediaType(final String value) {
		try {
			MediaType.parseMediaType(value);
			return true;
		} catch (final InvalidMediaTypeException e) {
			return false;
		}
	}

	/**
	 * Names the file a read returns, encoded the way RFC 6266 asks for rather than written into the header as it stands.
	 * A response header is written as ISO-8859-1, and a name carrying anything outside it makes the container drop the
	 * whole header, leaving the client with no name at all rather than an approximate one.
	 *
	 * @param  summary the metadata of the object being read
	 * @return         the value of the Content-Disposition header
	 */
	private static String toContentDisposition(final StoredFileSummary summary) {
		return ContentDisposition.attachment()
			.filename(ofNullable(summary.fileName()).orElse(summary.id()), UTF_8)
			.build()
			.toString();
	}

	private StoredFileSummary findExisting(final String bucket, final String id) {
		return storedFileRepository.findSummary(bucket, id)
			.filter(not(this::isExpired))
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, ERROR_NOT_FOUND.formatted(id, bucket)));
	}

	private boolean isExpired(final StoredFileSummary summary) {
		return ofNullable(summary.expiresAt())
			.map(expiry -> expiry.isBefore(now(clock)))
			.orElse(false);
	}

	private OffsetDateTime toExpiry(final OffsetDateTime expiresAt, final OffsetDateTime timestamp) {
		return ofNullable(expiresAt)
			.orElseGet(() -> ofNullable(storageProperties.defaultTimeToLive())
				.map(timestamp::plus)
				.orElse(null));
	}

	/**
	 * Reads the request body, refusing anything larger than the configured maximum object size. The declared content
	 * length is only a hint — a chunked request declares none and a lying one declares the wrong one — so the read itself
	 * is bounded rather than trusted.
	 *
	 * @param  request the request holding the content
	 * @return         the content of the request body
	 */
	private byte[] readContent(final HttpServletRequest request) {
		final var maxBytes = storageProperties.maxObjectSize().toBytes();

		if (request.getContentLengthLong() > maxBytes) {
			throw Problem.valueOf(CONTENT_TOO_LARGE, ERROR_TOO_LARGE.formatted(maxBytes));
		}

		final byte[] content;
		try {
			content = request.getInputStream().readNBytes(toIntExact(min(maxBytes + 1, MAX_ARRAY_LENGTH)));
		} catch (final IOException e) {
			LOG.warn("Failed to read the content of an upload to bucket [{}]", request.getRequestURI(), e);
			throw Problem.valueOf(BAD_REQUEST, ERROR_READ_UPLOAD);
		}

		if (content.length > maxBytes) {
			throw Problem.valueOf(CONTENT_TOO_LARGE, ERROR_TOO_LARGE.formatted(maxBytes));
		}

		return content;
	}

	private static String toEtag(final byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(content));
		} catch (final NoSuchAlgorithmException e) {
			LOG.error("The [{}] digest algorithm is not available", DIGEST_ALGORITHM, e);
			throw Problem.valueOf(INTERNAL_SERVER_ERROR, ERROR_DIGEST);
		}
	}

	/**
	 * Matches an If-None-Match header against the digest of an object. The header may be a wildcard or a comma separated
	 * list of entity tags, each of which may be weak.
	 *
	 * @param  ifNoneMatch the header sent by the client, or null
	 * @param  etag        the digest of the object
	 * @return             whether the object is unchanged as far as the client is concerned
	 */
	private static boolean isNotModified(final String ifNoneMatch, final String etag) {
		return ofNullable(ifNoneMatch)
			.map(String::strip)
			.map(header -> "*".equals(header) || stream(header.split(","))
				.map(String::strip)
				.map(candidate -> candidate.replaceFirst("^W/", "").replace("\"", ""))
				.anyMatch(etag::equals))
			.orElse(false);
	}

	/**
	 * Derives the stored file name from the Content-Disposition header the client sent with the upload. The name is only
	 * ever echoed back in the Content-Disposition header of a read and is never used to address the object, but it is
	 * client controlled and is sanitized accordingly.
	 *
	 * @param  contentDisposition the header sent by the client, or null
	 * @return                    the file name to store, or null when the client sent nothing usable
	 */
	private static String toFileName(final String contentDisposition) {
		return ofNullable(contentDisposition)
			.map(StorageService::parseFileName)
			.map(name -> name.replaceAll(FILE_NAME_DIRECTORY_REGEX, ""))
			.map(name -> name.replaceAll(FILE_NAME_ILLEGAL_CHARACTER_REGEX, ""))
			.map(String::strip)
			.filter(not(String::isEmpty))
			.map(name -> name.substring(0, min(name.length(), FILE_NAME_MAX_LENGTH)))
			.orElse(null);
	}

	private static String parseFileName(final String contentDisposition) {
		try {
			return ContentDisposition.parse(contentDisposition).getFilename();
		} catch (final IllegalArgumentException e) {
			LOG.info("Ignoring malformed Content-Disposition header [{}]", contentDisposition);
			return null;
		}
	}
}
