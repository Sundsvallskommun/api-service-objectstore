package se.sundsvall.objectstore.integration.db;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;
import se.sundsvall.objectstore.integration.db.model.StoredFileSummary;

@CircuitBreaker(name = "storedFileRepository")
public interface StoredFileRepository extends JpaRepository<StoredFileEntity, String> {

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
	 * Finds the ids of all objects that have expired. Only the ids are fetched to avoid loading the (potentially large)
	 * content of every expired object into memory.
	 *
	 * @param  timestamp the point in time to compare the expiry against
	 * @return           the ids of all objects that expired before the given timestamp
	 */
	@Query("select entity.id from StoredFileEntity entity where entity.expiresAt is not null and entity.expiresAt < :timestamp")
	List<String> findExpiredIds(@Param("timestamp") OffsetDateTime timestamp);
}
