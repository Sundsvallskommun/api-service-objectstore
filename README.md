# Object Store

_A deliberately minimal, S3-shaped object store backed by MariaDB. Intended as a stand-in until a real S3-compatible
store is in place._

## Getting Started

### Prerequisites

- **Java 25 or higher**
- **Maven**
- **MariaDB**
- **Git**
- **Docker** (for the testcontainers-based tests)

### Installation

1. **Clone the repository:**

   ```bash
   git clone https://github.com/Sundsvallskommun/api-service-objectstore.git
   cd api-service-objectstore
   ```
2. **Configure the application:**

   See [Configuration](#configuration).

3. **Build and run the application:**

   ```bash
   mvn spring-boot:run
   ```

## Design

The service stores objects (files) and nothing else. It supports **create, read and delete — no update**, mirroring the
subset of S3 the consuming services need.

- **Buckets are implicit.** A bucket is a string namespace on the object row, not an entity. Storing the first object in
  a bucket creates it; removing the last object leaves nothing behind. There are no bucket-level permissions, policies
  or versioning. An object is keyed by its bucket and its id together, so the same id stored in two buckets is two
  unrelated objects rather than one.
- **Bucket names follow the S3 naming rules** (lowercase letters, digits and hyphens, 3–63 characters) so they remain
  valid bucket names after a migration to a real S3. Since buckets live at the root of the service, the names the
  framework serves itself — `actuator`, `api-docs`, `csrf`, `error`, `favicon.ico`, `h2-console`, `swagger-resources`,
  `swagger-ui`, `swagger-ui.html` and `webjars` — are excluded in the request mappings and cannot be used as bucket
  names. Adding a root-level endpoint to this service means adding it to that exclusion list.
- **The client chooses the object id, and it must be a UUID.** Storing is a `PUT` to the id, as it is in S3, and the id
  is what later reads and deletes address the object by. Requiring a UUID rather than a free-form S3 key is what keeps
  key validation out of the service entirely — a UUID has no path separators, no relative segments and a fixed length —
  and a UUID is still a legal S3 key after a migration. A UUID carries no case, so the id is lowercased on the way in
  and an id spelled in another case addresses the object already stored rather than a second one; the metadata that
  comes back carries the id the object is stored under. The name of the uploaded file is stored alongside it as
  metadata and echoed back in the `Content-Disposition` header of a read, but it never identifies the object.
- **Storing to an id that already holds an object replaces it**, as it does in S3. There is no `409`. This is what makes
  a store idempotent: a client that retries one it never saw the response to ends up with exactly one object rather
  than an orphaned second copy. It also means a consumer that deliberately re-stores a different file under the same id
  has replaced it, which is the one sense in which this service updates anything.
- **A client that wants the opposite sends `If-None-Match: *`** and gets a `412` instead of an overwrite, matching the
  conditional writes of S3. The check runs before the request body is read, so a refused store never pulls the content
  across the wire, and the primary key refuses the insert again at the end, so two simultaneous create-only stores of
  the same id cannot both succeed. An expired object does not stand in the way of one, since it is already invisible to
  every read. No other value is accepted in the header — a specific entity tag is refused with a `400` rather than
  ignored, because a client that sends one is asking for a guarantee and silently overwriting is the one answer it
  must not get.
- **Content is sent and returned as the raw request body**, as with the S3 `PutObject` and `GetObject` calls — not as
  `multipart/form-data`. The content type is taken from the `Content-Type` header and replayed on reads. Since it is
  replayed rather than merely recorded, a header that cannot be stored whole is refused with a `400` rather than
  truncated or dropped — a client told its object was stored is entitled to get the type it sent back, and both of
  those hand it a different one. That covers a header longer than the 255 characters the column holds and one that is
  not a media type at all. Sending no `Content-Type` is fine; reads of an object stored without one fall back to
  `application/octet-stream`.
- **The name of the uploaded file comes from the `Content-Disposition` header**, is client controlled and is sanitized —
  the directory some clients send along with it is stripped, as are the characters that would let it break out of the
  `Content-Disposition` header of a read and every character that carries no glyph of its own, the bidirectional
  overrides that would otherwise let a name render as a different file type included. It is capped at 255 characters
  and dropped entirely when the header is absent, malformed or leaves nothing usable, in which case reads fall back to
  naming the file after its id. On the way out it is encoded as RFC 6266 asks for, carrying both a plain `filename`
  and a `filename*` — a response header is written as ISO-8859-1, and a name holding anything outside it would
  otherwise make the container discard the whole header and leave the client with no name at all.
- **Every object carries an `ETag`** — the hex encoded SHA-256 digest of its content, returned on store and on every
  read. A read whose `If-None-Match` matches is answered with a bare `304` without the content being fetched at all: a
  read looks up the metadata first and goes back for the content only once it is clear the client is getting it.
- **Uploads are size limited by `storage.max-object-size`.** A raw request body carries no framework-enforced limit, so
  this property is the only thing bounding the memory an upload consumes. Oversized uploads are refused with `413`,
  whether or not the client declared its length honestly. The refusal is decided before the body is read, so
  `server.tomcat.max-swallow-size` tracks the limit — see [Configuration](#configuration) — to keep a refused upload
  from reaching the client as a connection reset instead of as the status.
- **An empty body is refused with a `400`.** A zero-byte object is far more often a client that failed to attach
  anything than one that meant it, and storing it would hand every later read an object that looks stored and is not.
- **Deletes are idempotent**, matching S3 — deleting an object that does not exist returns `204`, not `404`.
- **Objects expire.** An upload sets its own expiry with the `expiresAt` query parameter, and one that carries none is
  given the configured default time to live — or no expiry at all, when that is left unset. An expiry that has already
  passed is refused with a `400` rather than honoured, since it would store an object invisible to the very next read.
  Expired objects are invisible to reads and lists immediately, and a scheduled job removes them from the database.

### Migrating to a real S3

Consumers should hide this service behind a small storage interface of their own (store / fetch / delete). Migration is
then a matter of writing one S3-SDK-backed implementation and swapping the bean — a store is already a `PUT` of a raw
body to a client-chosen key, the bucket of every object maps 1:1 onto an S3 bucket, and its id is a legal S3 object key
as it stands. **The mapping of a municipality onto S3 buckets is deliberately left open** — this service carries no
municipality ID, so the choice (bucket per municipality vs. municipality as key prefix) is made at migration time.

Migrating the stored files themselves is not supported; the service is intended for short-lived objects such as
attachments that are discarded once sent.

## API Documentation

Access the API documentation via Swagger UI:

- **Swagger UI:** [http://localhost:8080/api-docs](http://localhost:8080/api-docs)

The checked-in specification lives in `src/integration-test/resources/api/openapi.yaml` and is verified against the
running service by `OpenApiSpecificationIT`.

### API Endpoints

|  Method  |       Path       |                                                                                                                             Description                                                                                                                              |
|----------|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PUT`    | `/{bucket}/{id}` | Store an object under a client-chosen UUID. Content is the raw request body; `Content-Type`, `Content-Disposition` and the `expiresAt` query parameter are optional. Returns `200` with the metadata and an `ETag`. Replaces any object already stored under the id. |
| `GET`    | `/{bucket}`      | List a page of the objects in a bucket, ordered by id. Optional `continuationToken` and `maxKeys` (1–1000, default 1000) query parameters.                                                                                                                           |
| `GET`    | `/{bucket}/{id}` | Read an object. Returns the raw bytes and an `ETag`. Honours `If-None-Match` with a `304`.                                                                                                                                                                           |
| `DELETE` | `/{bucket}/{id}` | Delete an object. Idempotent.                                                                                                                                                                                                                                        |

### Example Requests

```bash
# Store an object under an id you generated yourself
ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
curl -X PUT "http://localhost:8080/attachments/$ID" \
  -H "Content-Type: application/pdf" \
  -H 'Content-Disposition: attachment; filename="invoice-123.pdf"' \
  --data-binary "@invoice-123.pdf"
# {"id":"d1b2d33e-...","bucket":"attachments","fileName":"invoice-123.pdf","etag":"9f86d081...", ... }

# Store it again — the same call replaces it rather than adding a second object
curl -X PUT "http://localhost:8080/attachments/$ID" --data-binary "@invoice-123.pdf"

# Refuse to replace it — 412 when the id is already taken
curl -X PUT "http://localhost:8080/attachments/$ID" -H 'If-None-Match: *' --data-binary "@invoice-123.pdf"

# Read it back
curl "http://localhost:8080/attachments/$ID" -O -J

# Read it back only if it changed
curl "http://localhost:8080/attachments/$ID" \
  -H 'If-None-Match: "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"'

# List the bucket, a page at a time
curl "http://localhost:8080/attachments?maxKeys=100"
curl "http://localhost:8080/attachments?maxKeys=100&continuationToken=d1b2d33e-1b0c-4a10-9a1a-4a0e9e1f6f2b"

# Delete it
curl -X DELETE "http://localhost:8080/attachments/$ID"
```

An explicit expiry goes in the `expiresAt` query parameter and has to be percent-encoded — an unencoded `+` in a query
string decodes to a space and the timestamp then fails to parse:

```bash
curl -X PUT "http://localhost:8080/attachments/$ID?expiresAt=2026-08-25T14:30:00%2B02:00" --data-binary "@invoice-123.pdf"
```

## Configuration

### Key Configuration Parameters

- **Server Port:**

  ```yaml
  server:
    port: 8080
  ```
- **Database Settings:**

  ```yaml
  spring:
    datasource:
      url: jdbc:mariadb://localhost:3306/your_database
      username: your_db_username
      password: your_db_password
  ```
- **Default time to live** — applied when an upload carries no explicit `expiresAt`. Omit the property to store objects
  without an expiry.

  ```yaml
  storage:
    default-time-to-live: P7D
  ```
- **Cleanup job** — removes expired objects. Set `cron` to `-` to disable.

  ```yaml
  scheduler:
    cleanup:
      name: cleanup
      cron: "0 0 3 * * *"
      lock-at-most-for: PT10M
      maximum-execution-time: PT5M
  ```
- **Maximum object size** — objects are held in memory during upload and download, so keep this modest. Since content
  arrives as a raw request body there is no framework-enforced limit, so this property is the only bound on the memory
  an upload consumes.

  The default of 15 MB is chosen to sit under the 16 MB `max_allowed_packet` that a MariaDB server ships with, since an
  object travels to the database inside a single statement. Raising it means raising `max_allowed_packet` to match —
  otherwise a large upload is accepted, transferred in full, and only then refused by the database.

  Tomcat discards at most `server.tomcat.max-swallow-size` of a request body it has decided not to read, and an upload
  refused with a `413` or a `412` is refused before its body is read. That setting therefore tracks this property rather
  than being given a value of its own, so that a refusal reaches the client as the status rather than as a connection
  reset.

  ```yaml
  storage:
    max-object-size: 15MB
  server:
    tomcat:
      max-swallow-size: ${storage.max-object-size}
  ```

  ```ini
  # my.cnf on the database server, needed only when max-object-size is raised past 15MB
  [mysqld]
  max_allowed_packet = 64M
  ```

### Database Initialization

The project uses [Flyway](https://github.com/flyway/flyway) for database migrations. Flyway is disabled by default, so
enable it to populate the schema on startup:

```yaml
spring:
  flyway:
    enabled: true
```

## Contributing

Contributions are welcome! Please
see [CONTRIBUTING.md](https://github.com/Sundsvallskommun/.github/blob/main/.github/CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the [MIT License](LICENSE).

## Code status

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-objectstore&metric=alert_status)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-objectstore)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-objectstore&metric=reliability_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-objectstore)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-objectstore&metric=security_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-objectstore)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-objectstore&metric=sqale_rating)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-objectstore)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-objectstore&metric=vulnerabilities)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-objectstore)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=Sundsvallskommun_api-service-objectstore&metric=bugs)](https://sonarcloud.io/summary/overall?id=Sundsvallskommun_api-service-objectstore)

---

© 2026 Sundsvalls kommun
