package se.sundsvall.objectstore.integration.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.OffsetDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.exception.ConstraintViolationException.ConstraintKind.UNIQUE;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
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
	private static final OffsetDateTime TIMESTAMP = OffsetDateTime.parse("2026-08-20T12:00:00Z");

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
		final var timestamp = TIMESTAMP;

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
		doThrow(violationOfKind(UNIQUE)).when(entityManagerMock).flush();

		// Act & Assert
		assertThatThrownBy(() -> storedFileRepository.createExclusively(entity, TIMESTAMP))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("An object is already stored under the id");
	}

	/**
	 * Only a collision on the key means the id is taken. Any other integrity failure is left alone rather than reported
	 * as one, which would answer a store refused for an unrelated reason with a precondition the client never sent.
	 */
	@ParameterizedTest
	@EnumSource(value = ConstraintKind.class, mode = EXCLUDE, names = "UNIQUE")
	void createExclusivelyWhenTheViolationIsNotACollision(final ConstraintKind kind) {
		// Arrange
		final var entity = entity();
		final var violation = violationOfKind(kind);

		when(entityManagerMock.createQuery(anyString())).thenReturn(queryMock);
		when(queryMock.setParameter(anyString(), any())).thenReturn(queryMock);
		doThrow(violation).when(entityManagerMock).flush();

		// Act & Assert
		assertThatThrownBy(() -> storedFileRepository.createExclusively(entity, TIMESTAMP))
			.isSameAs(violation);
	}

	private static ConstraintViolationException violationOfKind(final ConstraintKind kind) {
		final var violation = mock(ConstraintViolationException.class);
		when(violation.getKind()).thenReturn(kind);
		return violation;
	}
}
