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
	 * Removes all objects whose expiry has passed.
	 *
	 * @return the number of removed objects
	 */
	@Transactional
	public int removeExpiredObjects() {
		final var expiredIds = storedFileRepository.findExpiredIds(now());

		if (expiredIds.isEmpty()) {
			LOG.debug("No expired objects to remove");
			return 0;
		}

		storedFileRepository.deleteAllById(expiredIds);

		return expiredIds.size();
	}
}
