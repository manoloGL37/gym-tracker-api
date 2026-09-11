# Gym Tracker API

Gym Tracker API is a REST backend for recording training activity. It provides authenticated access to users, an exercise catalog, routines, workout sessions, and training statistics.

The project is designed as a modular monolith: one deployable Spring Boot application with clear domain boundaries and no distributed-system overhead.

## Features

- Short-lived JWT access tokens plus persistent, rotating refresh sessions
- User registration and current-user profile retrieval
- Global exercise catalog plus private custom exercises
- Exercise translations, aliases, filters, and pagination
- Routine creation and maintenance
- Workout tracking, including exercises and performed sets
- Training summaries, comparisons, evolution, and per-exercise statistics
- PostgreSQL persistence with Flyway migrations
- Explicit, idempotent exercise dataset import in transactional batches
- Docker Compose support for local development
- OpenAPI specification and Swagger UI

## Tech Stack

| Area | Technology |
| --- | --- |
| Language and framework | Java 21, Spring Boot 4.1, Spring Web MVC |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL |
| Security | Spring Security, JWT, BCrypt |
| Database migrations | Flyway |
| API documentation | OpenAPI 3, Swagger UI |
| Build and testing | Maven Wrapper, JUnit 5, Testcontainers |
| Local containers | Docker, Docker Compose |

## Architecture

The backend is a modular monolith. Each business domain keeps its controller, service, repository, DTO, model, and exception code together where applicable:

```text
dev.manuel.gymtracker_api/
├── auth/
├── user/
├── exercise/
├── routine/
├── workout/
├── statistics/
├── common/
└── config/
```

This keeps the API straightforward to run and deploy while preserving explicit boundaries between domains.

## API Documentation

Start the application locally, then open:

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Swagger groups endpoints by Authentication, Users, Exercises, Routines, Workouts, and Statistics. Request and response DTOs are generated from the API contracts, so the documented schemas stay aligned with the application.

### Using JWT in Swagger UI

1. Call `POST /api/auth/login` with valid credentials.
2. Copy the `accessToken` from the response.
3. Select **Authorize** in Swagger UI and paste the token in the `bearerAuth` field. The token value only is enough; Swagger UI applies the `Bearer` prefix.
4. Call protected endpoints normally. The login response also sets an HttpOnly refresh cookie; Swagger/browser clients can use it with `POST /api/auth/refresh`.

## Running Locally

### Requirements

- Java 21
- Docker Desktop
- Git

Maven does not need to be installed globally; the repository includes Maven Wrapper.

### 1. Start PostgreSQL

Start only the database for local development:

```bash
docker compose up -d postgres
```

The database is exposed at `localhost:5433` and uses the local development database named `gymtracker`.

### 2. Configure the JWT secret

`JWT_SECRET` is required. For PowerShell:

```powershell
$env:JWT_SECRET = "replace-with-a-long-random-local-secret"
```

### 3. Start the API

```powershell
./mvnw spring-boot:run
```

Flyway applies pending migrations at startup. Normal startup does not import the exercise dataset.

## Environment Variables

| Variable | Purpose | Local default |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5433/gymtracker` |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL user | `gymtracker` |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password | `gymtracker` |
| `JWT_SECRET` | Secret used to sign JWTs | Required |
| `JWT_EXPIRATION_MS` | Access-token lifetime in milliseconds | `3600000` (1 hour) |
| `REFRESH_TOKEN_EXPIRATION_MS` | Absolute refresh-session lifetime in milliseconds | `2592000000` (30 days) |
| `REFRESH_COOKIE_NAME` | Refresh cookie name | `refreshToken` |
| `REFRESH_COOKIE_SECURE` | Emit the cookie only over HTTPS | `false` |
| `REFRESH_COOKIE_SAME_SITE` | Cookie SameSite policy | `Lax` |
| `PORT` | HTTP server port | `8080` |
| `APP_CORS_ALLOWED_ORIGINS` | Comma-separated browser origins allowed to call the API (no paths and no trailing slash), for example `http://localhost:4200,https://gym-tracker-eight-dun.vercel.app` | `http://localhost:4200,https://gym-tracker-eight-dun.vercel.app` |

Never commit production credentials or JWT secrets. Production configuration is supplied through the deployment environment.

For Render production set `JWT_SECRET`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `APP_CORS_ALLOWED_ORIGINS` to the exact Angular origins, `REFRESH_COOKIE_SECURE=true`, and `REFRESH_COOKIE_SAME_SITE=None`. Keep `REFRESH_COOKIE_NAME=refreshToken`, `JWT_EXPIRATION_MS=3600000`, and `REFRESH_TOKEN_EXPIRATION_MS=2592000000` unless deliberately changing the documented frontend contract. Never use `*` in `APP_CORS_ALLOWED_ORIGINS`: credentialed CORS requires exact origins.

## Database Migrations

Flyway migrations live in `src/main/resources/db/migration`. Pending migrations run automatically when the API starts, and Flyway records their history in the database.

Do not modify a migration that has already been applied. Introduce schema changes in a new, versioned migration instead.

## Exercise Dataset Import

The bundled exercise dataset is stored in `src/main/resources/data/exercises.json`. It is deliberately not imported during normal application startup.

Import or update it explicitly with:

```powershell
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--gymtracker.import.exercises=true"
```

The importer processes the dataset in transactional batches. It is safe to run again: exercises are matched by `source` and `source_id`, so existing dataset entries are updated instead of duplicated.

## Docker

To run the API and PostgreSQL together through Docker Compose, provide a JWT secret and build the services:

```powershell
$env:JWT_SECRET = "replace-with-a-long-random-local-secret"
docker compose up --build
```

Useful commands:

```bash
docker compose ps
docker compose down
```

`docker compose down` preserves the PostgreSQL volume. Add `-v` only when intentionally removing local database data.

## Testing

Run the full test suite with:

```powershell
./mvnw clean test
```

Integration tests use Testcontainers and require Docker to be available.

## Deployment

The production deployment is intentionally simple:

```text
GitHub → Render Web Service → Neon PostgreSQL
```

Render runs the Spring Boot application and connects to Neon using deployment environment variables. No production credentials are stored in this repository.

The current production API URL is <https://gym-tracker-api-s70k.onrender.com>. The deployed frontend origin is `https://gym-tracker-eight-dun.vercel.app`; it is included in the default CORS policy. Configure additional/replacement origins through `APP_CORS_ALLOWED_ORIGINS`; do not use `*`. CORS allows credentials so Angular can send the HttpOnly refresh cookie.
