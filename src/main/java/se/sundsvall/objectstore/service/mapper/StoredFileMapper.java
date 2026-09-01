package se.sundsvall.objectstore.service.mapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.objectstore.api.model.FileMetadata;
import se.sundsvall.objectstore.api.model.ObjectListing;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;
import se.sundsvall.objectstore.integration.db.model.StoredFileSummary;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

public final class StoredFileMapper {

	private static final Logger LOG = LoggerFactory.getLogger(StoredFileMapper.class);

	private static final String ERROR_DIGEST = "Could not compute the digest of the uploaded file";

	private static final String DIGEST_ALGORITHM = "SHA-256";

	private StoredFileMapper() {}

	/**
	 * Maps an object about to be stored. The size and the digest are derived from the content rather than passed in, so
	 * that neither can disagree with the content they describe.
	 *
	 * @param  bucket      the bucket to store the object in
	 * @param  id          the id to store the object under
	 * @param  fileName    the name of the file, or null when the client sent none
	 * @param  contentType the content type of the object
	 * @param  content     the content of the object
	 * @param  created     the point in time the object is stored
	 * @param  expiresAt   the point in time the object expires, or null when it never does
	 * @return             the object to store
	 */
	public static StoredFileEntity toStoredFileEntity(final String bucket, final String id, final String fileName,
		final String contentType, final byte[] content, final OffsetDateTime created, final OffsetDateTime expiresAt) {

		return StoredFileEntity.create()
			.withId(id)
			.withBucket(bucket)
			.withFileName(fileName)
			.withContentType(contentType)
			.withSizeInBytes((long) content.length)
			.withEtag(toEtag(content))
			.withContent(content)
			.withCreated(created)
			.withExpiresAt(expiresAt);
	}

	public static FileMetadata toFileMetadata(final StoredFileEntity entity) {
		return ofNullable(entity)
			.map(storedFile -> FileMetadata.create()
				.withId(storedFile.getId())
				.withBucket(storedFile.getBucket())
				.withFileName(storedFile.getFileName())
				.withContentType(storedFile.getContentType())
				.withSize(storedFile.getSizeInBytes())
				.withEtag(storedFile.getEtag())
				.withCreated(storedFile.getCreated())
				.withExpiresAt(storedFile.getExpiresAt()))
			.orElse(null);
	}

	public static FileMetadata toFileMetadata(final StoredFileSummary summary) {
		return ofNullable(summary)
			.map(storedFile -> FileMetadata.create()
				.withId(storedFile.id())
				.withBucket(storedFile.bucket())
				.withFileName(storedFile.fileName())
				.withContentType(storedFile.contentType())
				.withSize(storedFile.sizeInBytes())
				.withEtag(storedFile.etag())
				.withCreated(storedFile.created())
				.withExpiresAt(storedFile.expiresAt()))
			.orElse(null);
	}

	public static ObjectListing toObjectListing(final List<StoredFileSummary> summaries, final boolean truncated) {
		final var objects = ofNullable(summaries).orElse(emptyList()).stream()
			.map(StoredFileMapper::toFileMetadata)
			.toList();

		return ObjectListing.create()
			.withObjects(objects)
			.withTruncated(truncated)
			.withNextContinuationToken(toNextContinuationToken(objects, truncated));
	}

	private static String toEtag(final byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(content));
		} catch (final NoSuchAlgorithmException e) {
			LOG.error("The [{}] digest algorithm is not available", DIGEST_ALGORITHM, e);
			throw Problem.valueOf(INTERNAL_SERVER_ERROR, ERROR_DIGEST);
		}
	}

	private static String toNextContinuationToken(final List<FileMetadata> objects, final boolean truncated) {
		if (!truncated || objects.isEmpty()) {
			return null;
		}
		return objects.getLast().getId();
	}
}
