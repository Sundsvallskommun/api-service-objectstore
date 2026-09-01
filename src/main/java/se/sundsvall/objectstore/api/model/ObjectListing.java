package se.sundsvall.objectstore.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

import static io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY;

@Schema(description = "A page of the objects in a bucket")
public class ObjectListing {

	@Schema(description = "The objects in this page, ordered by id", accessMode = READ_ONLY)
	private List<FileMetadata> objects;

	@Schema(description = "Whether more objects exist beyond this page", examples = "false", accessMode = READ_ONLY)
	private boolean truncated;

	@Schema(description = "The token to pass as continuationToken to fetch the next page. Null when the listing is not truncated.", examples = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b", accessMode = READ_ONLY)
	private String nextContinuationToken;

	public static ObjectListing create() {
		return new ObjectListing();
	}

	public List<FileMetadata> getObjects() {
		return objects;
	}

	public void setObjects(final List<FileMetadata> objects) {
		this.objects = objects;
	}

	public ObjectListing withObjects(final List<FileMetadata> objects) {
		this.objects = objects;
		return this;
	}

	public boolean isTruncated() {
		return truncated;
	}

	public void setTruncated(final boolean truncated) {
		this.truncated = truncated;
	}

	public ObjectListing withTruncated(final boolean truncated) {
		this.truncated = truncated;
		return this;
	}

	public String getNextContinuationToken() {
		return nextContinuationToken;
	}

	public void setNextContinuationToken(final String nextContinuationToken) {
		this.nextContinuationToken = nextContinuationToken;
	}

	public ObjectListing withNextContinuationToken(final String nextContinuationToken) {
		this.nextContinuationToken = nextContinuationToken;
		return this;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final ObjectListing that = (ObjectListing) o;
		return truncated == that.truncated && Objects.equals(objects, that.objects)
			&& Objects.equals(nextContinuationToken, that.nextContinuationToken);
	}

	@Override
	public int hashCode() {
		return Objects.hash(objects, truncated, nextContinuationToken);
	}

	@Override
	public String toString() {
		return "ObjectListing{" +
			"objects=" + objects +
			", truncated=" + truncated +
			", nextContinuationToken='" + nextContinuationToken + '\'' +
			'}';
	}
}
