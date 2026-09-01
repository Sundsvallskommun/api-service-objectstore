package se.sundsvall.objectstore.service.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CleanupSchedulerTest {

	@Mock
	private CleanupWorker cleanupWorkerMock;

	@InjectMocks
	private CleanupScheduler cleanupScheduler;

	@Test
	void execute() {
		// Arrange
		when(cleanupWorkerMock.removeExpiredObjects()).thenReturn(3);

		// Act
		cleanupScheduler.execute();

		// Assert
		verify(cleanupWorkerMock).removeExpiredObjects();
		verifyNoMoreInteractions(cleanupWorkerMock);
	}
}
