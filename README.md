# TicketHub

![CI](https://github.com/<you>/tickethub/actions/workflows/ci.yml/badge.svg)

Ticket booking platform: **Java 21 · Spring Boot 3 · Spring Data JPA · MySQL 8 · React**.

JWT authentication, live seat allocation, and secure payment integration, built so that
a seat can never be sold twice and the booking path stays fast under contention.

---

## Quick start

```bash
# 1. Database + backend
docker compose up --build            # API on http://localhost:8080

# 2. Frontend (separate terminal)
cd frontend && npm install && npm run dev      # http://localhost:5173
```

Swagger UI: http://localhost:8080/swagger-ui.html

Demo data (two logins, a hall, two shows):

```bash
SPRING_PROFILES_ACTIVE=demo mvn spring-boot:run
# admin@tickethub.dev / Admin123!
# user@tickethub.dev  / User1234!
```

## Tests

```bash
mvn clean verify        # unit + integration; integration tests start a real MySQL 8 container
```

Docker must be running: integration tests use Testcontainers against **MySQL 8**, never H2.
Row locking, `SKIP LOCKED` and generated columns behave differently in H2, which would make
the concurrency tests meaningless.

Key suites:

| Class | What it proves |
|---|---|
| `SeatHoldConcurrencyTest` | 200 threads race for one seat, exactly one wins; overlapping seat sets stay consistent; a partially unavailable request holds nothing |
| `BookingLifecycleTest` | hold → pay → confirm, webhook idempotency, hold expiry, late-payment refund, cancellation, ownership |
| `AuthApiTest` | registration, login, refresh-token rotation, role enforcement |
| `CatalogueServiceTest` | seat generation, show-overlap guard, search filters |
| `SeatMapApiTest` | public seat map, 409 with `unavailableSeatIds` |
| `JwtServiceTest`, `WebhookSignatureVerifierTest`, `BookingReferenceGeneratorTest` | unit-level, no Spring context |

## How duplicate reservations are prevented

Three layers, primary first:

1. **Atomic conditional UPDATE** (`ShowSeatRepository.holdSeats`): one statement sets the
   requested seats to `HELD` only where `status = 'AVAILABLE'`. InnoDB takes the row locks.
   If the affected row count is not exactly the number of seats requested, the whole
   transaction rolls back and nothing is held.
2. **Unique index on a generated column** (`booking_items.active_show_seat_id`): MySQL has no
   partial unique index, so the column is `NULL` for inactive rows and a unique key tolerates
   many `NULL`s. Even a coding bug cannot put one seat into two active bookings.
3. **`@Version` optimistic locking** on `Booking` and `ShowSeat` catches lost updates in the
   cancel-versus-confirm paths.

Deadlock losers are retried up to three times in a **fresh** transaction. Seat ids are always
locked in ascending order. No gateway call is ever made while a database lock is held.

## Layout

```
src/main/java/com/tickethub
├── config       SecurityConfig, properties, rate limiting, OpenAPI, demo seeder
├── common       ProblemDetail error handling, error codes, page wrapper
├── auth         JWT service/filter, login, refresh-token rotation
├── user
├── catalogue    venues, halls, seats, events, shows
├── seating      ShowSeat, seat map projection, SSE publisher
├── booking      hold/confirm/expire/cancel transactions, expiry scheduler
├── payment      gateway seam, signed webhook, idempotency, refunds
└── report
src/main/resources/db/migration   V1 schema, V2 performance indexes
src/test/java/com/tickethub       unit, integration, concurrency suites
frontend/                          React 18 + Vite seat-map client
```

## Performance work

`V2__performance_indexes.sql` is deliberately separate so you can record a baseline before
tuning. The tuning log and the k6 workload are in document 06. Measure, then state the
measured number: do not quote a figure the runs have not produced.

## Deployment

See [DEPLOY.md](DEPLOY.md) for GitHub, Railway (MySQL + backend) and Vercel (frontend). Copy
`.env.example` to `.env` for local configuration.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | local MySQL | datasource |
| `JWT_SECRET` | dev value | HS256 signing key, at least 32 bytes |
| `PAYMENT_WEBHOOK_SECRET` | `test-webhook-secret` | HMAC secret for webhook verification |
| `CORS_ORIGINS` | `http://localhost:5173` | allowed frontend origins |
| `tickethub.booking.hold-minutes` | 5 | seat hold duration |
| `tickethub.booking.max-seats-per-booking` | 6 | per-booking seat cap |

## Known first-run checks

Three places to watch on the first `mvn clean verify` against your MySQL:

1. `ddl-auto: validate` versus the MySQL column types (switch to `none` if it is noisy).
2. The `:param IS NULL` pattern in `EventRepository.search`.
3. `LIMIT :batchSize` in the native sweeper query in `BookingRepository`.
