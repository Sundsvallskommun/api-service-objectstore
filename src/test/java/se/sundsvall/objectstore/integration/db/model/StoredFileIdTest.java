package se.sundsvall.objectstore.integration.db.model;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;

class StoredFileIdTest {

	@Test
	void testBean() {
		MatcherAssert.assertThat(StoredFileId.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@Test
	void testBuilderMethods() {
		// Arrange
		final var bucket = "attachments";
		final var id = "0e2b7b3a-2a3e-4b7e-9c37-5f5e1a6b3e10";

		// Act
		final var result = StoredFileId.create().withBucket(bucket).withId(id);

		// Assert
		assertThat(result).hasNoNullFieldsOrProperties();
		assertThat(result.getBucket()).isEqualTo(bucket);
		assertThat(result.getId()).isEqualTo(id);
	}

	/**
	 * The same id in two buckets identifies two different objects, which is the whole reason the bucket is part of the
	 * key.
	 */
	@Test
	void testTheSameIdInTwoBucketsIsNotTheSameIdentity() {
		// Arrange
		final var id = "0e2b7b3a-2a3e-4b7e-9c37-5f5e1a6b3e10";

		// Act
		final var one = StoredFileId.create().withBucket("attachments").withId(id);
		final var other = StoredFileId.create().withBucket("archive").withId(id);

		// Assert
		assertThat(one).isNotEqualTo(other);
		assertThat(one).isEqualTo(StoredFileId.create().withBucket("attachments").withId(id));
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(StoredFileId.create()).hasAllNullFieldsOrProperties();
		assertThat(new StoredFileId()).hasAllNullFieldsOrProperties();
	}

}
