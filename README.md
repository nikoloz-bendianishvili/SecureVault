# SecureVault

A document-sharing REST API built with Spring Boot. Users register, verify their email, log in with JWT access tokens and rotating refresh tokens, create documents and share them read-only with other users. Administrators manage roles and account status and can inspect an audit trail of security-relevant events.

## Tech stack

- Java 21, Spring Boot 4.1, Maven
- Spring Web MVC, Spring Security, Spring Data JPA (Hibernate), Bean Validation
- PostgreSQL 17 with Flyway migrations
- JWT via `jjwt` (HS256)
- Spring Mail, with [Mailpit](https://mailpit.axllent.org/) as a local SMTP server
- JUnit 5, Mockito, MockMvc, Testcontainers

## Getting started

### Prerequisites

- JDK 21
- Docker with Docker Compose

### 1. Configure the environment

```bash
cp .env.example .env
```

Generate a JWT signing key and put it into `JWT_SECRET` in `.env`:

```bash
openssl rand -base64 32
```

| Variable | Purpose | Local default |
|---|---|---|
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL database and credentials, used by Docker Compose and the app | `securevault` / `securevault` / `change-me` |
| `DB_URL` | JDBC URL of the database | `jdbc:postgresql://localhost:5432/securevault` |
| `MAIL_HOST`, `MAIL_PORT` | SMTP server | `localhost` / `1025` (Mailpit) |
| `JWT_SECRET` | Base64-encoded HMAC key, at least 256 bits | must be generated |
| `ADMIN_MAIL`, `ADMIN_PASSWORD` | Admin account created on first startup | `admin@securevault.local` / `change-me-admin-password` |

`.env` is git-ignored. The application reads it on startup through `spring.config.import`; real environment variables work as well and take precedence.

### 2. Start PostgreSQL and Mailpit

```bash
docker compose up -d
```

| Service | Address |
|---|---|
| PostgreSQL | `localhost:5432` |
| Mailpit SMTP | `localhost:1025` |
| Mailpit web UI (read verification and reset emails) | http://localhost:8025 |

### 3. Build and run

```bash
./mvnw clean package -DskipTests
java -jar target/SecureVault-0.0.1-SNAPSHOT.jar
```

On Windows use `mvnw.cmd`. Run the JAR from the project root so `.env` is found. Flyway applies the schema on startup, and the API listens on `http://localhost:8080`.

The links in the emails point to `http://localhost:3000` (`app.frontend-url`), where a frontend would live. Without a frontend, copy the `token` value from the email in Mailpit and send it to the API.

## Running the tests

```bash
./mvnw test     # unit + integration tests
./mvnw verify   # full build including tests
```

Docker must be running: repository and integration tests start a disposable PostgreSQL 17 container through Testcontainers. Tests use the `test` profile (`src/test/resources/application-test.yaml`) and do not need a `.env` file. Email sending is mocked so tests can read verification and reset tokens directly.

| Layer | Location | What is covered |
|---|---|---|
| Unit | `service/*Test`, `security/*Test` | Service logic with Mockito: login outcomes, token validity, rotation and reuse detection, password reset, ownership and sharing rules, JWT generation and validation |
| Repository | `persistence/*RepositoryTest` | Custom queries and database constraints (unique email, unique token hashes, share mapping) against real PostgreSQL |
| Integration | `api/*IntegrationTest` | Requests through the real Spring Security filter chain via MockMvc: status codes, error bodies, JWT expiry and tampering, refresh rotation and reuse, logout, roles, document access, audit pagination, and that no secrets reach the audit log |

## API

Base URL: `http://localhost:8080/api/v1`. All request and response bodies are JSON. Protected endpoints require `Authorization: Bearer <accessToken>`.

| Method | Path | Access | Success |
|---|---|---|---|
| POST | `/auth/register` | public | 201 |
| POST | `/auth/verify-email` | public | 200 |
| POST | `/auth/resend-verification` | public | 200 |
| POST | `/auth/login` | public | 200 |
| POST | `/auth/refresh` | public | 200 |
| POST | `/auth/logout` | public | 204 |
| POST | `/auth/forgot-password` | public | 200 |
| POST | `/auth/reset-password` | public | 200 |
| POST | `/documents` | authenticated | 201 |
| GET | `/documents` | authenticated | 200 |
| GET | `/documents/{documentId}` | owner or shared user | 200 |
| PATCH | `/documents/{documentId}` | owner | 200 |
| DELETE | `/documents/{documentId}` | owner | 204 |
| POST | `/documents/{documentId}/shares` | owner | 204 |
| DELETE | `/documents/{documentId}/shares/{userId}` | owner | 204 |
| GET | `/admin/users` | ADMIN | 200 |
| PATCH | `/admin/users/{userId}/role` | ADMIN | 204 |
| PATCH | `/admin/users/{userId}/status` | ADMIN | 204 |
| GET | `/admin/audit-events` | ADMIN | 200 |

The examples below were captured from a running instance. Tokens are shortened, example emails are simplified and paged responses are trimmed to fewer entries.

### Authentication

**Register**

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alex@example.com","password":"StrongPassword123!","fullName":"Alex Smith"}'
```

`201 Created` with an empty body. A verification email is sent. Password: 8-100 characters; full name: 2-100 characters.

Invalid input returns `400`:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid request parameters",
  "path": "/api/v1/auth/register",
  "errors": {
    "password": "size must be between 8 and 100",
    "fullName": "must not be blank",
    "email": "must be a well-formed email address"
  },
  "timestamp": "2026-10-16T11:16:08.377038100Z"
}
```

A registered email returns `409` with `"message": "Email already exists"`.

**Verify email**

```bash
curl -X POST http://localhost:8080/api/v1/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{"token":"<token from the verification email>"}'
```

`200 OK` with an empty body. An unknown, expired or already used token returns `400`:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid or expired verification token",
  "path": "/api/v1/auth/verify-email",
  "errors": null,
  "timestamp": "2026-10-16T11:16:13.756673500Z"
}
```

**Resend verification email**

```bash
curl -X POST http://localhost:8080/api/v1/auth/resend-verification \
  -H "Content-Type: application/json" \
  -d '{"email":"alex@example.com"}'
