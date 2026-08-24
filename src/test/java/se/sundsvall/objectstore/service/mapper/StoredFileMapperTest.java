package se.sundsvall.objectstore.service.mapper;

import java.sql.Blob;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.rowset.serial.SerialBlob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import se.sundsvall.objectstore.api.model.FileMetadata;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;
import se.sundsvall.objectstore.integration.db.model.StoredFileSummary;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.OffsetDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class StoredFileMapperTest {

	private static final String ID = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b";
	private static final String ETAG = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
	private static final OffsetDateTime CREATED = now().minusDays(1);
	private static final OffsetDateTime EXPIRES_AT = now().plusDays(7);

	private static Blob blob() {
		try {
			return new SerialBlob("content".getBytes(UTF_8));
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static StoredFileSummary summary(final String id) {
		return new StoredFileSummary(id, "attachments", "invoice.pdf", "application/pdf", 1024L, ETAG, CREATED, EXPIRES_AT);
	}

	@Test
	void toStoredFileEntity() {
		// Arrange
		final var content = blob();

		// Act
		final var result = StoredFileMapper.toStoredFileEntity("attachments", ID, "invoice.pdf", "application/pdf", 1024L, ETAG, content, CREATED, EXPIRES_AT);

		// Assert
		assertThat(result).hasNoNullFieldsOrProperties();
		assertThat(result.getId()).isEqualTo(ID);
		assertThat(result.getBucket()).isEqualTo("attachments");
		assertThat(result.getFileName()).isEqualTo("invoice.pdf");
		assertThat(result.getContentType()).isEqualTo("application/pdf");
		assertThat(result.getSizeInBytes()).isEqualTo(1024L);
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
