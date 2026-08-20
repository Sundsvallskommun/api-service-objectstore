package se.sundsvall.objectstore.integration.db;

import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;

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
}
