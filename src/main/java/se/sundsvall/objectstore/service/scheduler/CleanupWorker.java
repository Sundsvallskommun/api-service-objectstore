package se.sundsvall.objectstore.service.scheduler;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.sundsvall.objectstore.integration.db.StoredFileRepository;

import static java.time.OffsetDateTime.now;

@Component
public class CleanupWorker {

	private static final Logger LOG = LoggerFactory.getLogger(CleanupWorker.class);

	private final StoredFileRepository storedFileRepository;
	private final Clock clock;

	public CleanupWorker(final StoredFileRepository storedFileRepository, final Clock clock) {
		this.storedFileRepository = storedFileRepository;
		this.clock = clock;
	}

	/**
	 * Removes all objects whose expiry has passed, in a single statement — selecting them first would load the content of
	 * every expired object into memory only to throw it away.
	 *
	 * @return the number of removed objects
	 */
	@Transactional
	public int removeExpiredObjects() {
		final var removed = storedFileRepository.deleteExpired(now(clock));

		if (removed == 0) {
			LOG.debug("No expired objects to remove");
		}

		return removed;
	}
}
