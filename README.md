# TechDecide API

REST API backend for [TechDecide](https://github.com/safouaneOulhabib/techdecide-frontend) — a Technical Decision Hub for Agile Teams. Teams log, govern, and export Architecture Decision Records (ADRs) through a formal lifecycle with JWT-authenticated access.

## Tech Stack

| Layer      | Technology                        |
|------------|-----------------------------------|
| Runtime    | Java 21                           |
| Framework  | Spring Boot 3.5                   |
| Security   | Spring Security + JWT (jjwt 0.12) |
| ORM        | Spring Data JPA + Hibernate       |
| Database   | PostgreSQL 16                     |
| Build      | Maven (Maven Wrapper included)    |

## Local Development Setup

### Prerequisites

- Java 21
- Docker and Docker Compose
- Maven (or use the included `./mvnw`)

### 1 — Start PostgreSQL

```bash
docker compose up -d
```

This starts a PostgreSQL 16 container on port **5434** with a persistent named volume (`techdecide_data`). Default credentials are `postgres / postgres` against database `techdecide`.

### 2 — Run the API

```bash
./mvnw spring-boot:run
```

The server starts on **http://localhost:8080**.

### 3 — Run tests

```bash
./mvnw test
```

Tests use an in-memory H2 database — no running Postgres required.

## Environment Variables

All defaults are suitable for local development. Override these in production:

| Variable                                        | Default                                     | Description                         |
|-------------------------------------------------|---------------------------------------------|-------------------------------------|
| `SPRING_DATASOURCE_URL`                         | `jdbc:postgresql://localhost:5434/techdecide` | JDBC connection URL               |
| `SPRING_DATASOURCE_USERNAME`                    | `postgres`                                  | Database user                       |
| `SPRING_DATASOURCE_PASSWORD`                    | `postgres`                                  | Database password                   |
| `APP_JWT_SECRET`                                | `techdecide-secret-key-change-in-production` | JWT signing secret (change this!)  |
| `APP_JWT_EXPIRATION`                            | `86400000`                                  | Token TTL in milliseconds (24 h)    |

`docker-compose.yml` also honours `DB_NAME`, `DB_USER`, and `DB_PASSWORD` environment variables if you need to override the Postgres container defaults.

## API Endpoints Summary

All endpoints except `/api/auth/**` require a valid `Authorization: Bearer <token>` header.

### Authentication — `/api/auth`

| Method | Path                  | Description              |
|--------|-----------------------|--------------------------|
| POST   | `/api/auth/register`  | Register a new user      |
| POST   | `/api/auth/login`     | Log in, receive JWT      |

### Decisions — `/api/decisions`

| Method | Path                           | Description                                           |
|--------|--------------------------------|-------------------------------------------------------|
| GET    | `/api/decisions`               | List all decisions                                    |
| GET    | `/api/decisions/{id}`          | Get a single decision                                 |
| GET    | `/api/decisions/team/{teamId}` | List decisions for a team                             |
| GET    | `/api/decisions/search`        | Filter by status, team, tag, author (query params)    |
| POST   | `/api/decisions`               | Create a decision (status: DRAFT)                     |
| PUT    | `/api/decisions/{id}`          | Update a decision (DRAFT or PROPOSED only)            |
| PATCH  | `/api/decisions/{id}/status`   | Advance lifecycle; body carries `status` (+ optional `supersededById`) |
| DELETE | `/api/decisions/{id}`          | Delete a decision (DRAFT, PROPOSED, or REJECTED only) |

**Decision lifecycle:** `DRAFT → PROPOSED → APPROVED | REJECTED → SUPERSEDED`

### Comments & Votes — `/api/decisions/{decisionId}/comments`

| Method | Path                                      | Description                             |
|--------|-------------------------------------------|-----------------------------------------|
| GET    | `/api/decisions/{decisionId}/comments`    | List comments for a decision            |
| POST   | `/api/decisions/{decisionId}/comments`    | Add a comment (with Approve/Reject/Abstain vote) |
| DELETE | `/api/comments/{id}`                      | Delete own comment                      |

### Reports — `/api/reports`

| Method | Path               | Description                                             |
|--------|--------------------|---------------------------------------------------------|
| GET    | `/api/reports`     | List all reports (summary with status counts)           |
| GET    | `/api/reports/{id}`| Get full report with frozen decision snapshots          |
| POST   | `/api/reports`     | Create report from selected decisions                   |
| PUT    | `/api/reports/{id}`| Update report title and introduction (items immutable)  |
| DELETE | `/api/reports/{id}`| Delete a report (owner only)                            |

### Tags — `/api/tags`

| Method | Path           | Description      |
|--------|----------------|------------------|
| GET    | `/api/tags`    | List all tags    |
| GET    | `/api/tags/{id}` | Get tag by id  |
| POST   | `/api/tags`    | Create a tag     |
| DELETE | `/api/tags/{id}` | Delete a tag   |

### Organizations — `/api/organizations`

| Method | Path                       | Description              |
|--------|----------------------------|--------------------------|
| GET    | `/api/organizations`       | List organizations       |
| GET    | `/api/organizations/{id}`  | Get organization by id   |
| POST   | `/api/organizations`       | Create organization      |
| PUT    | `/api/organizations/{id}`  | Update organization      |
| DELETE | `/api/organizations/{id}`  | Delete organization      |

### Teams — `/api/teams`

| Method | Path                                        | Description                     |
|--------|---------------------------------------------|---------------------------------|
| GET    | `/api/teams`                                | List all teams                  |
| GET    | `/api/teams/{id}`                           | Get team by id                  |
| GET    | `/api/teams/organization/{organizationId}`  | List teams for an organization  |
| POST   | `/api/teams`                                | Create team                     |
| PUT    | `/api/teams/{id}`                           | Update team                     |
| DELETE | `/api/teams/{id}`                           | Delete team                     |

## Project Structure

```
src/main/java/com/techdecide/api/
  controller/    HTTP layer — validates input, delegates to service
  service/       Business logic and transaction boundaries
  repository/    Spring Data JPA interfaces
  entity/        JPA entities (PostgreSQL-mapped)
  dto/           Request and response DTOs
  exception/     Domain exceptions mapped to HTTP status codes
  security/      JWT filter, UserPrincipal, Spring Security config
```

## License

MIT — see [LICENSE](LICENSE).
