package se.sundsvall.objectstore.apptest;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import se.sundsvall.objectstore.Application;
import se.sundsvall.objectstore.integration.db.StoredFileRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.ETAG;
import static org.springframework.http.HttpHeaders.IF_NONE_MATCH;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_MODIFIED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;
import static org.springframework.http.MediaType.TEXT_PLAIN;

@WireMockAppTestSuite(files = "classpath:/StorageIT/", classes = Application.class)
@Sql(scripts = {
	"/db/scripts/truncate.sql",
	"/db/scripts/testdata-it.sql"
})
class StorageIT extends AbstractAppTest {

	private static final String BUCKET = "attachments";
	private static final String OTHER_BUCKET = "archive";
	private static final String EXISTING_ID = "11111111-1111-1111-1111-111111111111";
	private static final String REMOVABLE_ID = "33333333-3333-3333-3333-333333333333";
	private static final String MISSING_ID = "99999999-9999-9999-9999-999999999999";
	private static final String NEW_ID = "44444444-4444-4444-4444-444444444444";
	private static final String MIXED_CASE_ID = "aaaaaaa1-1111-1111-1111-11111111111a";
	private static final String EXISTING_ETAG = "c448faf851ca35959e15384db68a45027e8ab0bd19ba4e3fae4b649338a25fa2";
	private static final String UPLOADED_ETAG = "39ef3a7253e3e05fa992f66c0525cb2932da288e4894c0f146e2c9f55fbab22b";
	private static final String RESPONSE_FILE = "response.json";

	@Autowired
	private StoredFileRepository storedFileRepository;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@Test
	void test01_storeObject() {
		setupCall()
			.withServicePath(objectPath(BUCKET, NEW_ID))
			.withHttpMethod(PUT)
			.withContentType(TEXT_PLAIN)
			.withHeader(CONTENT_DISPOSITION, "attachment; filename=\"upload.txt\"")
			.withRequest("upload.txt")
			.withExpectedResponseStatus(OK)
			.withExpectedResponseHeader(ETAG, List.of(".*%s.*".formatted(UPLOADED_ETAG)))
			.sendRequest();

		assertThat(storedFileRepository.findByBucketAndId(BUCKET, NEW_ID))
			.hasValueSatisfying(entity -> {
				assertThat(entity.getFileName()).isEqualTo("upload.txt");
				assertThat(entity.getContentType()).isEqualTo(TEXT_PLAIN.toString());
				assertThat(entity.getEtag()).isEqualTo(UPLOADED_ETAG);
			});
	}