```

Always `200 OK` with an empty body, whether or not the email is registered. Earlier unused verification links stop working.

**Log in**

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alex@example.com","password":"StrongPassword123!"}'
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "PsmOiLq4wpT7xKkWSOJy...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Wrong password or unknown email returns `401` with `"message": "Invalid credentials"` in both cases. An unverified account returns `403` (`"Email is not verified"`), a deactivated account `403` (`"User account is not active"`).

**Refresh tokens**

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

`200 OK` with a new token pair in the login format. The presented refresh token is revoked. An unknown, expired or revoked token returns `401`:

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid refresh token",
  "path": "/api/v1/auth/refresh",
  "errors": null,
  "timestamp": "2026-10-16T11:16:14.090712400Z"
}
```

**Log out**

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"token":"<refreshToken>"}'
```

`204 No Content`. The refresh token is revoked. Repeating the call also returns `204`.

**Forgot password**

```bash
curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"alex@example.com"}'
```

Always `200 OK` with an empty body. If the email is registered, a reset link valid for one hour is sent.

**Reset password**

```bash
curl -X POST http://localhost:8080/api/v1/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token":"<token from the reset email>","newPassword":"NewStrongPassword456!"}'
```

`200 OK` with an empty body. All refresh tokens of the user are revoked. An invalid, expired or used token returns `400`.

### Documents

**Create**

```bash
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Project plan","text":"Q3 roadmap draft"}'
```

`201 Created`:

```json
{
  "id": 8,
  "ownerId": 9,
  "name": "Project plan",
  "text": "Q3 roadmap draft",
  "createdAt": "2026-10-16T11:16:14.281285Z",
  "updatedAt": "2026-10-16T11:16:14.281285Z",
  "sharedWithUserIds": []
}
```

**List own documents**

```bash
curl "http://localhost:8080/api/v1/documents?page=0&size=20" \
  -H "Authorization: Bearer <accessToken>"
