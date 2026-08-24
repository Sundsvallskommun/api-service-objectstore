package se.sundsvall.objectstore.service.scheduler;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.objectstore.integration.db.StoredFileRepository;

import static java.time.OffsetDateTime.now;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CleanupWorkerTest {

	@Mock
	private StoredFileRepository storedFileRepositoryMock;

	@Captor
	private ArgumentCaptor<OffsetDateTime> timestampCaptor;

	@InjectMocks
	private CleanupWorker cleanupWorker;

	@Test
	void removeExpiredObjects() {
		// Arrange
		final var ids = List.of("id-1", "id-2");
		when(storedFileRepositoryMock.findExpiredIds(any(OffsetDateTime.class))).thenReturn(ids);

		// Act
		final var result = cleanupWorker.removeExpiredObjects();

		// Assert
		assertThat(result).isEqualTo(2);
		verify(storedFileRepositoryMock).findExpiredIds(timestampCaptor.capture());
		assertThat(timestampCaptor.getValue()).isCloseTo(now(), within(5, SECONDS));
		verify(storedFileRepositoryMock).deleteAllById(ids);
	}

	@Test
	void removeExpiredObjectsWhenNoneExpired() {
		// Arrange
		when(storedFileRepositoryMock.findExpiredIds(any(OffsetDateTime.class))).thenReturn(List.of());

		// Act
		final var result = cleanupWorker.removeExpiredObjects();

		// Assert
		assertThat(result).isZero();
		verify(storedFileRepositoryMock).findExpiredIds(any(OffsetDateTime.class));
		verifyNoMoreInteractions(storedFileRepositoryMock);
	}
}
