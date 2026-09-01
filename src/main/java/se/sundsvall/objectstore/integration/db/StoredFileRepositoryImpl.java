package se.sundsvall.objectstore.integration.db;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.hibernate.query.NativeQuery;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;

import static java.util.Optional.ofNullable;

class StoredFileRepositoryImpl implements StoredFileRepositoryCustom {

	private static final String DELETE_EXPIRED = """
		delete from StoredFileEntity entity
		where entity.bucket = :bucket
		and entity.id = :id
		and entity.expiresAt is not null
		and entity.expiresAt <= :timestamp
		""";

	/**
	 * Stores an object and replaces the one already stored under its id in a single statement, leaving the choice between
	 * the two to the primary key. Written as native SQL since the upsert is a feature of the database rather than of JPQL,
	 * and named columns rather than the values() function, which MySQL has deprecated.
	 */
	private static final String UPSERT = """
		insert into stored_file (bucket, id, file_name, content_type, size_in_bytes, etag, content, created, expires_at)
		values (:bucket, :id, :fileName, :contentType, :sizeInBytes, :etag, :content, :created, :expiresAt)
		on duplicate key update
		file_name = :fileName,
		content_type = :contentType,
		size_in_bytes = :sizeInBytes,
		etag = :etag,
		content = :content,
		created = :created,
		expires_at = :expiresAt
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

	@Override
	@Transactional
	public void store(final StoredFileEntity entity) {
		final NativeQuery<?> query = entityManager.createNativeQuery(UPSERT).unwrap(NativeQuery.class);

		query.setParameter("bucket", entity.getBucket(), String.class);
		query.setParameter("id", entity.getId(), String.class);
		query.setParameter("fileName", entity.getFileName(), String.class);
		query.setParameter("contentType", entity.getContentType(), String.class);
		query.setParameter("sizeInBytes", entity.getSizeInBytes(), Long.class);
		query.setParameter("etag", entity.getEtag(), String.class);
		query.setParameter("content", entity.getContent(), byte[].class);
		query.setParameter("created", toColumnValue(entity.getCreated()), LocalDateTime.class);
		query.setParameter("expiresAt", toColumnValue(entity.getExpiresAt()), LocalDateTime.class);

		query.executeUpdate();
	}

	/**
	 * Converts a timestamp to what the column holding it stores. The columns are declared without a time zone and the
	 * entity normalizes into them, so a statement that writes them itself has to normalize the same way — the same
	 * instant, read in the zone the driver reads the column back in, which is the default zone of the JVM.
	 *
	 * @param  timestamp the timestamp to convert, or null
	 * @return           the value to bind, or null when there is no timestamp
	 */
	private static LocalDateTime toColumnValue(final OffsetDateTime timestamp) {
		return ofNullable(timestamp)
			.map(value -> value.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime())
			.orElse(null);
	}
}
