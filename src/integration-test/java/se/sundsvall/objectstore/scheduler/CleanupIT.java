package se.sundsvall.objectstore.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import se.sundsvall.objectstore.Application;
import se.sundsvall.objectstore.integration.db.StoredFileRepository;
import se.sundsvall.objectstore.service.scheduler.CleanupWorker;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cleanup is a single statement rather than a load followed by a remove, so there is nothing to assert on short of
 * running it against a database. It is the one thing the scheduler does, and the cron that would otherwise run it is
 * disabled in this profile.
 */
@ActiveProfiles("it")
@SpringBootTest(classes = Application.class, properties = "spring.main.banner-mode=off")
@Sql(scripts = {
	"/db/scripts/truncate.sql",
	"/db/scripts/testdata-it.sql"
})
class CleanupIT {

	private static final String BUCKET = "attachments";
	private static final String UNEXPIRED_ID = "11111111-1111-1111-1111-111111111111";
	private static final String EXPIRED_ID = "22222222-2222-2222-2222-222222222222";
	private static final String NEVER_EXPIRING_ID = "33333333-3333-3333-3333-333333333333";

	@Autowired
	private CleanupWorker cleanupWorker;

	@Autowired
	private StoredFileRepository storedFileRepository;

	/**
	 * An object whose expiry has passed is removed; one whose expiry is still ahead and one that carries no expiry at all
	 * are both left alone. The last of those is what the null check in the statement is for — without it every object
	 * that never expires would be removed on the first run.
	 */
	@Test
	void removeExpiredObjects() {
		// Act
		final var removed = cleanupWorker.removeExpiredObjects();

		// Assert
		assertThat(removed).isOne();
		assertThat(storedFileRepository.findByBucketAndId(BUCKET, EXPIRED_ID)).isEmpty();
		assertThat(storedFileRepository.findByBucketAndId(BUCKET, UNEXPIRED_ID)).isPresent();
		assertThat(storedFileRepository.findByBucketAndId(BUCKET, NEVER_EXPIRING_ID)).isPresent();
	}
}
