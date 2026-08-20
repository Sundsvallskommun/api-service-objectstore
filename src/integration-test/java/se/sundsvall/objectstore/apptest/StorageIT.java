package se.sundsvall.objectstore.apptest;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;
import se.sundsvall.objectstore.Application;
import se.sundsvall.objectstore.integration.db.StoredFileRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
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
	private static final String EXISTING_ETAG = "c448faf851ca35959e15384db68a45027e8ab0bd19ba4e3fae4b649338a25fa2";
	private static final String UPLOADED_ETAG = "39ef3a7253e3e05fa992f66c0525cb2932da288e4894c0f146e2c9f55fbab22b";
	private static final String RESPONSE_FILE = "response.json";

	@Autowired
	private StoredFileRepository storedFileRepository;

	@Test
	void test01_storeObject() {
		setupCall()
			.withServicePath("/%s/%s".formatted(BUCKET, NEW_ID))
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

	/**
	 * Storing to an id that already holds an object replaces it rather than adding a second one.
	 */
	@Test
	void test09_storeObjectOverExistingId() {
		setupCall()
			.withServicePath("/%s/%s".formatted(BUCKET, EXISTING_ID))
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

	@Test
	void test02_readObject() throws Exception {
		setupCall()
			.withServicePath("/%s/%s".formatted(BUCKET, EXISTING_ID))
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponseHeader(ETAG, List.of(".*%s.*".formatted(EXISTING_ETAG)))
			.withExpectedBinaryResponse("expected-content.txt")
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test03_listObjects() {
		setupCall()
			.withServicePath("/%s".formatted(BUCKET))
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test04_deleteObject() {
		setupCall()
			.withServicePath("/%s/%s".formatted(BUCKET, REMOVABLE_ID))
			.withHttpMethod(DELETE)
			.withExpectedResponseStatus(NO_CONTENT)
			.withExpectedResponseBodyIsNull()
			.sendRequestAndVerifyResponse();

		assertThat(storedFileRepository.findByBucketAndId(BUCKET, REMOVABLE_ID)).isEmpty();
	}

	@Test
	void test05_readObjectNotFound() {
		setupCall()
			.withServicePath("/%s/%s".formatted(BUCKET, MISSING_ID))
			.withHttpMethod(GET)
			.withExpectedResponseStatus(NOT_FOUND)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test06_readObjectWithInvalidId() {
		setupCall()
			.withServicePath("/%s/not-a-uuid".formatted(BUCKET))
			.withHttpMethod(GET)
			.withExpectedResponseStatus(BAD_REQUEST)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test07_readObjectNotModified() {
		setupCall()
			.withServicePath("/%s/%s".formatted(BUCKET, EXISTING_ID))
			.withHttpMethod(GET)
			.withHeader(IF_NONE_MATCH, "\"%s\"".formatted(EXISTING_ETAG))
			.withExpectedResponseStatus(NOT_MODIFIED)
			.withExpectedResponseBodyIsNull()
			.sendRequestAndVerifyResponse();
	}

	@Test
	void test08_listObjectsPaged() {
		setupCall()
			.withServicePath("/%s?maxKeys=1".formatted(BUCKET))
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse(RESPONSE_FILE)
			.sendRequestAndVerifyResponse();
	}

	/**
	 * A wildcard If-None-Match refuses the store rather than overwriting what the id already holds.
	 */
	@Test
	void test10_storeObjectWithCreateOnlyPrecondition() {
		setupCall()
			.withServicePath("/%s/%s".formatted(BUCKET, EXISTING_ID))
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
			.withServicePath("/%s/%s".formatted(OTHER_BUCKET, EXISTING_ID))
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
			.withServicePath("/%s/%s".formatted(BUCKET, NEW_ID))
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
}
