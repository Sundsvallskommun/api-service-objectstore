package se.sundsvall.objectstore.integration.db.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * The identity of a stored object. An object is addressed by the bucket holding it together with the id the client
 * chose, so the two form the primary key — an id identifies an object only within its bucket, and the same id may be
 * stored in any number of buckets without the objects having anything to do with one another.
 */
public class StoredFileId implements Serializable {

	private String bucket;

	private String id;

	public static StoredFileId create() {
		return new StoredFileId();
	}

	public String getBucket() {
		return bucket;
	}

	public void setBucket(final String bucket) {
		this.bucket = bucket;
	}

	public StoredFileId withBucket(final String bucket) {
		this.bucket = bucket;
		return this;
	}

	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public StoredFileId withId(final String id) {
		this.id = id;
		return this;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final StoredFileId that = (StoredFileId) o;
		return Objects.equals(bucket, that.bucket) && Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(bucket, id);
	}

	@Override
	public String toString() {
		return "StoredFileId{" +
			"bucket='" + bucket + '\'' +
			", id='" + id + '\'' +
			'}';
	}
}
