package se.sundsvall.objectstore.api.model;

import java.util.List;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanConstructor;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanEquals;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanHashCode;
import static com.google.code.beanmatchers.BeanMatchers.hasValidBeanToString;
import static com.google.code.beanmatchers.BeanMatchers.hasValidGettersAndSetters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;

class ObjectListingTest {

	@Test
	void testBean() {
		MatcherAssert.assertThat(ObjectListing.class, allOf(
			hasValidBeanConstructor(),
			hasValidGettersAndSetters(),
			hasValidBeanHashCode(),
			hasValidBeanEquals(),
			hasValidBeanToString()));
	}

	@Test
	void testBuilderMethods() {
		// Arrange
		final var objects = List.of(FileMetadata.create().withId("d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b"));
		final var nextContinuationToken = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b";

		// Act
		final var result = ObjectListing.create()
			.withObjects(objects)
			.withTruncated(true)
			.withNextContinuationToken(nextContinuationToken);

		// Assert
		assertThat(result).hasNoNullFieldsOrProperties();
		assertThat(result.getObjects()).isEqualTo(objects);
		assertThat(result.isTruncated()).isTrue();
		assertThat(result.getNextContinuationToken()).isEqualTo(nextContinuationToken);
	}

	@Test
	void testNoDirtOnCreatedBean() {
		assertThat(ObjectListing.create()).hasAllNullFieldsOrPropertiesExcept("truncated");
		assertThat(new ObjectListing()).hasAllNullFieldsOrPropertiesExcept("truncated");
		assertThat(ObjectListing.create().isTruncated()).isFalse();
	}
}
