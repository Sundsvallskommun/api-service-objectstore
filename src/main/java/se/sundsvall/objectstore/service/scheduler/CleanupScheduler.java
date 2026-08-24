package se.sundsvall.objectstore.service.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

@Component
public class CleanupScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(CleanupScheduler.class);

	private final CleanupWorker cleanupWorker;

	public CleanupScheduler(final CleanupWorker cleanupWorker) {
		this.cleanupWorker = cleanupWorker;
	}

	@Dept44Scheduled(
		name = "${scheduler.cleanup.name}",
		cron = "${scheduler.cleanup.cron}",
		lockAtMostFor = "${scheduler.cleanup.lock-at-most-for}",
		maximumExecutionTime = "${scheduler.cleanup.maximum-execution-time}")
	public void execute() {
		final var removed = cleanupWorker.removeExpiredObjects();

		LOG.info("Removed {} expired object(s)", removed);
	}
}
