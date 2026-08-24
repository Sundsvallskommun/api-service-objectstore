package se.sundsvall.objectstore.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import se.sundsvall.objectstore.Application;
import se.sundsvall.objectstore.api.model.FileMetadata;
import se.sundsvall.objectstore.api.model.ObjectListing;
import se.sundsvall.objectstore.service.StorageService;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.MediaType.APPLICATION_PDF;

@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("junit")
class StorageResourceTest {

	private static final String BUCKET = "attachments";
	private static final String ID = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b";
	private static final String FILE_NAME = "invoice-123.pdf";
	private static final String ETAG = "2239ce4df9ee8db012834642ec801b55ba2c92b28bdd11f4d73d9c55d39f3b0a";
	private static final String BUCKET_PATH = "/{bucket}";
	private static final String OBJECT_PATH = "/{bucket}/{id}";
	private static final byte[] CONTENT = "file-content".getBytes(UTF_8);

	@MockitoBean
	private StorageService storageServiceMock;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void storeObject() {
		// Arrange
		when(storageServiceMock.store(eq(BUCKET), eq(ID), eq(APPLICATION_PDF.toString()), any(), isNull(), isNull(), any()))
			.thenReturn(FileMetadata.create().withId(ID).withBucket(BUCKET).withFileName(FILE_NAME).withEtag(ETAG));

		// Act
		final var response = webTestClient.put()
			.uri(OBJECT_PATH.replace("{bucket}", BUCKET).replace("{id}", ID))
			.contentType(APPLICATION_PDF)
			.header(CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(FILE_NAME))
			.bodyValue(CONTENT)
			.exchange()
			.expectStatus().isOk()
			.expectHeader().valueEquals(HttpHeaders.ETAG, "\"%s\"".formatted(ETAG))
			.expectBody(FileMetadata.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getId()).isEqualTo(ID);
		assertThat(response.getFileName()).isEqualTo(FILE_NAME);
		verify(storageServiceMock).store(eq(BUCKET), eq(ID), eq(APPLICATION_PDF.toString()), any(), isNull(), isNull(), any());
		verifyNoMoreInteractions(storageServiceMock);
	}

	/**
	 * A UTC expiry is used here deliberately — a numeric offset carries a plus, which a client has to percent-encode
	 * before it survives the query string.
	 */
	@Test
	void storeObjectWithExpiry() {
		// Arrange
		final var expiresAt = OffsetDateTime.parse("2026-08-25T12:30:00Z");

		when(storageServiceMock.store(eq(BUCKET), eq(ID), any(), isNull(), isNull(), eq(expiresAt), any())).thenReturn(FileMetadata.create().withId(ID).withEtag(ETAG));

		// Act
		webTestClient.put()
			.uri(builder -> builder.path(OBJECT_PATH).queryParam("expiresAt", "2026-08-25T12:30:00Z").build(Map.of("bucket", BUCKET, "id", ID)))
			.contentType(APPLICATION_PDF)
			.bodyValue(CONTENT)
			.exchange()
			.expectStatus().isOk();

		// Assert
		verify(storageServiceMock).store(eq(BUCKET), eq(ID), any(), isNull(), isNull(), eq(expiresAt), any());
		verifyNoMoreInteractions(storageServiceMock);
	}

	@Test
	void storeObjectWithCreateOnlyPrecondition() {
		// Arrange
		when(storageServiceMock.store(eq(BUCKET), eq(ID), any(), any(), eq("*"), isNull(), any()))
			.thenReturn(FileMetadata.create().withId(ID).withEtag(ETAG));

		// Act
		webTestClient.put()
			.uri(OBJECT_PATH.replace("{bucket}", BUCKET).replace("{id}", ID))
			.contentType(APPLICATION_PDF)
			.ifNoneMatch("*")
			.bodyValue(CONTENT)
			.exchange()
			.expectStatus().isOk();

		// Assert
		verify(storageServiceMock).store(eq(BUCKET), eq(ID), any(), any(), eq("*"), isNull(), any());
		verifyNoMoreInteractions(storageServiceMock);
	}

	@Test
	void listObjects() {
		// Arrange
		final var metadata = FileMetadata.create().withId(ID).withBucket(BUCKET).withFileName(FILE_NAME).withSize(12L);
		when(storageServiceMock.list(BUCKET, null, 1000))
			.thenReturn(ObjectListing.create().withObjects(List.of(metadata)).withTruncated(false));

		// Act
		final var response = webTestClient.get()
			.uri(builder -> builder.path(BUCKET_PATH).build(Map.of("bucket", BUCKET)))
			.exchange()
			.expectStatus().isOk()
			.expectBody(ObjectListing.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getObjects()).extracting(FileMetadata::getId).containsExactly(ID);
		assertThat(response.isTruncated()).isFalse();
		verify(storageServiceMock).list(BUCKET, null, 1000);
		verifyNoMoreInteractions(storageServiceMock);
	}

	@Test
	void listObjectsWithPaging() {
		// Arrange
		when(storageServiceMock.list(BUCKET, ID, 10))
			.thenReturn(ObjectListing.create().withObjects(List.of()).withTruncated(true).withNextContinuationToken(ID));

		// Act
		final var response = webTestClient.get()
			.uri(builder -> builder.path(BUCKET_PATH).queryParam("continuationToken", ID).queryParam("maxKeys", 10).build(Map.of("bucket", BUCKET)))
			.exchange()
			.expectStatus().isOk()
			.expectBody(ObjectListing.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.isTruncated()).isTrue();
		assertThat(response.getNextContinuationToken()).isEqualTo(ID);
		verify(storageServiceMock).list(BUCKET, ID, 10);
		verifyNoMoreInteractions(storageServiceMock);
	}

	@Test
	void readObject() {
		// Act
		webTestClient.get()
			.uri(OBJECT_PATH.replace("{bucket}", BUCKET).replace("{id}", ID))
			.exchange()
			.expectStatus().isOk();

		// Assert
		verify(storageServiceMock).readTo(eq(BUCKET), eq(ID), isNull(), any());
		verifyNoMoreInteractions(storageServiceMock);
	}

	@Test
	void readObjectWithIfNoneMatch() {
		// Act
		webTestClient.get()
			.uri(OBJECT_PATH.replace("{bucket}", BUCKET).replace("{id}", ID))
			.ifNoneMatch("\"%s\"".formatted(ETAG))
			.exchange()
			.expectStatus().isOk();

		// Assert
		verify(storageServiceMock).readTo(eq(BUCKET), eq(ID), eq("\"%s\"".formatted(ETAG)), any());
		verifyNoMoreInteractions(storageServiceMock);
	}

	@Test
	void deleteObject() {
		// Act
		webTestClient.delete()
			.uri(OBJECT_PATH.replace("{bucket}", BUCKET).replace("{id}", ID))
			.exchange()
			.expectStatus().isNoContent();

		// Assert
		verify(storageServiceMock).delete(BUCKET, ID);
		verifyNoMoreInteractions(storageServiceMock);
	}
}
