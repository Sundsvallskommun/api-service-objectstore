package se.sundsvall.objectstore.service.mapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import se.sundsvall.objectstore.api.model.FileMetadata;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;
import se.sundsvall.objectstore.integration.db.model.StoredFileSummary;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class StoredFileMapperTest {

	private static final String ID = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b";
	private static final String ETAG = "ed7002b439e9ac845f22357d822bac1444730fbdb6016d3ec9432297b9ec9f73";
	private static final OffsetDateTime TIMESTAMP = OffsetDateTime.parse("2026-08-20T12:00:00Z");
	private static final OffsetDateTime CREATED = TIMESTAMP.minusDays(1);
	private static final OffsetDateTime EXPIRES_AT = TIMESTAMP.plusDays(7);

	private static byte[] content() {
		return "content".getBytes(UTF_8);
	}

	private static StoredFileSummary summary(final String id) {
		return new StoredFileSummary(id, "attachments", "invoice.pdf", "application/pdf", 1024L, ETAG, CREATED, EXPIRES_AT);
	}

	@Test
	void toStoredFileEntity() {
		// Arrange
		final var content = content();

		// Act
		final var result = StoredFileMapper.toStoredFileEntity("attachments", ID, "invoice.pdf", "application/pdf", content, CREATED, EXPIRES_AT);

		// Assert
		assertThat(result).hasNoNullFieldsOrProperties();
		assertThat(result.getId()).isEqualTo(ID);
		assertThat(result.getBucket()).isEqualTo("attachments");
		assertThat(result.getFileName()).isEqualTo("invoice.pdf");
		assertThat(result.getContentType()).isEqualTo("application/pdf");
		assertThat(result.getSizeInBytes()).isEqualTo(content.length);
		assertThat(result.getEtag()).isEqualTo(ETAG);
		assertThat(result.getContent()).isEqualTo(content);
		assertThat(result.getCreated()).isEqualTo(CREATED);
		assertThat(result.getExpiresAt()).isEqualTo(EXPIRES_AT);
	}

	@ParameterizedTest
	@MethodSource("toFileMetadataArguments")
	void toFileMetadata(final StoredFileEntity input, final FileMetadata expected) {
		// Act
		final var result = StoredFileMapper.toFileMetadata(input);

		// Assert
		if (expected == null) {
			assertThat(result).isNull();
		} else {
			assertThat(result).usingRecursiveComparison().isEqualTo(expected);
		}
	}

	private static Stream<Arguments> toFileMetadataArguments() {
		return Stream.of(
			Arguments.of(null, null),
			Arguments.of(
				StoredFileEntity.create()
					.withId(ID)
					.withBucket("attachments")
					.withFileName("invoice.pdf")
					.withContentType("application/pdf")
					.withSizeInBytes(1024L)
					.withEtag(ETAG)
					.withCreated(CREATED)
					.withExpiresAt(EXPIRES_AT),
				FileMetadata.create()
					.withId(ID)
					.withBucket("attachments")
					.withFileName("invoice.pdf")
					.withContentType("application/pdf")
					.withSize(1024L)
					.withEtag(ETAG)
					.withCreated(CREATED)
					.withExpiresAt(EXPIRES_AT)),
			Arguments.of(
				StoredFileEntity.create().withId(ID),
				FileMetadata.create().withId(ID)));
	}

	@Test
	void toFileMetadataFromSummary() {
		// Act
		final var result = StoredFileMapper.toFileMetadata(summary(ID));

		// Assert
		assertThat(result).usingRecursiveComparison().isEqualTo(FileMetadata.create()
			.withId(ID)
			.withBucket("attachments")
			.withFileName("invoice.pdf")
			.withContentType("application/pdf")
			.withSize(1024L)
			.withEtag(ETAG)
			.withCreated(CREATED)
			.withExpiresAt(EXPIRES_AT));
	}

	@Test
	void toFileMetadataFromNullSummary() {
		assertThat(StoredFileMapper.toFileMetadata((StoredFileSummary) null)).isNull();
	}

	@Test
	void toObjectListingWhenTruncated() {
		// Act
		final var result = StoredFileMapper.toObjectListing(List.of(summary("a"), summary("b")), true);

		// Assert
		assertThat(result.getObjects())
			.extracting(FileMetadata::getId, FileMetadata::getSize)
			.containsExactly(tuple("a", 1024L), tuple("b", 1024L));
		assertThat(result.isTruncated()).isTrue();
		assertThat(result.getNextContinuationToken()).isEqualTo("b");
	}

	@Test
	void toObjectListingWhenNotTruncated() {
		// Act
		final var result = StoredFileMapper.toObjectListing(List.of(summary("a")), false);

		// Assert
		assertThat(result.getObjects()).hasSize(1);
		assertThat(result.isTruncated()).isFalse();
		assertThat(result.getNextContinuationToken()).isNull();
	}

	@Test
	void toObjectListingWithNull() {
		// Act
		final var result = StoredFileMapper.toObjectListing(null, false);

		// Assert
		assertThat(result.getObjects()).isEmpty();
		assertThat(result.isTruncated()).isFalse();
		assertThat(result.getNextContinuationToken()).isNull();
	}
}
