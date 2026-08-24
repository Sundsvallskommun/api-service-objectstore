package se.sundsvall.objectstore.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.objectstore.api.model.FileMetadata;
import se.sundsvall.objectstore.api.model.ObjectListing;
import se.sundsvall.objectstore.configuration.StorageProperties;
import se.sundsvall.objectstore.integration.db.StoredFileRepository;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;
import se.sundsvall.objectstore.service.util.BlobUtil;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static java.lang.Math.min;
import static java.lang.Math.toIntExact;
import static java.time.OffsetDateTime.now;
import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.ETAG;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static org.springframework.util.StreamUtils.copy;
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
	private static final String CONTENT_DISPOSITION_VALUE = "attachment; filename=\"%s\"";

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

	private final StoredFileRepository storedFileRepository;
	private final BlobUtil blobUtil;
	private final StorageProperties storageProperties;

	public StorageService(final StoredFileRepository storedFileRepository, final BlobUtil blobUtil, final StorageProperties storageProperties) {
		this.storedFileRepository = storedFileRepository;
		this.blobUtil = blobUtil;
		this.storageProperties = storageProperties;
	}

	/**
	 * Stores an object under the id the client chose, mirroring the shape of the S3 PutObject call — the content is taken
	 * from the raw request body and storing to an id that already holds an object replaces it wholesale, so a client that
	 * retries a store it never saw the response to ends up with exactly one object rather than two. A client that wants
	 * the opposite guarantee sends a wildcard If-None-Match and gets a 412 rather than an overwrite.
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
	@Transactional
	public FileMetadata store(final String bucket, final String id, final String contentType, final String contentDisposition,
		final String ifNoneMatch, final OffsetDateTime expiresAt, final HttpServletRequest request) {

		verifyPrecondition(bucket, id, ifNoneMatch);

		final var content = readContent(request);

		if (content.length == 0) {
			throw Problem.valueOf(BAD_REQUEST, ERROR_EMPTY_CONTENT);
		}

		final var entity = toStoredFileEntity(bucket, id, toFileName(contentDisposition), contentType, (long) content.length,
			toEtag(content), blobUtil.toBlob(content), now(), toExpiry(expiresAt));

		return toFileMetadata(storedFileRepository.save(entity));
	}

	/**
	 * Writes an object to the response. When the client sends an If-None-Match that matches the digest of the object the
	 * response is a bare 304 with no body — the row is still fetched, since the MariaDB driver materializes a BLOB along
	 * with it, but nothing is streamed back.
	 *
	 * @param bucket      the bucket holding the object
	 * @param id          the id identifying the object
	 * @param ifNoneMatch the If-None-Match header sent by the client, or null
	 * @param response    the response to write to
	 */
	@Transactional(readOnly = true)
	public void readTo(final String bucket, final String id, final String ifNoneMatch, final HttpServletResponse response) {
		final var entity = findExisting(bucket, id);

		response.addHeader(ETAG, "\"%s\"".formatted(entity.getEtag()));

		if (isNotModified(ifNoneMatch, entity.getEtag())) {
			response.setStatus(SC_NOT_MODIFIED);
			return;
		}

		try {
			final var content = entity.getContent();

			response.addHeader(CONTENT_TYPE, ofNullable(entity.getContentType()).orElse(APPLICATION_OCTET_STREAM_VALUE));
			response.addHeader(CONTENT_DISPOSITION, CONTENT_DISPOSITION_VALUE.formatted(ofNullable(entity.getFileName()).orElse(entity.getId())));
			response.setContentLengthLong(content.length());

			copy(content.getBinaryStream(), response.getOutputStream());
		} catch (final SQLException | IOException e) {
			LOG.warn("Failed to read content of object with id [{}] in bucket [{}]", id, bucket, e);
			throw Problem.valueOf(INTERNAL_SERVER_ERROR, ERROR_READ_CONTENT.formatted(id, bucket));
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
		final var page = storedFileRepository.findPage(bucket, ofNullable(continuationToken).orElse(""), now(), Limit.of(maxKeys + 1));

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
		storedFileRepository.findByBucketAndId(bucket, id)
			.ifPresent(storedFileRepository::delete);
	}

	/**
	 * Refuses a store that carries a create-only precondition the bucket cannot satisfy. The check runs before the request
	 * body is read, so a refused store never pulls the content across the wire. An expired object does not stand in the
	 * way of one, since it is already invisible to every read.
	 *
	 * @param bucket      the bucket to store the object in
	 * @param id          the id to store the object under
	 * @param ifNoneMatch the If-None-Match header sent by the client, or null
	 */
	private void verifyPrecondition(final String bucket, final String id, final String ifNoneMatch) {
		ofNullable(ifNoneMatch)
			.map(String::strip)
			.ifPresent(precondition -> {
				if (!CREATE_ONLY.equals(precondition)) {
					throw Problem.valueOf(BAD_REQUEST, ERROR_UNSUPPORTED_PRECONDITION.formatted(CREATE_ONLY));
				}
				if (storedFileRepository.existsUnexpired(bucket, id, now())) {
					throw Problem.valueOf(PRECONDITION_FAILED, ERROR_ALREADY_EXISTS.formatted(id, bucket));
				}
			});
	}

	private StoredFileEntity findExisting(final String bucket, final String id) {
		return storedFileRepository.findByBucketAndId(bucket, id)
			.filter(not(StorageService::isExpired))
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, ERROR_NOT_FOUND.formatted(id, bucket)));
	}

	private static boolean isExpired(final StoredFileEntity entity) {
		return ofNullable(entity.getExpiresAt())
			.map(expiry -> expiry.isBefore(now()))
			.orElse(false);
	}

	private OffsetDateTime toExpiry(final OffsetDateTime expiresAt) {
		return ofNullable(expiresAt)
			.orElseGet(() -> ofNullable(storageProperties.defaultTimeToLive())
				.map(now()::plus)
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
