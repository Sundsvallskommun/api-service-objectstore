package se.sundsvall.objectstore.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.sundsvall.dept44.common.validators.annotation.ValidUuid;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.objectstore.api.model.FileMetadata;
import se.sundsvall.objectstore.api.model.ObjectListing;
import se.sundsvall.objectstore.service.StorageService;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.ETAG;
import static org.springframework.http.HttpHeaders.IF_NONE_MATCH;
import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

@RestController
@Validated
@Tag(name = "Storage", description = "Storage of objects")
@ApiResponses(value = {
	@ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(oneOf = {
		Problem.class, ConstraintViolationProblem.class
	}))),
	@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
})
class StorageResource {

	/**
	 * Bucket names follow the naming rules of S3: lowercase letters, digits and hyphens, 3-63 characters.
	 */
	static final String BUCKET_PATTERN = "^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$";

	/**
	 * The path of a bucket. Since buckets sit at the root of the service, the first segment is matched with an exclusion of
	 * the paths served by the framework itself (the index page, the OpenAPI specification, the Swagger UI and its webjars,
	 * actuator and the error page). Excluding them in the mapping rather than in the validation of the bucket name is what
	 * lets those requests fall through to the handlers serving them — a request that merely fails validation never reaches
	 * them. The consequence is that the excluded names cannot be used as bucket names.
	 */
	private static final String BUCKET_PATH = "/{bucket:(?!actuator$|api-docs$|csrf$|error$|favicon\\.ico$|h2-console$|swagger-resources$|swagger-ui$|swagger-ui\\.html$|webjars$).+}";

	private static final String OBJECT_PATH = BUCKET_PATH + "/{id}";

	private static final String BUCKET_MESSAGE = "not a valid bucket name";

	/**
	 * The largest page a listing returns, matching the page size of the S3 ListObjectsV2 call.
	 */
	private static final String MAX_KEYS_DEFAULT = "1000";

	private final StorageService storageService;

	StorageResource(final StorageService storageService) {
		this.storageService = storageService;
	}

