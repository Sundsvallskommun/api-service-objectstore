package se.sundsvall.objectstore.integration.db;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;
import se.sundsvall.objectstore.integration.db.model.StoredFileId;
import se.sundsvall.objectstore.integration.db.model.StoredFileSummary;

@CircuitBreaker(name = "storedFileRepository")
public interface StoredFileRepository extends JpaRepository<StoredFileEntity, StoredFileId>, StoredFileRepositoryCustom {

	Optional<StoredFileEntity> findByBucketAndId(String bucket, String id);

	/**
	 * Answers whether a bucket holds an object under an id that has not expired. Counting rather than fetching the entity
	 * keeps the content out of memory — the MariaDB driver materializes a BLOB as soon as its row is read.
	 *
	 * @param  bucket    the bucket holding the object
	 * @param  id        the id identifying the object
	 * @param  timestamp the point in time to compare the expiry against
	 * @return           whether an object that has not expired is stored under the id
	 */
	@Query("""
		select count(entity) > 0
		from StoredFileEntity entity
		where entity.bucket = :bucket
		and entity.id = :id
		and (entity.expiresAt is null or entity.expiresAt > :timestamp)
		""")
	boolean existsUnexpired(@Param("bucket") String bucket, @Param("id") String id, @Param("timestamp") OffsetDateTime timestamp);

	/**
	 * Finds the metadata of a single object, without its content. Reads start here so that a request answered with a 304
	 * never pulls the content out of the database at all, and so that the content of a request that is answered with the
	 * object is fetched separately, outside the transaction that decided the object exists.
	 *
	 * @param  bucket the bucket holding the object
	 * @param  id     the id identifying the object
	 * @return        the metadata of the object, or empty when the bucket holds no object under the id
	 */
	@Query("""
		select new se.sundsvall.objectstore.integration.db.model.StoredFileSummary(
			entity.id, entity.bucket, entity.fileName, entity.contentType, entity.sizeInBytes, entity.etag, entity.created, entity.expiresAt)
		from StoredFileEntity entity
		where entity.bucket = :bucket
		and entity.id = :id
		""")
	Optional<StoredFileSummary> findSummary(@Param("bucket") String bucket, @Param("id") String id);

	/**
	 * Finds the content of a single object and nothing else.
	 *
	 * @param  bucket the bucket holding the object
	 * @param  id     the id identifying the object
	 * @return        the content of the object, or null when the bucket holds no object under the id
	 */
	@Query("select entity.content from StoredFileEntity entity where entity.bucket = :bucket and entity.id = :id")
	byte[] findContent(@Param("bucket") String bucket, @Param("id") String id);

	/**
	 * Finds a page of the objects in a bucket, ordered by id, mirroring the lexicographic ordering by key that S3 lists
	 * with. Objects that have expired are excluded here rather than in the service so that a page is never silently
	 * shorter than it claims to be.
	 *
	 * @param  bucket            the bucket to list
	 * @param  continuationToken the id the previous page ended with, or an empty string to start from the beginning
	 * @param  timestamp         the point in time to compare the expiry against
	 * @param  limit             the maximum number of objects to return
	 * @return                   the objects in the bucket, without their content
	 */
	@Query("""
		select new se.sundsvall.objectstore.integration.db.model.StoredFileSummary(
			entity.id, entity.bucket, entity.fileName, entity.contentType, entity.sizeInBytes, entity.etag, entity.created, entity.expiresAt)
		from StoredFileEntity entity
		where entity.bucket = :bucket
		and entity.id > :continuationToken
		and (entity.expiresAt is null or entity.expiresAt > :timestamp)
		order by entity.id asc
		""")
	List<StoredFileSummary> findPage(@Param("bucket") String bucket, @Param("continuationToken") String continuationToken,
		@Param("timestamp") OffsetDateTime timestamp, Limit limit);

	/**
	 * Deletes an object. A statement rather than a load followed by a remove, so that deleting a large object does not
	 * pull its content into memory on the way out.
	 *
	 * @param  bucket the bucket holding the object
	 * @param  id     the id identifying the object
	 * @return        the number of deleted objects, which is one or zero
	 */
	@Modifying
	@Query("delete from StoredFileEntity entity where entity.bucket = :bucket and entity.id = :id")
	int deleteByBucketAndId(@Param("bucket") String bucket, @Param("id") String id);

	/**
	 * Deletes every object whose expiry has passed, in one statement. Selecting the expired objects first would load the
	 * content of every one of them into memory only to throw it away.
	 *
	 * @param  timestamp the point in time to compare the expiry against
	 * @return           the number of deleted objects
	 */
	@Modifying
	@Query("delete from StoredFileEntity entity where entity.expiresAt is not null and entity.expiresAt < :timestamp")
	int deleteExpired(@Param("timestamp") OffsetDateTime timestamp);
}
