package se.sundsvall.objectstore.db;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import se.sundsvall.objectstore.Application;
import se.sundsvall.objectstore.integration.db.StoredFileRepository;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The store is a statement the service writes itself rather than the save of the repository, which would load the row
 * it is about to overwrite — and with it the content of the object being replaced. What that statement decides is left
 * to the database, so it is asserted against a real one.
 */
@ActiveProfiles("it")
@SpringBootTest(classes = Application.class, properties = "spring.main.banner-mode=off")
@Sql(scripts = {
	"/db/scripts/truncate.sql",
	"/db/scripts/testdata-it.sql"
})
class StoredFileRepositoryIT {

	private static final String BUCKET = "attachments";
	private static final String EXISTING_ID = "11111111-1111-1111-1111-111111111111";
	private static final String FREE_ID = "44444444-4444-4444-4444-444444444444";
	private static final String SAVED_ID = "55555555-5555-5555-5555-555555555555";
	private static final byte[] CONTENT = "stored-content".getBytes(UTF_8);
	private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-08-20T12:00:00Z");

	@Autowired
	private StoredFileRepository storedFileRepository;

	private static StoredFileEntity entity(final String id) {
		return StoredFileEntity.create()
			.withBucket(BUCKET)
			.withId(id)
			.withFileName("stored.txt")
			.withContentType("text/plain")
			.withSizeInBytes((long) CONTENT.length)
			.withEtag("etag")
			.withContent(CONTENT)
			.withCreated(CREATED)
			.withExpiresAt(CREATED.plusDays(7));
	}

	@Test
	void storeWhenTheIdIsFree() {
		// Act
		storedFileRepository.store(entity(FREE_ID));

		// Assert
		assertThat(storedFileRepository.findByBucketAndId(BUCKET, FREE_ID))
			.hasValueSatisfying(stored -> {
				assertThat(stored.getFileName()).isEqualTo("stored.txt");
				assertThat(stored.getContent()).isEqualTo(CONTENT);
			});
	}

	/**
	 * The statement replaces every column of the object it overwrites rather than only the ones that were given a value,
	 * so nothing of the object being replaced is left behind — an expiry least of all, which would outlive the object it
	 * belonged to.
	 */
	@Test
	void storeWhenTheIdIsTaken() {
		// Act
		storedFileRepository.store(entity(EXISTING_ID).withExpiresAt(null));

		// Assert
		assertThat(storedFileRepository.findByBucketAndId(BUCKET, EXISTING_ID))
			.hasValueSatisfying(stored -> {
				assertThat(stored.getFileName()).isEqualTo("stored.txt");
				assertThat(stored.getContent()).isEqualTo(CONTENT);
				assertThat(stored.getExpiresAt()).isNull();
			});
	}

	/**
	 * The statement writes the timestamp columns itself rather than through the mapping of the entity, so it has to write
	 * what the mapping would have written. An object stored either way therefore has to read back as the same point in
	 * time — a comparison against the mapping rather than against a value computed the same way the statement computes
	 * it, which would agree with itself however wrong it was.
	 */
	@Test
	void storeWritesTheTimestampsTheMappingWay() {
		// Arrange
		storedFileRepository.save(entity(SAVED_ID));

		// Act
		storedFileRepository.store(entity(FREE_ID));

		// Assert
		final var saved = storedFileRepository.findByBucketAndId(BUCKET, SAVED_ID).orElseThrow();

		assertThat(storedFileRepository.findByBucketAndId(BUCKET, FREE_ID))
			.hasValueSatisfying(stored -> {
				assertThat(stored.getCreated()).isEqualTo(saved.getCreated());
				assertThat(stored.getExpiresAt()).isEqualTo(saved.getExpiresAt());
			});
	}
}