	@PutMapping(path = OBJECT_PATH, consumes = ALL_VALUE, produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "Store an object",
		description = "The content is sent as the raw request body and its media type is stored and replayed on reads. Any media type is accepted — the ones listed here are only what the documentation offers to pick from. Buckets are implicit — storing the first object in a bucket creates it. Storing to an id that already holds an object replaces it, as it does in S3, which is what makes a retried store safe. A client that wants the opposite sends If-None-Match with a wildcard and gets a 412 instead of an overwrite.",
		requestBody = @RequestBody(required = true, description = "The content of the object", content = {
			@Content(mediaType = APPLICATION_OCTET_STREAM_VALUE, schema = @Schema(type = "string", format = "binary")),
			@Content(mediaType = APPLICATION_PDF_VALUE, schema = @Schema(type = "string", format = "binary")),
			@Content(mediaType = IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary")),
			@Content(mediaType = TEXT_PLAIN_VALUE, schema = @Schema(type = "string", format = "binary"))
		}),
		responses = {
			@ApiResponse(responseCode = "200", description = "Successful operation", headers = @Header(name = ETAG, schema = @Schema(type = "string")), useReturnTypeSchema = true),
			@ApiResponse(responseCode = "412", description = "Precondition failed", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class))),
			@ApiResponse(responseCode = "413", description = "Payload too large", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
		})
	ResponseEntity<FileMetadata> storeObject(
		final HttpServletRequest request,
		@Parameter(name = "bucket", description = "Bucket name", example = "attachments") @PathVariable @Pattern(regexp = BUCKET_PATTERN, message = BUCKET_MESSAGE) final String bucket,
		@Parameter(name = "id", description = "Object id, chosen by the client", example = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b") @PathVariable @ValidUuid final String id,
		@Parameter(hidden = true) @RequestHeader(value = CONTENT_TYPE, required = false) final String contentType,
		@Parameter(name = CONTENT_DISPOSITION, description = "The name of the file is taken from the filename parameter of this header", example = "attachment; filename=\"invoice-123.pdf\"") @RequestHeader(
			value = CONTENT_DISPOSITION,
			required = false) final String contentDisposition,
		@Parameter(name = IF_NONE_MATCH,
			description = "Send a wildcard to refuse the store with a 412 rather than overwrite an object already held under the id. No other value is accepted.",
			example = "*") @RequestHeader(value = IF_NONE_MATCH, required = false) final String ifNoneMatch,
		@Parameter(name = "expiresAt", description = "Point in time when the object expires. Defaults to the configured time to live.", example = "2026-08-25T14:30:00+02:00") @RequestParam(required = false) @DateTimeFormat(
			iso = DATE_TIME) final OffsetDateTime expiresAt) {

		final var metadata = storageService.store(bucket, id, contentType, contentDisposition, ifNoneMatch, expiresAt, request);

		return ok().eTag(metadata.getEtag()).body(metadata);
	}

	@GetMapping(path = BUCKET_PATH, produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "List the objects in a bucket",
		description = "Objects are returned ordered by id, a page at a time. A truncated listing carries the token to pass as continuationToken to fetch the next page.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Successful operation", useReturnTypeSchema = true)
		})
	ResponseEntity<ObjectListing> listObjects(
		@Parameter(name = "bucket", description = "Bucket name", example = "attachments") @PathVariable @Pattern(regexp = BUCKET_PATTERN, message = BUCKET_MESSAGE) final String bucket,
		@Parameter(name = "continuationToken",
			description = "Token returned by the previous page. Omit to start from the beginning.",
			example = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b") @RequestParam(required = false) @ValidUuid(nullable = true) final String continuationToken,
		@Parameter(name = "maxKeys", description = "Maximum number of objects to return", example = "1000") @RequestParam(required = false,
			defaultValue = MAX_KEYS_DEFAULT) @Min(1) @Max(1000) final int maxKeys) {

		return ok(storageService.list(bucket, continuationToken, maxKeys));
	}

	/**
	 * Any media type is producible, because the content type of a read is whatever the object was stored with rather than
	 * anything the mapping can know in advance. Naming one here would answer a client that asks for the very type its
	 * object happens to be with a 406. The body is described on the response instead, since the handler writes to the
	 * response itself and so has no return type for the specification to be derived from.
	 */
	@GetMapping(path = OBJECT_PATH, produces = ALL_VALUE)
	@Operation(summary = "Read an object",
		description = "The content is returned as the raw response body. A request whose If-None-Match matches the ETag of the object is answered with a bare 304.",
		responses = {
			@ApiResponse(responseCode = "200",
				description = "Successful operation",
				headers = @Header(name = ETAG, schema = @Schema(type = "string")),
				content = @Content(mediaType = APPLICATION_OCTET_STREAM_VALUE, schema = @Schema(type = "string", format = "binary"))),
			@ApiResponse(responseCode = "304", description = "Not modified"),
			@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
		})
	void readObject(
		final HttpServletResponse response,
		@Parameter(name = "bucket", description = "Bucket name", example = "attachments") @PathVariable @Pattern(regexp = BUCKET_PATTERN, message = BUCKET_MESSAGE) final String bucket,
		@Parameter(name = "id", description = "Object id", example = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b") @PathVariable @ValidUuid final String id,
		@Parameter(name = IF_NONE_MATCH, description = "Entity tag the client already holds", example = "\"9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08\"") @RequestHeader(value = IF_NONE_MATCH,
			required = false) final String ifNoneMatch) {

		storageService.readTo(bucket, id, ifNoneMatch, response);
	}

	@DeleteMapping(path = OBJECT_PATH, produces = ALL_VALUE)
	@Operation(summary = "Delete an object", description = "Deletion is idempotent — deleting an object that does not exist is not an error.", responses = {
		@ApiResponse(responseCode = "204", description = "Successful operation", useReturnTypeSchema = true)
	})
	ResponseEntity<Void> deleteObject(
		@Parameter(name = "bucket", description = "Bucket name", example = "attachments") @PathVariable @Pattern(regexp = BUCKET_PATTERN, message = BUCKET_MESSAGE) final String bucket,
		@Parameter(name = "id", description = "Object id", example = "d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b") @PathVariable @ValidUuid final String id) {

		storageService.delete(bucket, id);

		return noContent().build();
	}
}
