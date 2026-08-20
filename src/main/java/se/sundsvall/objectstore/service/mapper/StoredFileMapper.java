package se.sundsvall.objectstore.service.mapper;

import java.time.OffsetDateTime;
import java.util.List;
import se.sundsvall.objectstore.api.model.FileMetadata;
import se.sundsvall.objectstore.api.model.ObjectListing;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;
import se.sundsvall.objectstore.integration.db.model.StoredFileSummary;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

public final class StoredFileMapper {

	private StoredFileMapper() {}

	public static StoredFileEntity toStoredFileEntity(final String bucket, final String id, final String fileName,
		final String contentType, final Long sizeInBytes, final String etag, final byte[] content, final OffsetDateTime created,
		final OffsetDateTime expiresAt) {

		return StoredFileEntity.create()
			.withId(id)
			.withBucket(bucket)
			.withFileName(fileName)
			.withContentType(contentType)
			.withSizeInBytes(sizeInBytes)
			.withEtag(etag)
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

	private static String toNextContinuationToken(final List<FileMetadata> objects, final boolean truncated) {
		if (!truncated || objects.isEmpty()) {
			return null;
		}
		return objects.getLast().getId();
	}
}
