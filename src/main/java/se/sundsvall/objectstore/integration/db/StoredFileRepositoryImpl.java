package se.sundsvall.objectstore.integration.db;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;

class StoredFileRepositoryImpl implements StoredFileRepositoryCustom {

	private static final String DELETE_EXPIRED = """
		delete from StoredFileEntity entity
		where entity.bucket = :bucket
		and entity.id = :id
		and entity.expiresAt is not null
		and entity.expiresAt < :timestamp
		""";

	private final EntityManager entityManager;

	StoredFileRepositoryImpl(final EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	@Transactional
	public void createExclusively(final StoredFileEntity entity, final OffsetDateTime timestamp) {
		// An object that has expired is already invisible to every read, so it does not stand in the way of a create.
		// Removing it in the same transaction as the insert keeps two simultaneous creates serialized behind its row.
		entityManager.createQuery(DELETE_EXPIRED)
			.setParameter("bucket", entity.getBucket())
			.setParameter("id", entity.getId())
			.setParameter("timestamp", timestamp)
			.executeUpdate();

		try {
			entityManager.persist(entity);
			entityManager.flush();
		} catch (final ConstraintViolationException e) {
			// Only a collision on the key means the id is taken. Reporting any other integrity failure as one would
			// answer a store that is refused for an unrelated reason with a precondition the client never sent.
			if (e.getKind() != ConstraintKind.UNIQUE) {
				throw e;
			}
			throw new DataIntegrityViolationException("An object is already stored under the id", e);
		}
	}
}
