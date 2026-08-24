package se.sundsvall.objectstore.integration.db.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.sql.Blob;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.hibernate.annotations.TimeZoneStorage;

import static jakarta.persistence.FetchType.LAZY;
import static org.hibernate.annotations.TimeZoneStorageType.NORMALIZE;

@Entity
@Table(name = "stored_file",
	indexes = {
		@Index(name = "ix_stored_file_bucket_id", columnList = "bucket, id"),
		@Index(name = "ix_stored_file_expires_at", columnList = "expires_at")
	})
public class StoredFileEntity {

	@Id
	@Column(name = "id", nullable = false, updatable = false, length = 36)
	private String id;

	@Column(name = "bucket", nullable = false, length = 63)
	private String bucket;

	@Column(name = "file_name", length = 255)
	private String fileName;

	@Column(name = "content_type", length = 255)
	private String contentType;

	@Column(name = "size_in_bytes", nullable = false)
	private Long sizeInBytes;

	@Column(name = "etag", nullable = false, length = 64)
	private String etag;

	@Basic(fetch = LAZY)
	@Lob
	@Column(name = "content", columnDefinition = "longblob")
	private Blob content;

	@Column(name = "created", nullable = false)
	@TimeZoneStorage(NORMALIZE)
	private OffsetDateTime created;

	@Column(name = "expires_at")
	@TimeZoneStorage(NORMALIZE)
	private OffsetDateTime expiresAt;

	public static StoredFileEntity create() {
		return new StoredFileEntity();
	}

	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public StoredFileEntity withId(final String id) {
		this.id = id;
		return this;
	}

	public String getBucket() {
		return bucket;
	}

	public void setBucket(final String bucket) {
		this.bucket = bucket;
	}

	public StoredFileEntity withBucket(final String bucket) {
		this.bucket = bucket;
		return this;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(final String fileName) {
		this.fileName = fileName;
	}

	public StoredFileEntity withFileName(final String fileName) {
		this.fileName = fileName;
		return this;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(final String contentType) {
		this.contentType = contentType;
	}

	public StoredFileEntity withContentType(final String contentType) {
		this.contentType = contentType;
		return this;
	}

	public Long getSizeInBytes() {
		return sizeInBytes;
	}

	public void setSizeInBytes(final Long sizeInBytes) {
		this.sizeInBytes = sizeInBytes;
	}

	public StoredFileEntity withSizeInBytes(final Long sizeInBytes) {
		this.sizeInBytes = sizeInBytes;
		return this;
	}

	public String getEtag() {
		return etag;
	}

	public void setEtag(final String etag) {
		this.etag = etag;
	}

	public StoredFileEntity withEtag(final String etag) {
		this.etag = etag;
		return this;
	}

	public Blob getContent() {
		return content;
	}

	public void setContent(final Blob content) {
		this.content = content;
	}

	public StoredFileEntity withContent(final Blob content) {
		this.content = content;
		return this;
	}

	public OffsetDateTime getCreated() {
		return created;
	}

	public void setCreated(final OffsetDateTime created) {
		this.created = created;
	}

	public StoredFileEntity withCreated(final OffsetDateTime created) {
		this.created = created;
		return this;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(final OffsetDateTime expiresAt) {
		this.expiresAt = expiresAt;
	}

	public StoredFileEntity withExpiresAt(final OffsetDateTime expiresAt) {
		this.expiresAt = expiresAt;
		return this;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final StoredFileEntity that = (StoredFileEntity) o;
		return Objects.equals(id, that.id) && Objects.equals(bucket, that.bucket) && Objects.equals(fileName, that.fileName)
			&& Objects.equals(contentType, that.contentType) && Objects.equals(sizeInBytes, that.sizeInBytes)
			&& Objects.equals(etag, that.etag) && Objects.equals(content, that.content) && Objects.equals(created, that.created)
			&& Objects.equals(expiresAt, that.expiresAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, bucket, fileName, contentType, sizeInBytes, etag, content, created, expiresAt);
	}

	@Override
	public String toString() {
		return "StoredFileEntity{" +
			"id='" + id + '\'' +
			", bucket='" + bucket + '\'' +
			", fileName='" + fileName + '\'' +
			", contentType='" + contentType + '\'' +
			", sizeInBytes=" + sizeInBytes +
			", etag='" + etag + '\'' +
			", content=" + content +
			", created=" + created +
			", expiresAt=" + expiresAt +
			'}';
	}
}
