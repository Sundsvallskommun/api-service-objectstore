package se.sundsvall.objectstore.api.model;

import java.time.OffsetDateTime;
import java.util.Random;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static com.google.code.beanmatchers.BeanMatchers.registerValueGenerator;
import static java.time.OffsetDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;

class FileMetadataTest {

	@BeforeAll
	static void setup() {
		registerValueGenerator(() -> now().plusDays(new Random().nextInt(100)), OffsetDateTime.class);
	}

	@Test
	void testBean() {
		MatcherAssert.assertThat(FileMetadata.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@Test
	void testBuilderMethods() {
		// Arrange
		final var id = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b";
		final var bucket = "attachments";
		final var fileName = "invoice-123.pdf";
		final var contentType = "application/pdf";
		final var size = 20971L;
		final var etag = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
		final var created = now();
		final var expiresAt = now().plusDays(7);

		// Act
		final var result = FileMetadata.create()
			.withId(id)
			.withBucket(bucket)
			.withFileName(fileName)
			.withContentType(contentType)
			.withSize(size)
			.withEtag(etag)
			.withCreated(created)
			.withExpiresAt(expiresAt);

		// Assert
		assertThat(result).hasNoNullFieldsOrProperties();
		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getBucket()).isEqualTo(bucket);
		assertThat(result.getFileName()).isEqualTo(fileName);
		assertThat(result.getContentType()).isEqualTo(contentType);
		assertThat(result.getSize()).isEqualTo(size);
		assertThat(result.getEtag()).isEqualTo(etag);
		assertThat(result.getCreated()).isEqualTo(created);
		assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(FileMetadata.create()).hasAllNullFieldsOrProperties();
		assertThat(new FileMetadata()).hasAllNullFieldsOrProperties();
	}
}
