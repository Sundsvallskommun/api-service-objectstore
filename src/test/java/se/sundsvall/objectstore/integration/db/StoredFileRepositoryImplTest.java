package se.sundsvall.objectstore.integration.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;

import static java.time.OffsetDateTime.now;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoredFileRepositoryImplTest {

	private static final String BUCKET = "attachments";
	private static final String ID = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b";

	@Mock
	private EntityManager entityManagerMock;

	@Mock
	private Query queryMock;

	@InjectMocks
	private StoredFileRepositoryImpl storedFileRepository;

	private static StoredFileEntity entity() {
		return StoredFileEntity.create().withBucket(BUCKET).withId(ID);
	}

	/**
	 * An expired object is already invisible to every read, so it is removed rather than allowed to refuse the create.
	 */
	@Test
	void createExclusively() {
		// Arrange
		final var entity = entity();
		final var timestamp = now();

		when(entityManagerMock.createQuery(anyString())).thenReturn(queryMock);
		when(queryMock.setParameter(anyString(), any())).thenReturn(queryMock);

		// Act
		storedFileRepository.createExclusively(entity, timestamp);

		// Assert
		verify(queryMock).setParameter("bucket", BUCKET);
		verify(queryMock).setParameter("id", ID);
		verify(queryMock).setParameter("timestamp", timestamp);
		verify(queryMock).executeUpdate();
		verify(entityManagerMock).persist(entity);
		verify(entityManagerMock).flush();
	}

	/**
	 * The insert is flushed rather than left to the commit, so that the refusal arrives while there is still something to
	 * translate it into.
	 */
	@Test
	void createExclusivelyWhenTheIdIsAlreadyTaken() {
		// Arrange
		final var entity = entity();

		when(entityManagerMock.createQuery(anyString())).thenReturn(queryMock);
		when(queryMock.setParameter(anyString(), any())).thenReturn(queryMock);
		doThrow(mock(ConstraintViolationException.class)).when(entityManagerMock).flush();

		// Act & Assert
		assertThatThrownBy(() -> storedFileRepository.createExclusively(entity, now()))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("An object is already stored under the id");
	}
}
