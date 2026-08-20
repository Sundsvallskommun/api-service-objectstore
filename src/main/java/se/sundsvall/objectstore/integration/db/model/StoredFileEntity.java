package se.sundsvall.objectstore.integration.db.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import org.hibernate.annotations.TimeZoneStorage;

import static org.hibernate.annotations.TimeZoneStorageType.NORMALIZE;

@Entity
@Table(name = "stored_file",
	indexes = {
		@Index(name = "ix_stored_file_expires_at", columnList = "expires_at")
	})
@IdClass(StoredFileId.class)
public class StoredFileEntity {

	/**
	 * The bucket is part of the primary key rather than an ordinary column. Keying on the id alone would let a store
	 * into one bucket collide with an object another bucket already holds, and since a store is a merge, the collision
	 * would move the existing object rather than being refused.
	 */
	@Id
	@Column(name = "bucket", nullable = false, updatable = false, length = 63)
	private String bucket;

	@Id
	@Column(name = "id", nullable = false, updatable = false, length = 36)
	private String id;

	@Column(name = "file_name", length = 255)
	private String fileName;

	@Column(name = "content_type", length = 255)
	private String contentType;

	@Column(name = "size_in_bytes", nullable = false)
	private Long sizeInBytes;

	@Column(name = "etag", nullable = false, length = 64)
	private String etag;

	/**
	 * The content is fetched along with the row whenever the entity is loaded — the MariaDB driver materializes a BLOB
	 * as soon as it reads the row it belongs to, so declaring it lazy would be a promise the driver does not keep.
	 * Anything that does not want the content selects a projection instead of the entity.
	 */
	@Lob
	@Column(name = "content", columnDefinition = "longblob")
	private byte[] content;

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

	public byte[] getContent() {
		return content;
	}

	public void setContent(final byte[] content) {
		this.content = content;
	}

	public StoredFileEntity withContent(final byte[] content) {
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
			&& Objects.equals(etag, that.etag) && Arrays.equals(content, that.content) && Objects.equals(created, that.created)
			&& Objects.equals(expiresAt, that.expiresAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, bucket, fileName, contentType, sizeInBytes, etag, Arrays.hashCode(content), created, expiresAt);
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
			", content=" + (content == null ? "null" : content.length + " bytes") +
			", created=" + created +
			", expiresAt=" + expiresAt +
			'}';
	}
}