```

Returns only documents owned by the caller, newest first by default (`sort` can override it, e.g. `sort=name,asc`). Documents shared with the caller are not included; they are read by id. Page size is capped at 100.

```json
{
  "content": [
    {
      "id": 8,
      "ownerId": 9,
      "name": "Project plan",
      "text": "Q3 roadmap draft",
      "createdAt": "2026-10-16T11:16:14.281285Z",
      "updatedAt": "2026-10-16T11:16:14.281285Z",
      "sharedWithUserIds": []
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

**Get one**

```bash
curl http://localhost:8080/api/v1/documents/8 \
  -H "Authorization: Bearer <accessToken>"
```

`200 OK` with the document, for the owner and for users it is shared with. Anyone else receives `404`:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Document not found",
  "path": "/api/v1/documents/8",
  "errors": null,
  "timestamp": "2026-10-16T11:16:19.650702400Z"
}
```

**Update**

```bash
curl -X PATCH http://localhost:8080/api/v1/documents/8 \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Project plan v2"}'
```

`200 OK` with the updated document. Only fields present in the body are changed. A shared user receives `403`:

```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied",
  "path": "/api/v1/documents/8",
  "errors": null,
  "timestamp": "2026-10-16T11:16:19.732157300Z"
}
```

**Delete**

```bash
curl -X DELETE http://localhost:8080/api/v1/documents/8 \
  -H "Authorization: Bearer <accessToken>"
```

`204 No Content`. Shares of the document are removed with it.

**Share**

```bash
curl -X POST http://localhost:8080/api/v1/documents/8/shares \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"userId":10}'
```

`204 No Content`. Sharing with yourself returns `400`, an unknown user `404`, an existing share `409`:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Document is already shared with this user",
  "path": "/api/v1/documents/8/shares",
  "errors": null,
  "timestamp": "2026-10-16T11:16:19.697903100Z"
}
```

**Revoke a share**

```bash
curl -X DELETE http://localhost:8080/api/v1/documents/8/shares/10 \
  -H "Authorization: Bearer <accessToken>"
```

`204 No Content`. The user immediately loses read access. A share that does not exist returns `404`.

### Administration

All admin endpoints return `403` for USER and MODERATOR accounts and `401` without a token.

**List users**

```bash
curl "http://localhost:8080/api/v1/admin/users?page=0&size=20" \
  -H "Authorization: Bearer <adminAccessToken>"
```

```json
{
  "content": [
    {
      "id": 9,
      "fullName": "Alex Smith",
      "email": "alex@example.com",
      "role": "USER",
      "status": "ACTIVE",
      "emailVerified": true,
      "createdAt": "2026-10-16T11:16:03.282587Z",
      "updatedAt": "2026-10-16T11:16:24.538093Z"
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

**Change role**

```bash
curl -X PATCH http://localhost:8080/api/v1/admin/users/10/role \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"role":"MODERATOR"}'
```

`204 No Content`. Allowed values: `USER`, `MODERATOR`, `ADMIN`. An admin cannot change their own role (`400`).

**Activate or deactivate**

```bash
curl -X PATCH http://localhost:8080/api/v1/admin/users/10/status \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"status":"DEACTIVATED"}'
```

`204 No Content`. Allowed values: `ACTIVE`, `DEACTIVATED`. Deactivation revokes all refresh tokens of the user, and existing access tokens are rejected from the next request on. An admin cannot change their own status (`400`).

**Audit events**

```bash
curl "http://localhost:8080/api/v1/admin/audit-events?page=0&size=3" \
  -H "Authorization: Bearer <adminAccessToken>"
```

Always sorted newest first (`createdAt`, then `id`); page size is capped at 100.

```json
{
  "content": [
    {
      "id": 89,
      "eventType": "STATUS_CHANGED",
      "actorUserId": 3,
      "targetType": "USER",
      "targetId": 10,
      "details": "ACTIVE -> DEACTIVATED",
      "ipAddress": "0:0:0:0:0:0:0:1",
      "createdAt": "2026-10-16T11:16:24.738892Z"
    },
    {
      "id": 88,
      "eventType": "ROLE_CHANGED",
      "actorUserId": 3,
      "targetType": "USER",
      "targetId": 10,
      "details": "USER -> MODERATOR",
      "ipAddress": "0:0:0:0:0:0:0:1",
      "createdAt": "2026-10-16T11:16:24.705728Z"
    },
    {
      "id": 87,
      "eventType": "LOGIN_SUCCESS",
      "actorUserId": 3,
      "targetType": "USER",
      "targetId": 3,
      "details": "User logged in",
      "ipAddress": "0:0:0:0:0:0:0:1",
      "createdAt": "2026-10-16T11:16:24.625617Z"
    }
  ],
  "page": {
    "size": 3,
    "number": 0,
    "totalElements": 89,
    "totalPages": 30
  }
}
```

### Errors

Every error uses the same body: `status`, `error`, `message`, `path`, `timestamp`, and `errors` (field → message) for validation failures. Stack traces, SQL and class names are never returned; unexpected errors are logged and answered with a generic `500`.

| Status | When |
|---|---|
| 400 | Validation failure, malformed JSON, invalid path or sort parameter, invalid/expired/used verification or reset token, sharing with yourself, admin changing their own role or status |
| 401 | Missing, invalid or expired access token; wrong credentials; invalid, expired, revoked or reused refresh token |
| 403 | Authenticated but not allowed: wrong role, unverified or deactivated account at login, shared user modifying a document |
| 404 | Unknown resource, or a document the caller has no access to |
| 405 | HTTP method not supported by the endpoint |
| 409 | Duplicate email or duplicate share |
| 415 | Request body sent without `Content-Type: application/json` |

## Authentication and authorization design

**Passwords** are hashed with BCrypt and never stored, logged or audited in plain text.

**Email verification.** Registration creates the account with `emailVerified = false` and emails a token. Tokens are 32 bytes from `SecureRandom`, URL-safe Base64 encoded; only their SHA-256 hash is stored. A token expires after 24 hours and is single-use. Resending invalidates every unused token of the user. Unverified users cannot log in (`403`), so they never obtain an access token and cannot reach any protected endpoint.

**Access tokens** are HS256-signed JWTs valid for 15 minutes, with the claims `sub` (user id), `roles`, `iat`, `exp` and `jti`. The signing key comes from `JWT_SECRET` and is never committed; keys shorter than 256 bits are rejected at startup. `JwtAuthenticationFilter` validates the token on every request and loads the user from the database, so deactivation and role changes take effect immediately instead of when the token expires. Invalid or expired tokens result in `401` via a custom `AuthenticationEntryPoint`.

**Refresh tokens** are opaque random tokens (same generation as above) valid for 7 days. Only the SHA-256 hash is stored, so a database leak does not expose usable tokens. SHA-256 is used instead of BCrypt because the token is looked up by its hash, which requires a deterministic function; the token's 256 bits of entropy make brute force infeasible.

- **Rotation:** every `/auth/refresh` revokes the presented token and issues a new pair.
- **Reuse detection:** presenting an already revoked token means it was stolen or replayed. All active refresh tokens of the user are revoked, `REFRESH_TOKEN_REUSE_DETECTED` is audited and `401` is returned. The revocation is committed even though the request fails.
- **Revocation** happens on logout, password change or reset, and deactivation, and is always checked against the database.

**Password reset.** `forgot-password` always returns the same response. Reset tokens follow the same rules as verification tokens, expire after one hour and are single-use. A successful reset revokes all refresh tokens of the user.

**Roles** are `USER`, `MODERATOR` and `ADMIN`, enforced at two levels:

1. HTTP level in `SecurityConfig`: `/api/v1/auth/**` is public, `/api/v1/admin/**` requires `ROLE_ADMIN`, everything else requires authentication.
2. Method level with `@EnableMethodSecurity`: admin operations in `UserService` and `AuditEventService` carry `@PreAuthorize("hasRole('ADMIN')")`, and `DocumentService` requires an authenticated caller.

An admin cannot change their own role or status, which prevents locking the system out of its last administrator.

**Document access** is decided in `DocumentService` by ownership and explicit shares only, independent of the user's role; an ADMIN has no access to other users' documents. Owners can read, update, delete, share and revoke. Shared users can only read.

**Resource-disclosure policy.** A caller without any access to a document receives `404`, the same as for a document that does not exist, so ids cannot be probed. A shared user attempting to modify a document receives `403`, because they already know it exists.

**Account enumeration.** Login returns the same `401` message for unknown emails and wrong passwords. `resend-verification` and `forgot-password` return the same response for registered and unknown emails.

**Audit trail.** Events are recorded for registration, email verification (success and failure), verification resend, login success and failure, logout, refresh-token reuse, password reset requests, password changes, role and status changes, and document sharing and revocation. Failure events are written in a separate transaction (`REQUIRES_NEW`) so they survive the rollback of the failing request. Passwords, raw tokens and access tokens are never written to audit records.

## Database schema

The schema is managed by Flyway (`src/main/resources/db/migration`); Hibernate only validates it (`ddl-auto: validate`). Primary keys are `BIGINT` identity columns and timestamps are `TIMESTAMPTZ`.

| Table | Purpose | Key constraints |
|---|---|---|
| `users` | Accounts | `email` unique; role, status and verification flag; optimistic-locking `version` |
| `document` | Documents | `owner_id` → `users` |
| `document_access` | Shares (document ↔ user) | Primary key `(document_id, user_id)` prevents duplicate shares; both columns are foreign keys |
| `verification_token` | Email-verification tokens | `token_hash` unique; `user_id` → `users`; `expires_at`, `used_at` |
| `password_reset_token` | Password-reset tokens | `token_hash` unique; `user_id` → `users`; `expires_at`, `used_at` |
| `refresh_token` | Refresh tokens | `token_hash` unique; `user_id` → `users`; `expires_at`, `revoked` |
| `audit_events` | Audit trail | `actor_user_id` and `target_id` are plain columns, not foreign keys, so audit history is never lost or blocked by changes to the referenced rows |

**Deletion behavior.** Users cannot be deleted through the API; they are deactivated instead, which keeps their audit history intact. Foreign keys therefore use the default `NO ACTION`. When a document is deleted, its rows in `document_access` are removed first by the `@ManyToMany` mapping.

### Indexes

| Index | Reason |
|---|---|
| `idx_document_owner_id` on `document(owner_id, created_at DESC)` | Serves `GET /documents`: filter by owner and read newest first directly from the index |
| Unique index on each `token_hash` (from the `UNIQUE` constraints) | Every refresh, verification and reset request looks a token up by its hash |
| `idx_refresh_token_user_id` on `refresh_token(user_id)` | Revoking all tokens of a user on reuse detection, password change and deactivation |
| `idx_refresh_token_expires_at` on `refresh_token(expires_at)` | Expiration checks and cleanup of expired tokens |
| `idx_verification_token_user_id`, `idx_password_reset_token_user_id` | Invalidating a user's outstanding tokens on resend and on a new reset request |
| `idx_audit_events_created_at` on `audit_events(created_at DESC, id DESC)` | Matches the newest-first ordering of the admin audit endpoint exactly, including the `id` tie-break, so pages are read from the index without sorting |
| Primary key of `document_access (document_id, user_id)` | Checking whether a user has access to a given document |

## Design notes and limitations

- Emails are stored and compared exactly as entered; `Alex@example.com` and `alex@example.com` are different accounts.
- The login endpoint skips the BCrypt comparison for unknown emails, and `forgot-password` / `resend-verification` only contact the SMTP server for existing accounts. Response bodies are identical, but response times can differ. Production deployments should add a dummy hash comparison and send emails asynchronously.
- `GET /documents` lists owned documents only. Documents shared with the caller are accessed by id.
- The logout body uses `token`, the refresh body `refreshToken`; both carry the refresh token.

## Project structure

```
src/main/java/com/example/SecureVault
├── api/          REST controllers and GlobalExceptionHandler
├── config/       SecurityConfig, SystemDataInitializer
├── dto/          Request and response records
├── entity/       JPA entities
├── enums/        Roles, Status, audit event and target types
├── exception/    Domain exceptions
├── persistence/  Spring Data repositories
├── security/     JWT service and filter, token hashing, 401/403 handlers
└── service/      Business logic: auth, users, documents, audit, email
```
