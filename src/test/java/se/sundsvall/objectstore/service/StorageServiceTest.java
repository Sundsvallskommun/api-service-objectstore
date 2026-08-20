package se.sundsvall.objectstore.service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.objectstore.configuration.StorageProperties;
import se.sundsvall.objectstore.integration.db.StoredFileRepository;
import se.sundsvall.objectstore.integration.db.model.StoredFileEntity;
import se.sundsvall.objectstore.integration.db.model.StoredFileSummary;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.OffsetDateTime.now;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.ETAG;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

	private static final String BUCKET = "attachments";
	private static final String ID = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b";
	private static final String FILE_NAME = "invoice-123.pdf";
	private static final String DISPOSITION = "attachment; filename=\"invoice-123.pdf\"";
	private static final byte[] CONTENT = "file-content".getBytes(UTF_8);

	/**
	 * The SHA-256 digest of {@link #CONTENT}, verifiable with {@code printf 'file-content' | shasum -a 256}.
	 */
	private static final String ETAG_VALUE = "2239ce4df9ee8db012834642ec801b55ba2c92b28bdd11f4d73d9c55d39f3b0a";

	@Mock
	private StoredFileRepository storedFileRepositoryMock;

	@Captor
	private ArgumentCaptor<StoredFileEntity> entityCaptor;

	private StorageService serviceWith(final Duration timeToLive) {
		return serviceWith(timeToLive, DataSize.ofMegabytes(15));
	}

	private StorageService serviceWith(final Duration timeToLive, final DataSize maxObjectSize) {
		return new StorageService(storedFileRepositoryMock, new StorageProperties(timeToLive, maxObjectSize));
	}

	private static MockHttpServletRequest requestWith(final byte[] content) {
		final var request = new MockHttpServletRequest();
		request.setContent(content);
		return request;
	}

	private static StoredFileSummary summary(final String id) {
		return summary(id, FILE_NAME, "application/pdf", null);
	}

	private static StoredFileSummary summary(final String id, final String fileName, final String contentType, final OffsetDateTime expiresAt) {
		return new StoredFileSummary(id, BUCKET, fileName, contentType, (long) CONTENT.length, ETAG_VALUE, now(), expiresAt);
	}

	@Test
	void store() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		when(storedFileRepositoryMock.save(any(StoredFileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		final var result = service.store(BUCKET, ID, "application/pdf", DISPOSITION, null, null, requestWith(CONTENT));

		// Assert
		verify(storedFileRepositoryMock).save(entityCaptor.capture());
		final var captured = entityCaptor.getValue();
		assertThat(captured.getId()).isEqualTo(ID);
		assertThat(captured.getBucket()).isEqualTo(BUCKET);
		assertThat(captured.getCreated()).isCloseTo(now(), within(5, SECONDS));
		assertThat(captured.getFileName()).isEqualTo(FILE_NAME);
		assertThat(captured.getContentType()).isEqualTo("application/pdf");
		assertThat(captured.getSizeInBytes()).isEqualTo(CONTENT.length);
		assertThat(captured.getEtag()).isEqualTo(ETAG_VALUE);
		assertThat(captured.getContent()).isEqualTo(CONTENT);
		assertThat(captured.getExpiresAt()).isCloseTo(now().plusDays(7), within(5, SECONDS));

		assertThat(result.getId()).isEqualTo(ID);
		assertThat(result.getEtag()).isEqualTo(ETAG_VALUE);
		assertThat(result.getSize()).isEqualTo(CONTENT.length);
	}

	@Test
	void storeWithExplicitExpiry() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));
		final var expiresAt = now().plusHours(2);

		when(storedFileRepositoryMock.save(any(StoredFileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		final var result = service.store(BUCKET, ID, "application/pdf", DISPOSITION, null, expiresAt, requestWith(CONTENT));

		// Assert
		assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
	}

	@Test
	void storeWithoutConfiguredTimeToLive() {
		// Arrange
		final var service = serviceWith(null);

		when(storedFileRepositoryMock.save(any(StoredFileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		final var result = service.store(BUCKET, ID, "application/pdf", DISPOSITION, null, null, requestWith(CONTENT));

		// Assert
		assertThat(result.getExpiresAt()).isNull();
	}

	/**
	 * Storing to an id that already holds an object replaces it rather than adding a second one, which is what makes a
	 * retried store safe. The repository is handed an entity carrying the same id, so the save merges onto the existing
	 * row.
	 */
	@Test
	void storeOverwritesAnExistingObject() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));
		final var replacement = "replacement".getBytes(UTF_8);

		when(storedFileRepositoryMock.save(any(StoredFileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		final var result = service.store(BUCKET, ID, "text/plain", "attachment; filename=\"replacement.txt\"", null, null, requestWith(replacement));

		// Assert
		verify(storedFileRepositoryMock).save(entityCaptor.capture());
		assertThat(entityCaptor.getValue().getId()).isEqualTo(ID);
		assertThat(result.getId()).isEqualTo(ID);
		assertThat(result.getFileName()).isEqualTo("replacement.txt");
		assertThat(result.getSize()).isEqualTo(replacement.length);
		assertThat(result.getEtag()).isNotEqualTo(ETAG_VALUE);
		verifyNoMoreInteractions(storedFileRepositoryMock);
	}

	/**
	 * A wildcard If-None-Match turns a store into a create-only one. The check runs before the body is read, so a refused
	 * store never pulls the content across the wire.
	 */
	@Test
	void storeWithCreateOnlyPreconditionWhenIdIsTaken() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		when(storedFileRepositoryMock.existsUnexpired(eq(BUCKET), eq(ID), any())).thenReturn(true);

		// Act & Assert
		assertThatThrownBy(() -> service.store(BUCKET, ID, null, DISPOSITION, "*", null, requestWith(CONTENT)))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("An object with id [%s] already exists in bucket [%s]".formatted(ID, BUCKET))
			.extracting("status").isEqualTo(PRECONDITION_FAILED);

		verify(storedFileRepositoryMock).existsUnexpired(eq(BUCKET), eq(ID), any());
		verifyNoMoreInteractions(storedFileRepositoryMock);
	}

	/**
	 * A create-only store goes in through the primary key rather than through a plain save, so that the object is only
	 * stored if nothing else got there first.
	 */
	@Test
	void storeWithCreateOnlyPreconditionWhenIdIsFree() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		when(storedFileRepositoryMock.existsUnexpired(eq(BUCKET), eq(ID), any())).thenReturn(false);

		// Act
		final var result = service.store(BUCKET, ID, null, DISPOSITION, "*", null, requestWith(CONTENT));

		// Assert
		assertThat(result.getId()).isEqualTo(ID);
		assertThat(result.getEtag()).isEqualTo(ETAG_VALUE);
		verify(storedFileRepositoryMock).createExclusively(entityCaptor.capture(), any());
		assertThat(entityCaptor.getValue().getContent()).isEqualTo(CONTENT);
		verify(storedFileRepositoryMock, never()).save(any(StoredFileEntity.class));
	}

	/**
	 * The check that the id is free cannot keep two simultaneous create-only stores from both passing it, so the refusal
	 * the database hands back has to be answered with the same status the check would have produced.
	 */
	@Test
	void storeWithCreateOnlyPreconditionWhenTheIdIsTakenConcurrently() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		when(storedFileRepositoryMock.existsUnexpired(eq(BUCKET), eq(ID), any())).thenReturn(false);
		doThrow(new DataIntegrityViolationException("duplicate key"))
			.when(storedFileRepositoryMock).createExclusively(any(StoredFileEntity.class), any());

		// Act & Assert
		assertThatThrownBy(() -> service.store(BUCKET, ID, null, DISPOSITION, "*", null, requestWith(CONTENT)))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("An object with id [%s] already exists in bucket [%s]".formatted(ID, BUCKET))
			.extracting("status").isEqualTo(PRECONDITION_FAILED);

		verify(storedFileRepositoryMock, never()).save(any(StoredFileEntity.class));
	}

	/**
	 * A specific entity tag is refused rather than ignored — a client that sends one is asking for a guarantee this
	 * service does not offer, and overwriting anyway is the one answer it must not get.
	 */
	@Test
	void storeWithUnsupportedPrecondition() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		// Act & Assert
		assertThatThrownBy(() -> service.store(BUCKET, ID, null, DISPOSITION, "\"%s\"".formatted(ETAG_VALUE), null, requestWith(CONTENT)))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("Only [*] is supported in the If-None-Match header of a store")
			.extracting("status").isEqualTo(BAD_REQUEST);

		verifyNoInteractions(storedFileRepositoryMock);
	}

	/**
	 * The name of the uploaded file is client controlled — it is stripped of the directory some clients send along with it,
	 * of control characters and of the characters that would let it break out of the Content-Disposition header, and is
	 * dropped entirely when nothing usable remains or the header cannot be parsed at all.
	 */
	@ParameterizedTest
	@MethodSource("sanitizedFileNameArguments")
	void storeSanitizesFileName(final String contentDisposition, final String expectedFileName) {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		when(storedFileRepositoryMock.save(any(StoredFileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		final var result = service.store(BUCKET, ID, null, contentDisposition, null, null, requestWith(CONTENT));

		// Assert
		assertThat(result.getFileName()).isEqualTo(expectedFileName);
	}

	private static Stream<Arguments> sanitizedFileNameArguments() {
		return Stream.of(
			Arguments.of("attachment; filename=\"invoice-123.pdf\"", "invoice-123.pdf"),
			Arguments.of("inline; filename=\"invoice-123.pdf\"", "invoice-123.pdf"),
			Arguments.of("attachment; filename=\"C:\\\\Users\\\\martin\\\\invoice-123.pdf\"", "invoice-123.pdf"),
			Arguments.of("attachment; filename=\"/var/tmp/invoice-123.pdf\"", "invoice-123.pdf"),
			Arguments.of("attachment; filename*=UTF-8''r%C3%A4kning.pdf", "räkning.pdf"),
			Arguments.of("attachment; filename=\"\"", null),
			Arguments.of("attachment", null),
			Arguments.of("!! not a header !!", null),
			Arguments.of(null, null));
	}

	@Test
	void storeTruncatesLongFileName() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		when(storedFileRepositoryMock.save(any(StoredFileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		final var result = service.store(BUCKET, ID, null, "attachment; filename=\"%s\"".formatted("a".repeat(300)), null, null, requestWith(CONTENT));

		// Assert
		assertThat(result.getFileName()).hasSize(255);
	}

	@Test
	void storeWithEmptyContent() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		// Act & Assert
		assertThatThrownBy(() -> service.store(BUCKET, ID, null, DISPOSITION, null, null, requestWith(new byte[0])))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("Content of the uploaded file must not be empty")
			.extracting("status").isEqualTo(BAD_REQUEST);

		verifyNoInteractions(storedFileRepositoryMock);
	}

	/**
	 * A raw request body carries no framework-enforced size limit, so an oversized upload must be refused whether or not
	 * the client declared its length honestly.
	 */
	@Test
	void storeWithDeclaredLengthOverTheLimit() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7), DataSize.ofBytes(4));
		final var request = requestWith(CONTENT);
		request.setContentType("application/octet-stream");

		// Act & Assert
		assertThatThrownBy(() -> service.store(BUCKET, ID, null, DISPOSITION, null, null, request))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("Content of the uploaded file exceeds the maximum size of 4 bytes")
			.extracting("status").isEqualTo(CONTENT_TOO_LARGE);

		verifyNoInteractions(storedFileRepositoryMock);
	}

	@Test
	void storeWithUndeclaredLengthOverTheLimit() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7), DataSize.ofBytes(4));
		final var request = new MockHttpServletRequest() {
			@Override
			public int getContentLength() {
				return -1;
			}

			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
		request.setContent(CONTENT);

		// Act & Assert
		assertThatThrownBy(() -> service.store(BUCKET, ID, null, DISPOSITION, null, null, request))
			.isInstanceOf(Problem.class)
			.extracting("status").isEqualTo(CONTENT_TOO_LARGE);

		verifyNoInteractions(storedFileRepositoryMock);
	}

	@Test
	void readTo() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));
		final var response = new MockHttpServletResponse();

		when(storedFileRepositoryMock.findSummary(BUCKET, ID)).thenReturn(Optional.of(summary(ID, FILE_NAME, "application/pdf", now().plusDays(1))));
		when(storedFileRepositoryMock.findContent(BUCKET, ID)).thenReturn(CONTENT);

		// Act
		service.readTo(BUCKET, ID, null, response);

		// Assert
		assertThat(response.getHeader(CONTENT_TYPE)).isEqualTo("application/pdf");
		assertThat(response.getHeader(CONTENT_DISPOSITION)).isEqualTo("attachment; filename=\"invoice-123.pdf\"");
		assertThat(response.getHeader(ETAG)).isEqualTo("\"%s\"".formatted(ETAG_VALUE));
		assertThat(response.getContentAsByteArray()).isEqualTo(CONTENT);
	}

	@Test
	void readToWithoutContentTypeAndFileName() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));
		final var response = new MockHttpServletResponse();

		when(storedFileRepositoryMock.findSummary(BUCKET, ID)).thenReturn(Optional.of(summary(ID, null, null, null)));
		when(storedFileRepositoryMock.findContent(BUCKET, ID)).thenReturn(CONTENT);

		// Act
		service.readTo(BUCKET, ID, null, response);

		// Assert
		assertThat(response.getHeader(CONTENT_TYPE)).isEqualTo("application/octet-stream");
		assertThat(response.getHeader(CONTENT_DISPOSITION)).isEqualTo("attachment; filename=\"%s\"".formatted(ID));
	}

	/**
	 * A matching If-None-Match must answer with a bare 304 without touching the content, whether the client sends the tag
	 * strongly, weakly, in a list or as a wildcard.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"\"2239ce4df9ee8db012834642ec801b55ba2c92b28bdd11f4d73d9c55d39f3b0a\"",
		"W/\"2239ce4df9ee8db012834642ec801b55ba2c92b28bdd11f4d73d9c55d39f3b0a\"",
		"\"other\", \"2239ce4df9ee8db012834642ec801b55ba2c92b28bdd11f4d73d9c55d39f3b0a\"",
		"*"
	})
	void readToWhenNotModified(final String ifNoneMatch) {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));
		final var response = new MockHttpServletResponse();

		when(storedFileRepositoryMock.findSummary(BUCKET, ID)).thenReturn(Optional.of(summary(ID)));

		// Act
		service.readTo(BUCKET, ID, ifNoneMatch, response);

		// Assert
		assertThat(response.getStatus()).isEqualTo(304);
		assertThat(response.getHeader(ETAG)).isEqualTo("\"%s\"".formatted(ETAG_VALUE));
		assertThat(response.getContentAsByteArray()).isEmpty();
		verify(storedFileRepositoryMock, never()).findContent(any(), any());
	}

	@Test
	void readToWhenIfNoneMatchDoesNotMatch() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));
		final var response = new MockHttpServletResponse();

		when(storedFileRepositoryMock.findSummary(BUCKET, ID)).thenReturn(Optional.of(summary(ID)));
		when(storedFileRepositoryMock.findContent(BUCKET, ID)).thenReturn(CONTENT);

		// Act
		service.readTo(BUCKET, ID, "\"stale\"", response);

		// Assert
		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentAsByteArray()).isEqualTo(CONTENT);
	}

	@Test
	void readToWhenNotFound() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));
		final var response = new MockHttpServletResponse();

		when(storedFileRepositoryMock.findSummary(BUCKET, ID)).thenReturn(Optional.empty());

		// Act & Assert
		assertThatThrownBy(() -> service.readTo(BUCKET, ID, null, response))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("No object with id [%s] exists in bucket [%s]".formatted(ID, BUCKET))
			.extracting("status").isEqualTo(NOT_FOUND);
	}

	@Test
	void readToWhenExpired() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));
		final var response = new MockHttpServletResponse();

		when(storedFileRepositoryMock.findSummary(BUCKET, ID)).thenReturn(Optional.of(summary(ID, FILE_NAME, "application/pdf", now().minusSeconds(1))));

		// Act & Assert
		assertThatThrownBy(() -> service.readTo(BUCKET, ID, null, response))
			.isInstanceOf(Problem.class)
			.extracting("status").isEqualTo(NOT_FOUND);

		verify(storedFileRepositoryMock, never()).findContent(any(), any());
	}

	/**
	 * The metadata and the content are fetched separately, so an object deleted in between has to be answered as one that
	 * was never there rather than with a half-written response.
	 */
	@Test
	void readToWhenContentDisappearsBetweenTheQueries() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));
		final var response = new MockHttpServletResponse();

		when(storedFileRepositoryMock.findSummary(BUCKET, ID)).thenReturn(Optional.of(summary(ID)));
		when(storedFileRepositoryMock.findContent(BUCKET, ID)).thenReturn(null);

		// Act & Assert
		assertThatThrownBy(() -> service.readTo(BUCKET, ID, null, response))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("No object with id [%s] exists in bucket [%s]".formatted(ID, BUCKET))
			.extracting("status").isEqualTo(NOT_FOUND);
	}

	@Test
	void readToWhenContentCannotBeWritten() throws Exception {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));
		final var response = Mockito.mock(HttpServletResponse.class);

		when(storedFileRepositoryMock.findSummary(BUCKET, ID)).thenReturn(Optional.of(summary(ID)));
		when(storedFileRepositoryMock.findContent(BUCKET, ID)).thenReturn(CONTENT);
		when(response.getOutputStream()).thenThrow(new IOException("boom"));

		// Act & Assert
		assertThatThrownBy(() -> service.readTo(BUCKET, ID, null, response))
			.isInstanceOf(Problem.class)
			.hasMessageContaining("Could not read content of object with id [%s] in bucket [%s]".formatted(ID, BUCKET))
			.extracting("status").isEqualTo(INTERNAL_SERVER_ERROR);
	}

	@Test
	void list() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		when(storedFileRepositoryMock.findPage(eq(BUCKET), eq(""), any(), eq(Limit.of(3))))
			.thenReturn(List.of(summary("a"), summary("b")));

		// Act
		final var result = service.list(BUCKET, null, 2);

		// Assert
		assertThat(result.getObjects()).extracting("id").containsExactly("a", "b");
		assertThat(result.isTruncated()).isFalse();
		assertThat(result.getNextContinuationToken()).isNull();
	}

	/**
	 * One object beyond the page is fetched to detect truncation, and must not leak into the page itself.
	 */
	@Test
	void listWhenTruncated() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		when(storedFileRepositoryMock.findPage(eq(BUCKET), eq("a"), any(), eq(Limit.of(3))))
			.thenReturn(List.of(summary("b"), summary("c"), summary("d")));

		// Act
		final var result = service.list(BUCKET, "a", 2);

		// Assert
		assertThat(result.getObjects()).extracting("id").containsExactly("b", "c");
		assertThat(result.isTruncated()).isTrue();
		assertThat(result.getNextContinuationToken()).isEqualTo("c");
	}

	@Test
	void listWhenBucketIsEmpty() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		when(storedFileRepositoryMock.findPage(eq(BUCKET), eq(""), any(), any())).thenReturn(List.of());

		// Act
		final var result = service.list(BUCKET, null, 1000);

		// Assert
		assertThat(result.getObjects()).isEmpty();
		assertThat(result.isTruncated()).isFalse();
	}

	/**
	 * Deleting is a statement rather than a load followed by a remove, so that a large object is not pulled into memory
	 * on its way out.
	 */
	@Test
	void delete() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		when(storedFileRepositoryMock.deleteByBucketAndId(BUCKET, ID)).thenReturn(1);

		// Act
		service.delete(BUCKET, ID);

		// Assert
		verify(storedFileRepositoryMock).deleteByBucketAndId(BUCKET, ID);
		verifyNoMoreInteractions(storedFileRepositoryMock);
	}

	@Test
	void deleteWhenNotFound() {
		// Arrange
		final var service = serviceWith(Duration.ofDays(7));

		when(storedFileRepositoryMock.deleteByBucketAndId(BUCKET, ID)).thenReturn(0);

		// Act
		service.delete(BUCKET, ID);

		// Assert
		verify(storedFileRepositoryMock).deleteByBucketAndId(BUCKET, ID);
		verifyNoMoreInteractions(storedFileRepositoryMock);
	}
}