	@Test
	void test02_readObject() throws Exception {
		setupCall()
			.withServicePath(objectPath(BUCKET, EXISTING_ID))
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponseHeader(ETAG, List.of(".*%s.*".formatted(EXISTING_ETAG)))
			.withExpectedBinaryResponse("expected-content.txt")
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test03_listObjects() {
		setupCall()
			.withServicePath(bucketPath(BUCKET))
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test04_deleteObject() {
		setupCall()
			.withServicePath(objectPath(BUCKET, REMOVABLE_ID))
			.withHttpMethod(DELETE)
			.withExpectedResponseStatus(NO_CONTENT)
			.withExpectedResponseBodyIsNull()
			.sendRequestAndVerifyResponse();

		assertThat(storedFileRepository.findByBucketAndId(BUCKET, REMOVABLE_ID)).isEmpty();
	}

	@Test
	void test05_readObjectNotFound() {
		setupCall()
			.withServicePath(objectPath(BUCKET, MISSING_ID))
			.withHttpMethod(GET)
			.withExpectedResponseStatus(NOT_FOUND)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test06_readObjectWithInvalidId() {
		setupCall()
			.withServicePath(objectPath(BUCKET, "not-a-uuid"))
			.withHttpMethod(GET)
			.withExpectedResponseStatus(BAD_REQUEST)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test07_readObjectNotModified() {
		setupCall()
			.withServicePath(objectPath(BUCKET, EXISTING_ID))
			.withHttpMethod(GET)
			.withHeader(IF_NONE_MATCH, "\"%s\"".formatted(EXISTING_ETAG))
			.withExpectedResponseStatus(NOT_MODIFIED)
			.withExpectedResponseBodyIsNull()
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test08_listObjectsPaged() {
		setupCall()
			.withServicePath(bucketPath(BUCKET) + "?maxKeys=1")
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();
	}

	/**
	 * Storing to an id that already holds an object replaces it rather than adding a second one.
	 */
	@Test
	void test09_storeObjectOverExistingId() {
		setupCall()
			.withServicePath(objectPath(BUCKET, EXISTING_ID))
			.withHttpMethod(PUT)
			.withContentType(TEXT_PLAIN)
			.withHeader(CONTENT_DISPOSITION, "attachment; filename=\"upload.txt\"")
			.withRequest("upload.txt")
			.withExpectedResponseStatus(OK)
			.sendRequest();

		assertThat(storedFileRepository.count()).isEqualTo(3);
		assertThat(storedFileRepository.findByBucketAndId(BUCKET, EXISTING_ID))
			.hasValueSatisfying(entity -> {
				assertThat(entity.getFileName()).isEqualTo("upload.txt");
				assertThat(entity.getEtag()).isEqualTo(UPLOADED_ETAG);
			});
	}

	/**
	 * A wildcard If-None-Match refuses the store rather than overwriting what the id already holds.
	 */
	@Test
	void test10_storeObjectWithCreateOnlyPrecondition() {
		setupCall()
			.withServicePath(objectPath(BUCKET, EXISTING_ID))
			.withHttpMethod(PUT)
			.withContentType(TEXT_PLAIN)
			.withHeader(IF_NONE_MATCH, "*")
			.withRequest("upload.txt")
			.withExpectedResponseStatus(PRECONDITION_FAILED)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();

		assertThat(storedFileRepository.findByBucketAndId(BUCKET, EXISTING_ID))
			.hasValueSatisfying(entity -> assertThat(entity.getEtag()).isEqualTo(EXISTING_ETAG));
	}

	/**
	 * An id identifies an object only within its bucket. Storing an id that another bucket already holds adds a second,
	 * independent object rather than moving the first one, which is what keying on the id alone would do.
	 */
	@Test
	void test11_storeObjectInAnotherBucket() {
		setupCall()
			.withServicePath(objectPath(OTHER_BUCKET, EXISTING_ID))
			.withHttpMethod(PUT)
			.withContentType(TEXT_PLAIN)
			.withHeader(CONTENT_DISPOSITION, "attachment; filename=\"upload.txt\"")
			.withRequest("upload.txt")
			.withExpectedResponseStatus(OK)
			.withExpectedResponseHeader(ETAG, List.of(".*%s.*".formatted(UPLOADED_ETAG)))
			.sendRequest();

		assertThat(storedFileRepository.count()).isEqualTo(4);
		assertThat(storedFileRepository.findByBucketAndId(OTHER_BUCKET, EXISTING_ID))
			.hasValueSatisfying(entity -> assertThat(entity.getEtag()).isEqualTo(UPLOADED_ETAG));
		assertThat(storedFileRepository.findByBucketAndId(BUCKET, EXISTING_ID))
			.hasValueSatisfying(entity -> assertThat(entity.getEtag()).isEqualTo(EXISTING_ETAG));
	}

	/**
	 * A create-only store of an id nothing holds goes through, and goes in through the primary key rather than through a
	 * plain save.
	 */
	@Test
	void test12_storeObjectWithCreateOnlyPreconditionWhenIdIsFree() {
		setupCall()
			.withServicePath(objectPath(BUCKET, NEW_ID))
			.withHttpMethod(PUT)
			.withContentType(TEXT_PLAIN)
			.withHeader(IF_NONE_MATCH, "*")
			.withHeader(CONTENT_DISPOSITION, "attachment; filename=\"upload.txt\"")
			.withRequest("upload.txt")
			.withExpectedResponseStatus(OK)
			.withExpectedResponseHeader(ETAG, List.of(".*%s.*".formatted(UPLOADED_ETAG)))
			.sendRequest();

		assertThat(storedFileRepository.count()).isEqualTo(4);
		assertThat(storedFileRepository.findByBucketAndId(BUCKET, NEW_ID))
			.hasValueSatisfying(entity -> assertThat(entity.getEtag()).isEqualTo(UPLOADED_ETAG));
	}

	/**
	 * A UUID carries no case, so an object stored under an id spelled one way is the same object a read spells the other
	 * way. The id is canonicalized on the way in and the object is stored once, not twice.
	 */
	@Test
	void test13_storeObjectWithAnUppercaseId() {
		setupCall()
			.withServicePath(objectPath(BUCKET, MIXED_CASE_ID.toUpperCase(Locale.ROOT)))
			.withHttpMethod(PUT)
			.withContentType(TEXT_PLAIN)
			.withRequest("upload.txt")
			.withExpectedResponseStatus(OK)
			.sendRequest();

		setupCall()
			.withServicePath(objectPath(BUCKET, MIXED_CASE_ID.toLowerCase(Locale.ROOT)))
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponseHeader(ETAG, List.of(".*%s.*".formatted(UPLOADED_ETAG)))
			.sendRequest();

		assertThat(storedFileRepository.count()).isEqualTo(4);
		assertThat(storedFileRepository.findByBucketAndId(BUCKET, MIXED_CASE_ID.toLowerCase(Locale.ROOT))).isPresent();
	}

	/**
	 * A response header is written as ISO-8859-1, so a file name carrying anything outside it is encoded the way RFC 6266
	 * asks for. Written as it stands the container discards the whole header and the client is left with no name at all.
	 */
	@Test
	void test14_readObjectWithANonAsciiFileName() {
		setupCall()
			.withServicePath(objectPath(BUCKET, NEW_ID))
			.withHttpMethod(PUT)
			.withContentType(TEXT_PLAIN)
			.withHeader(CONTENT_DISPOSITION, "attachment; filename*=UTF-8''r%C3%A4kning-%E2%82%AC.pdf")
			.withRequest("upload.txt")
			.withExpectedResponseStatus(OK)
			.sendRequest();

		setupCall()
			.withServicePath(objectPath(BUCKET, NEW_ID))
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponseHeader(CONTENT_DISPOSITION, List.of(".*filename\\*=UTF-8''r%C3%A4kning-%E2%82%AC\\.pdf"))
			.sendRequest();
	}

	/**
	 * The content type is replayed as the content type of every later read, so one too long to be stored whole is refused
	 * rather than truncated — a client told its object was stored is entitled to get the type it sent back.
	 */
	@Test
	void test15_storeObjectWithUnusableContentType() {
		setupCall()
			.withServicePath(objectPath(BUCKET, NEW_ID))
			.withHttpMethod(PUT)
			.withHeader(CONTENT_TYPE, "text/plain;charset=utf-8;x=%s".formatted("a".repeat(300)))
			.withRequest("upload.txt")
			.withExpectedResponseStatus(BAD_REQUEST)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();

		assertThat(storedFileRepository.findByBucketAndId(BUCKET, NEW_ID)).isEmpty();
	}

	/**
	 * The circuit breaker guards the methods declared on the repository interfaces, and a method inherited from
	 * JpaRepository is declared elsewhere — which once left every write unguarded while every read was guarded. A store
	 * goes through the upsert declared on the custom interface, which is annotated, and this fails if the write ever
	 * moves back to an inherited method.
	 */
	@Test
	void test16_storeIsGuardedByTheCircuitBreaker() {
		// Counting events rather than reading the metrics of the breaker, whose window holds only the last ten calls and
		// is long full by the time this runs.
		final var guardedCalls = new AtomicInteger();
		circuitBreakerRegistry.circuitBreaker("storedFileRepository").getEventPublisher()
			.onSuccess(event -> guardedCalls.incrementAndGet());

		setupCall()
			.withServicePath(objectPath(BUCKET, NEW_ID))
			.withHttpMethod(PUT)
			.withContentType(TEXT_PLAIN)
			.withRequest("upload.txt")
			.withExpectedResponseStatus(OK)
			.sendRequest();

		assertThat(guardedCalls).hasPositiveValue();
	}

	/**
	 * The path of a bucket. Built here rather than written out at every call so that the prefix the object endpoints sit
	 * under lives in one place.
	 *
	 * @param  bucket the bucket
	 * @return        the path of the bucket
	 */
	private static String bucketPath(final String bucket) {
		return "/objects/%s".formatted(bucket);
	}

	/**
	 * The path of an object.
	 *
	 * @param  bucket the bucket holding the object
	 * @param  id     the id identifying the object
	 * @return        the path of the object
	 */
	private static String objectPath(final String bucket, final String id) {
		return "%s/%s".formatted(bucketPath(bucket), id);
	}
}
