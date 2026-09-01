package se.sundsvall.objectstore.api;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.Violation;
import se.sundsvall.objectstore.Application;
import se.sundsvall.objectstore.service.StorageService;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;

@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("junit")
class StorageResourceFailureTest {

	private static final String BUCKET = "attachments";
	private static final String ID = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b";
	private static final String BUCKET_PATH = "/objects/{bucket}";

	private static final String OBJECT_PATH = "/objects/{bucket}/{id}";
	private static final String INVALID_BUCKET = "Not-A-Valid-Bucket";
	private static final String INVALID_ID = "not-a-uuid";
	private static final byte[] CONTENT = "file-content".getBytes(UTF_8);

	@MockitoBean
	private StorageService storageServiceMock;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void storeObjectWithInvalidBucket() {
		// Act
		final var response = webTestClient.put()
			.uri("/objects/%s/%s".formatted(INVALID_BUCKET, ID))
			.contentType(APPLICATION_OCTET_STREAM)
			.bodyValue(CONTENT)
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getTitle()).isEqualTo("Constraint Violation");
		assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("storeObject.bucket", "not a valid bucket name"));

		verifyNoInteractions(storageServiceMock);
	}

	@Test
	void storeObjectWithInvalidId() {
		// Act
		final var response = webTestClient.put()
			.uri("/objects/%s/%s".formatted(BUCKET, INVALID_ID))
			.contentType(APPLICATION_OCTET_STREAM)
			.bodyValue(CONTENT)
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("storeObject.id", "not a valid UUID"));

		verifyNoInteractions(storageServiceMock);
	}

	/**
	 * An expiry that has already passed would store an object that is invisible to the very next read, so it is refused
	 * rather than honoured.
	 */
	@Test
	void storeObjectWithExpiryInThePast() {
		// Act
		final var response = webTestClient.put()
			.uri(builder -> builder.path(OBJECT_PATH).queryParam("expiresAt", "2020-01-01T00:00:00Z").build(Map.of("bucket", BUCKET, "id", ID)))
			.contentType(APPLICATION_OCTET_STREAM)
			.bodyValue(CONTENT)
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("storeObject.expiresAt", "not a point in time in the future"));

		verifyNoInteractions(storageServiceMock);
	}

	@Test
	void listObjectsWithInvalidContinuationToken() {
		// Act
		final var response = webTestClient.get()
			.uri(builder -> builder.path(BUCKET_PATH).queryParam("continuationToken", INVALID_ID).build(Map.of("bucket", BUCKET)))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("listObjects.continuationToken", "not a valid UUID"));

		verifyNoInteractions(storageServiceMock);
	}

	@ParameterizedTest
	@ValueSource(ints = {
		0, 1001
	})
	void listObjectsWithInvalidMaxKeys(final int maxKeys) {
		// Act
		final var response = webTestClient.get()
			.uri(builder -> builder.path(BUCKET_PATH).queryParam("maxKeys", maxKeys).build(Map.of("bucket", BUCKET)))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field)
			.containsExactly("listObjects.maxKeys");

		verifyNoInteractions(storageServiceMock);
	}

	@Test
	void listObjectsWithInvalidBucket() {
		// Act
		final var response = webTestClient.get()
			.uri(builder -> builder.path(BUCKET_PATH).build(Map.of("bucket", INVALID_BUCKET)))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("listObjects.bucket", "not a valid bucket name"));

		verifyNoInteractions(storageServiceMock);
	}

	@Test
	void readObjectWithInvalidBucket() {
		// Act
		final var response = webTestClient.get()
			.uri("/objects/%s/%s".formatted(INVALID_BUCKET, ID))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("readObject.bucket", "not a valid bucket name"));

		verifyNoInteractions(storageServiceMock);
	}

	@Test
	void readObjectWithInvalidId() {
		// Act
		final var response = webTestClient.get()
			.uri("/objects/%s/%s".formatted(BUCKET, INVALID_ID))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("readObject.id", "not a valid UUID"));

		verifyNoInteractions(storageServiceMock);
	}

	/**
	 * The object endpoints sit under a prefix of their own so that they cannot shadow the paths served by the framework
	 * itself. These pin that they do not.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"/api-docs", "/swagger-ui/index.html", "/swagger-ui/swagger-initializer.js"
	})
	void frameworkPathsAreServed(final String path) {
		// Act
		webTestClient.get()
			.uri(path)
			.exchange()
			.expectStatus().isOk();

		// Assert
		verifyNoInteractions(storageServiceMock);
	}

	/**
	 * Paths the framework serves no resource for must fall through to a 404 rather than be treated as a bucket, which
	 * would answer with a constraint violation instead.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"/webjars/swagger-ui/index.css", "/favicon.ico", "/error"
	})
	void reservedPathsAreNotHandledAsObjects(final String path) {
		// Act
		webTestClient.get()
			.uri(path)
			.exchange()
			.expectStatus().value(status -> assertThat(status).isNotEqualTo(BAD_REQUEST.value()));

		// Assert
		verifyNoInteractions(storageServiceMock);
	}

	@Test
	void deleteObjectWithInvalidBucket() {
		// Act
		final var response = webTestClient.delete()
			.uri("/objects/%s/%s".formatted(INVALID_BUCKET, ID))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("deleteObject.bucket", "not a valid bucket name"));

		verifyNoInteractions(storageServiceMock);
	}

	@Test
	void deleteObjectWithInvalidId() {
		// Act
		final var response = webTestClient.delete()
			.uri("/objects/%s/%s".formatted(BUCKET, INVALID_ID))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactly(tuple("deleteObject.id", "not a valid UUID"));

		verifyNoInteractions(storageServiceMock);
	}
}
