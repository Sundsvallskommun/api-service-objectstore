package se.sundsvall.objectstore.integration.db;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;

@CircuitBreaker(name = "storedFileRepository")
public interface StoredFileRepositoryCustom {

	/**
	 * Stores an object only if its bucket does not already hold one under the same id, leaving the decision to the
	 * primary key rather than to a check made beforehand. Two simultaneous stores of the same id therefore cannot both
	 * succeed, which a check followed by a save allows.
	 *
	 * @param  entity                          the object to store
	 * @param  timestamp                       the point in time to compare the expiry of any object already stored under
	 *                                         the id against
	 * @throws DataIntegrityViolationException when the bucket already holds an object under the id
	 */
	void createExclusively(StoredFileEntity entity, OffsetDateTime timestamp);

	/**
	 * Stores an object, replacing whatever the bucket already holds under the same id. Written as a single upsert rather
	 * than left to the save of the repository, which decides between an insert and an update by loading the row first —
	 * and the row carries the content of the object being replaced, so the store of a large object read that content out
	 * of the database only to overwrite it. Nothing else in the service loads an entity it does not intend to read.
	 *
	 * @param entity the object to store
	 */
	void store(StoredFileEntity entity);
}
