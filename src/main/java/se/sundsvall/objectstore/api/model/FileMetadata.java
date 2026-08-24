package se.sundsvall.objectstore.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.format.annotation.DateTimeFormat;

import static io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY;
import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME;

@Schema(description = "Metadata for a stored object")
public class FileMetadata {

	@Schema(description = "Id identifying the object within the bucket", examples = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b", accessMode = READ_ONLY)
	private String id;

	@Schema(description = "Bucket holding the object", examples = "attachments", accessMode = READ_ONLY)
	private String bucket;

	@Schema(description = "Name of the uploaded file", examples = "invoice-123.pdf", accessMode = READ_ONLY)
	private String fileName;

	@Schema(description = "Content type of the object", examples = "application/pdf", accessMode = READ_ONLY)
	private String contentType;

	@Schema(description = "Size of the object in bytes", examples = "20971", accessMode = READ_ONLY)
	private Long size;

	@Schema(description = "Hex encoded SHA-256 digest of the content, also returned in the ETag header", examples = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", accessMode = READ_ONLY)
	private String etag;

	@Schema(description = "Timestamp when the object was stored", examples = "2026-08-18T14:30:00+02:00", accessMode = READ_ONLY)
	@DateTimeFormat(iso = DATE_TIME)
	private OffsetDateTime created;

	@Schema(description = "Timestamp when the object expires and becomes eligible for removal", examples = "2026-08-25T14:30:00+02:00", accessMode = READ_ONLY)
	@DateTimeFormat(iso = DATE_TIME)
	private OffsetDateTime expiresAt;

	public static FileMetadata create() {
		return new FileMetadata();
	}

	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public FileMetadata withId(final String id) {
		this.id = id;
		return this;
	}

	public String getBucket() {
		return bucket;
	}

	public void setBucket(final String bucket) {
		this.bucket = bucket;
	}

	public FileMetadata withBucket(final String bucket) {
		this.bucket = bucket;
		return this;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(final String fileName) {
		this.fileName = fileName;
	}

	public FileMetadata withFileName(final String fileName) {
		this.fileName = fileName;
		return this;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(final String contentType) {
		this.contentType = contentType;
	}

	public FileMetadata withContentType(final String contentType) {
		this.contentType = contentType;
		return this;
	}

	public Long getSize() {
		return size;
	}

	public void setSize(final Long size) {
		this.size = size;
	}

	public FileMetadata withSize(final Long size) {
		this.size = size;
		return this;
	}

	public String getEtag() {
		return etag;
	}

	public void setEtag(final String etag) {
		this.etag = etag;
	}

	public FileMetadata withEtag(final String etag) {
		this.etag = etag;
		return this;
	}

	public OffsetDateTime getCreated() {
		return created;
	}

	public void setCreated(final OffsetDateTime created) {
		this.created = created;
	}

	public FileMetadata withCreated(final OffsetDateTime created) {
		this.created = created;
		return this;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(final OffsetDateTime expiresAt) {
		this.expiresAt = expiresAt;
	}

	public FileMetadata withExpiresAt(final OffsetDateTime expiresAt) {
		this.expiresAt = expiresAt;
		return this;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final FileMetadata that = (FileMetadata) o;
		return Objects.equals(id, that.id) && Objects.equals(bucket, that.bucket) && Objects.equals(fileName, that.fileName)
			&& Objects.equals(contentType, that.contentType) && Objects.equals(size, that.size) && Objects.equals(etag, that.etag)
			&& Objects.equals(created, that.created) && Objects.equals(expiresAt, that.expiresAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, bucket, fileName, contentType, size, etag, created, expiresAt);
	}

	@Override
	public String toString() {
		return "FileMetadata{" +
			"id='" + id + '\'' +
			", bucket='" + bucket + '\'' +
			", fileName='" + fileName + '\'' +
			", contentType='" + contentType + '\'' +
			", size=" + size +
			", etag='" + etag + '\'' +
			", created=" + created +
			", expiresAt=" + expiresAt +
			'}';
	}
}
