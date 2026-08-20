package se.sundsvall.objectstore.service.scheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.objectstore.integration.db.StoredFileRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CleanupWorkerTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC);

	@Mock
	private StoredFileRepository storedFileRepositoryMock;

	@Captor
	private ArgumentCaptor<OffsetDateTime> timestampCaptor;

	private CleanupWorker cleanupWorker;

	@BeforeEach
	void setUp() {
		cleanupWorker = new CleanupWorker(storedFileRepositoryMock, CLOCK);
	}

	/**
	 * The expired objects are removed by one statement rather than fetched and then deleted, so nothing but the count
	 * ever comes back from the database.
	 */
	@Test
	void removeExpiredObjects() {
		// Arrange
		when(storedFileRepositoryMock.deleteExpired(any(OffsetDateTime.class))).thenReturn(2);

		// Act
		final var result = cleanupWorker.removeExpiredObjects();

		// Assert
		assertThat(result).isEqualTo(2);
		verify(storedFileRepositoryMock).deleteExpired(timestampCaptor.capture());
		assertThat(timestampCaptor.getValue()).isEqualTo(OffsetDateTime.now(CLOCK));
		verifyNoMoreInteractions(storedFileRepositoryMock);
	}

	@Test
	void removeExpiredObjectsWhenNoneExpired() {
		// Arrange
		when(storedFileRepositoryMock.deleteExpired(any(OffsetDateTime.class))).thenReturn(0);

		// Act
		final var result = cleanupWorker.removeExpiredObjects();

		// Assert
		assertThat(result).isZero();
		verify(storedFileRepositoryMock).deleteExpired(any(OffsetDateTime.class));
		verifyNoMoreInteractions(storedFileRepositoryMock);
	}
}
