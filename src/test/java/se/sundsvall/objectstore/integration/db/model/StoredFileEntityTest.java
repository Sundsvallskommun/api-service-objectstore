package se.sundsvall.objectstore.integration.db.model;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEqualsExcluding;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCodeExcluding;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToStringExcluding;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static com.google.code.beanmatchers.BeanMatchers.registerValueGenerator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;

class StoredFileEntityTest {

	/**
	 * A fixed point in time rather than the wall clock, so that a failure is reproducible, and a counter rather than a
	 * random offset from it, so that two generated values are never accidentally the same — which is what the equals and
	 * hashCode matchers need in order to tell a difference from a match.
	 */
	private static final OffsetDateTime TIMESTAMP = OffsetDateTime.parse("2026-08-20T12:00:00Z");
	private static final AtomicInteger TIMESTAMP_COUNTER = new AtomicInteger();

	private static final String[] NON_KEY_PROPERTIES = {
		"fileName", "contentType", "sizeInBytes", "etag", "content", "created", "expiresAt"
	};

	@BeforeAll
	static void setup() {
		registerValueGenerator(() -> TIMESTAMP.plusDays(TIMESTAMP_COUNTER.incrementAndGet()), OffsetDateTime.class);
		registerValueGenerator(StoredFileEntityTest::createContent, byte[].class);
	}

	private static byte[] createContent() {
		return ("content-" + TIMESTAMP_COUNTER.incrementAndGet()).getBytes(UTF_8);
	}

	/**
	 * Everything but the key is excluded from equals and hashCode, which compare identity rather than state, and the
	 * content is excluded from toString, which reports its size rather than its bytes — anything else would put an
	 * entire object into every log line that mentions the entity.
	 */
	@Test
	void testBean() {
		MatcherAssert.assertThat(StoredFileEntity.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCodeExcluding(NON_KEY_PROPERTIES),
			hasValidBeanEqualsExcluding(NON_KEY_PROPERTIES),
			hasValidBeanToStringExcluding("content")));
	}

	/**
	 * A stored object stays the same object when its content is replaced, since a store to an id that already holds one
	 * replaces it in place rather than producing a second object.
	 */
	@Test
	void testIdentityIsTheKeyAlone() {
		// Arrange
		final var one = StoredFileEntity.create().withBucket("attachments").withId("0e2b7b3a-2a3e-4b7e-9c37-5f5e1a6b3e10")
			.withEtag("one").withContent("one".getBytes(UTF_8));
		final var other = StoredFileEntity.create().withBucket("attachments").withId("0e2b7b3a-2a3e-4b7e-9c37-5f5e1a6b3e10")
			.withEtag("other").withContent("other".getBytes(UTF_8));

		// Assert
		assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
		assertThat(one).isNotEqualTo(StoredFileEntity.create().withBucket("archive").withId("0e2b7b3a-2a3e-4b7e-9c37-5f5e1a6b3e10"));
	}

	@Test
	void testBuilderMethods() {
		// Arrange
		final var id = "0e2b7b3a-2a3e-4b7e-9c37-5f5e1a6b3e10";
		final var bucket = "attachments";
		final var fileName = "invoice-123.pdf";
		final var contentType = "application/pdf";
		final var sizeInBytes = 20971L;
		final var etag = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
		final var content = createContent();
		final var created = TIMESTAMP;
		final var expiresAt = TIMESTAMP.plusDays(7);

		// Act
		final var result = StoredFileEntity.create()
			.withId(id)
			.withBucket(bucket)
			.withFileName(fileName)
			.withContentType(contentType)
			.withSizeInBytes(sizeInBytes)
			.withEtag(etag)
			.withContent(content)
			.withCreated(created)
			.withExpiresAt(expiresAt);

		// Assert
		assertThat(result).hasNoNullFieldsOrProperties();
		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getBucket()).isEqualTo(bucket);
		assertThat(result.getFileName()).isEqualTo(fileName);
		assertThat(result.getContentType()).isEqualTo(contentType);
		assertThat(result.getSizeInBytes()).isEqualTo(sizeInBytes);
		assertThat(result.getEtag()).isEqualTo(etag);
		assertThat(result.getContent()).isEqualTo(content);
		assertThat(result.getCreated()).isEqualTo(created);
		assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
	}

	/**
	 * The size of the content stands in for the content itself, so that logging an entity never dumps an object into the
	 * log.
	 */
	@Test
	void testToStringReportsTheSizeOfTheContent() {
		assertThat(StoredFileEntity.create().withContent("12345".getBytes(UTF_8)))
			.hasToString("StoredFileEntity{id='null', bucket='null', fileName='null', contentType='null', sizeInBytes=null, etag='null', content=5 bytes, created=null, expiresAt=null}");
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(StoredFileEntity.create()).hasAllNullFieldsOrProperties();
		assertThat(new StoredFileEntity()).hasAllNullFieldsOrProperties();
	}

}
