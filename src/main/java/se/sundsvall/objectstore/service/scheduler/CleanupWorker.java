package se.sundsvall.objectstore.service.scheduler;

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

	public CleanupWorker(final StoredFileRepository storedFileRepository) {
		this.storedFileRepository = storedFileRepository;
	}

	/**
	 * Removes all objects whose expiry has passed, in a single statement — selecting them first would load the content of
	 * every expired object into memory only to throw it away.
	 *
	 * @return the number of removed objects
	 */
	@Transactional
	public int removeExpiredObjects() {
		final var removed = storedFileRepository.deleteExpired(now());

		if (removed == 0) {
			LOG.debug("No expired objects to remove");
		}

		return removed;
	}
}
