package se.sundsvall.objectstore.integration.db.model;

import java.time.OffsetDateTime;

/**
 * The metadata of a stored object, without its content. Listing selects this rather than the entity since the MariaDB
 * driver materializes a BLOB as soon as its row is read — listing entities would pull the content of every object in
 * the
 * page into memory only to throw it away.
 *
 * @param id          the id identifying the object
 * @param bucket      the bucket holding the object
 * @param fileName    the name of the uploaded file, or null when the client sent nothing usable
 * @param contentType the content type of the object, or null when the client sent none
 * @param sizeInBytes the size of the object
 * @param etag        the hex encoded SHA-256 digest of the content
 * @param created     the point in time when the object was stored
 * @param expiresAt   the point in time when the object expires, or null when it never does
 */
public record StoredFileSummary(
	String id,
	String bucket,
	String fileName,
	String contentType,
	Long sizeInBytes,
	String etag,
	OffsetDateTime created,
	OffsetDateTime expiresAt) {
}
