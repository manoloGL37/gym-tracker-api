# Gym Tracker API

Backend REST para **Gym Tracker**, una aplicación orientada al registro y seguimiento de entrenamientos.

El backend proporciona una API independiente para gestionar usuarios, ejercicios, rutinas y entrenamientos, con PostgreSQL como sistema de persistencia.

El proyecto está construido como un **modular monolith**: una única aplicación desplegable con los diferentes dominios organizados en módulos independientes. El objetivo es mantener una arquitectura sencilla de desplegar y mantener, pero con límites suficientemente claros para permitir una futura evolución.

> 🚧 **Project status:** Work in progress

---

## Tech Stack

### Backend

* Java 21
* Spring Boot 4.1
* Spring Web MVC
* Spring Data JPA
* Hibernate
* Spring Security
* Bean Validation
* Lombok

### Database

* PostgreSQL 17
* Flyway
* Docker
* Docker Compose

### Build & Testing

* Maven
* Maven Wrapper
* JUnit 5

---

## Architecture

Gym Tracker utiliza una arquitectura de **modular monolith**.

La aplicación se ejecuta como un único backend, pero cada dominio mantiene sus propias responsabilidades y organización interna.

La estructura sigue principalmente una organización **por módulo**:

```text
dev.manuel.gymtracker_api/

├── user/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── model/
│   └── dto/
│
├── exercise/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── model/
│   ├── dto/
│   └── importer/
│
└── ...
```

Los módulos previstos incluyen:

* Users
* Exercises
* Routines
* Workouts
* Statistics

La aplicación seguirá siendo un único despliegue mientras el proyecto lo permita. Si en el futuro algún dominio necesitara ser extraído, la separación interna facilitaría esa evolución.

---

## Project Structure

La estructura actual del proyecto es similar a:

```text
gymtracker-api/

├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── dev/
│   │   │       └── manuel/
│   │   │           └── gymtracker_api/
│   │   │               ├── user/
│   │   │               ├── exercise/
│   │   │               └── GymtrackerApiApplication.java
│   │   │
│   │   └── resources/
│   │       ├── data/
│   │       │   └── exercises.json
│   │       ├── db/
│   │       │   └── migration/
│   │       │       ├── V1__create_users.sql
│   │       │       ├── V2__create_exercise_catalog.sql
│   │       │       ├── V3__allow_nullable_exercise_translation_name.sql
│   │       │       └── V4__add_exercise_source_unique_index.sql
│   │       └── application.yml
│   │
│   └── test/
│
├── docker-compose.yml
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

The structure will evolve as new modules and functionality are implemented.

---

# Requirements

To run the project locally, you need:

* Java 21
* Docker Desktop
* Git

The project includes the **Maven Wrapper**, so installing Maven globally is not required.

PostgreSQL does **not** need to be installed directly on the host machine. It runs inside Docker.

---

# Getting Started

## 1. Clone the repository

Clone the repository and enter the project directory:

```bash
git clone <repository-url>
cd gymtracker-api
```

---

## 2. Start PostgreSQL

Start the PostgreSQL container using Docker Compose:

```bash
docker compose up -d
```

Check that the container is running:

```bash
docker compose ps
```

To view the PostgreSQL logs:

```bash
docker compose logs postgres
```

The development database uses:

```text
Database: gymtracker
Username: gymtracker
Password: gymtracker
Host: localhost
Port: 5433
```

The PostgreSQL container itself listens on port `5432`, but the host exposes it through port `5433`.

This allows the project to coexist with a PostgreSQL installation already using port `5432`.

---

## 3. Start the API

The project includes Maven Wrapper.

From PowerShell on Windows:

```powershell
./mvnw spring-boot:run
```

The API will start on:

```text
http://localhost:8080
```

When the application starts, Flyway automatically executes any pending database migrations.

The normal application startup **does not import the exercise dataset**.

---

# Exercise Dataset

Gym Tracker includes an external exercise dataset used as the initial source for the application's global exercise catalogue.

The dataset is stored locally in:

```text
src/main/resources/data/exercises.json
```

The dataset contains information such as:

* Exercise names
* Categories
* Equipment
* Target muscles
* Muscle groups
* Secondary muscles
* Instructions
* Instructions in multiple languages

The external dataset is **not queried at runtime**.

Instead, the data is imported into the application's own PostgreSQL schema:

```text
exercises
exercise_translations
```

The application's domain model therefore remains independent from the structure and identifiers of the external dataset.

External identifiers are stored as `source_id` rather than being used as the primary key of an `Exercise`.

---

## Importing the Exercise Dataset

The exercise dataset must be imported explicitly.

This is intentional: the import process should not execute every time the API starts.

To import or update the dataset, run:

```powershell
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--gymtracker.import.exercises=true"
```

The importer:

1. Reads `src/main/resources/data/exercises.json`.
2. Checks which dataset exercises already exist.
3. Creates new exercises when necessary.
4. Updates existing dataset exercises.
5. Imports their translations.
6. Prevents duplicate exercises using the `(source, source_id)` database constraint.

A successful import should log:

```text
Exercise dataset imported successfully: 1324 exercises
```

The importer is designed to be safely executed again without creating duplicate exercises.

### Important

The normal application startup:

```powershell
./mvnw spring-boot:run
```

does **not** execute the importer.

The dataset only needs to be imported when the catalogue needs to be initially populated or updated.

---

# Database

PostgreSQL is managed locally using Docker Compose.

The current Docker configuration is:

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: gymtracker
      POSTGRES_USER: gymtracker
      POSTGRES_PASSWORD: gymtracker
    ports:
      - "5433:5432"
    volumes:
      - gymtracker_data:/var/lib/postgresql/data

volumes:
  gymtracker_data:
```

The Docker volume:

```text
gymtracker_data
```

persists the PostgreSQL data independently of the container lifecycle.

---

## Stop PostgreSQL

To stop the PostgreSQL container:

```bash
docker compose down
```

This removes the container but keeps the database volume.

---

## Remove PostgreSQL data

To remove the container and the persisted database volume:

```bash
docker compose down -v
```

> ⚠️ This permanently removes the local PostgreSQL data. Use this only when a completely clean database is required.

After removing the volume, starting the application again will recreate the database and Flyway will execute all migrations from the beginning.

---

# Database Migrations

Database schema changes are managed with **Flyway**.

Migrations are stored in:

```text
src/main/resources/db/migration/
```

Current migrations:

```text
V1__create_users.sql
V2__create_exercise_catalog.sql
V3__allow_nullable_exercise_translation_name.sql
V4__add_exercise_source_unique_index.sql
```

Flyway executes pending migrations automatically when the application starts.

The migration history is stored in the database, so an already-applied migration should not be modified.

New schema changes should be introduced through a new migration.

For example:

```text
V5__create_routines.sql
```

---

# Configuration

The main Spring Boot configuration is located at:

```text
src/main/resources/application.yml
```

The current local configuration connects to:

```text
jdbc:postgresql://localhost:5433/gymtracker
```

Local development credentials are currently defined in the application configuration.

These credentials are intended only for local development.

Production credentials and other sensitive configuration should be provided through environment variables or an appropriate secrets-management system rather than committed to the repository.

---

# API

The backend exposes REST endpoints under:

```text
/api
```

Current functionality includes:

### Users

```text
POST /api/users
GET  /api/users/{id}
```

User registration stores passwords using BCrypt rather than storing plaintext passwords.

### Exercises

```text
GET /api/exercises
```

The exercise catalogue supports pagination.

Current development usage:

```text
GET /api/exercises?userId={uuid}&page=0&size=20
```

The `userId` parameter is temporary and is currently used while authentication is being implemented.

The final API will obtain the authenticated user from the security context/JWT instead.

The exercise endpoint returns:

* Exercise information
* Translations
* Aliases
* Pagination metadata

Internal database fields such as `owner_id`, `source`, `source_id` and deletion timestamps are not exposed directly by the API.

---

# Authentication & Security

Spring Security is already part of the project, but authentication is still under development.

Current security work includes:

* Password hashing with BCrypt
* Stateless security configuration
* Endpoint authorization
* Input validation
* Protection of user-specific resources

JWT authentication and authorization based on the authenticated user are planned next.

The application will not rely on UUIDs as a security mechanism. Access to user-owned resources will always be validated server-side.

---

# Exercise Catalogue Design

Exercises can represent both global exercises and exercises created by individual users.

The same `Exercise` model is used for both cases.

Conceptually:

```text
Global exercise
owner_id = NULL

User-created exercise
owner_id = authenticated user's UUID
```

Global exercises can be shared by all users, while private exercises belong to their owner.

Exercises also support soft deletion through:

```text
deleted_at
```

This allows historical references to an exercise to remain valid without making the exercise available to normal catalogue queries.

Localized information is stored separately from the exercise itself:

```text
Exercise
    │
    ├── ExerciseTranslation
    │
    └── ExerciseAlias
```

This keeps the exercise identity independent from its localized names and instructions.

---

# Routines

A routine represents a planned workout structure.

The planned number of sets belongs to the relationship between a routine and an exercise rather than to the exercise itself.

The planned structure is conceptually:

```text
Routine
   │
   └── RoutineExercise
           │
           └── Exercise
```

The position of an exercise within a routine is stored explicitly so the order can be preserved.

Routines are planned structures and are separate from actual completed workouts.

---

# Workouts

A workout session represents an actual training session performed by the user.

The planned routine and the actual workout are deliberately separated.

Conceptually:

```text
WorkoutSession
      │
      └── WorkoutExercise
              │
              └── WorkoutSet
```

This allows users to:

* Complete only part of a routine
* Perform exercises in a different order
* Record workouts retroactively
* Perform workouts without using a routine
* Preserve historical workout data even if the original routine changes

The routine is therefore contextual information, while the workout session represents what actually happened.

---

# Training Plans

Training plans will provide a preferred sequence of routines without forcing the user to follow a rigid calendar.

For example:

```text
Pull A
  ↓
Push A
  ↓
Legs
  ↓
Pull B
  ↓
Push B
```

The plan is intended to recommend the next workout rather than restrict which workouts the user can perform.

Skipping a day does not automatically create a missed workout.

---

# Internationalization

The exercise catalogue is designed to support multiple languages.

Localized information is stored in `exercise_translations`.

The external dataset currently provides instructions in several languages, including:

* English
* Spanish
* French
* Italian
* Turkish
* Russian
* Chinese
* Hindi
* Polish
* Korean

The backend keeps the exercise identity independent from its translations.

Additional localization support can be added as the application evolves.

---

# Testing

Testing is being introduced progressively alongside the implementation.

The project currently uses:

* JUnit 5
* Spring Boot Test

For example, the exercise dataset reader has tests verifying that the JSON dataset can be loaded and deserialized correctly.

To run all tests:

```powershell
./mvnw test
```

To run a specific test:

```powershell
./mvnw -Dtest=ExerciseDatasetReaderTest test
```

The testing strategy will progressively include:

* Unit tests
* Repository tests
* Service/application tests
* Controller/API tests
* Integration tests
* PostgreSQL integration tests
* Testcontainers

---

# Development Workflow

A typical local development workflow is:

```text
1. Start Docker
       ↓
2. Start PostgreSQL
       ↓
3. Start Spring Boot
       ↓
4. Flyway executes pending migrations
       ↓
5. API starts
```

For a fresh database, the exercise catalogue can then be imported explicitly:

```text
API stopped
     ↓
Run importer
     ↓
1324 exercises imported
     ↓
Start API normally
```

### Common commands

Start PostgreSQL:

```powershell
docker compose up -d
```

Check PostgreSQL:

```powershell
docker compose ps
```

Start the API:

```powershell
./mvnw spring-boot:run
```

Import the exercise dataset:

```powershell
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--gymtracker.import.exercises=true"
```

Run tests:

```powershell
./mvnw test
```

Stop PostgreSQL:

```powershell
docker compose down
```

Reset PostgreSQL completely:

```powershell
docker compose down -v
```

---

# Frontend Integration

The existing Gym Tracker frontend currently stores application data locally using `localStorage`.

The long-term goal is to migrate persistence to this backend.

The planned flow is:

```text
Existing local data
        ↓
User creates an account
        ↓
Local data is synchronized
        ↓
PostgreSQL becomes the source of truth
        ↓
Frontend communicates with REST API
```

The migration process will be designed to preserve existing user data when moving from offline storage to the backend.

---

# Planned Features

## Authentication

* [x] User registration
* [x] Password hashing
* [x] Basic endpoint security configuration
* [ ] Login
* [ ] JWT authentication
* [ ] Authorization based on authenticated user
* [ ] Protected user resources

## Exercises

* [x] Exercise domain model
* [x] Global exercise catalogue
* [x] User-owned exercises model
* [x] Exercise translations
* [x] Exercise aliases model
* [x] Soft deletion model
* [x] External dataset importer
* [x] Pagination
* [ ] Exercise search
* [ ] Exercise filtering
* [ ] User exercise creation/editing/deletion

## Routines

* [ ] Create routines
* [ ] Edit routines
* [ ] Add/remove exercises
* [ ] Configure planned sets
* [ ] Preserve exercise order
* [ ] User-specific routines

## Training Plans

* [ ] Create training plans
* [ ] Assign routines to plans
* [ ] Configure routine order
* [ ] Recommend the next routine

## Workouts

* [ ] Start workout session
* [ ] Record performed exercises
* [ ] Record sets
* [ ] Record repetitions and weight
* [ ] Add workout notes
* [ ] Register workouts retroactively
* [ ] Support manual workouts
* [ ] Preserve historical workout data

## Statistics

* [ ] Training history
* [ ] Exercise progression
* [ ] Weight progression
* [ ] Repetition progression
* [ ] Training volume
* [ ] Future workout statistics

## Frontend

* [ ] Angular API integration
* [ ] Authentication integration
* [ ] Local data migration
* [ ] Replace `localStorage` persistence
* [ ] Synchronization between frontend and backend

## API Documentation

* [ ] OpenAPI
* [ ] Swagger UI
* [ ] Document request/response models
* [ ] Document authentication requirements
* [ ] Document validation errors

## Infrastructure

* [ ] Dockerize backend
* [ ] Production database configuration
* [ ] CI/CD
* [ ] Production deployment
* [ ] Monitoring and logging

---

# Project Status

🚧 **Work in progress**

### Completed

* [x] Spring Boot project
* [x] Java 21
* [x] Maven Wrapper
* [x] PostgreSQL 17
* [x] Docker Compose
* [x] Persistent PostgreSQL volume
* [x] Spring Data JPA
* [x] Hibernate
* [x] Flyway
* [x] Spring Security configuration
* [x] User domain
* [x] User registration
* [x] Password hashing with BCrypt
* [x] User retrieval
* [x] Global exception handling
* [x] Bean Validation
* [x] Exercise domain
* [x] Exercise translations
* [x] Exercise aliases
* [x] Exercise catalogue pagination
* [x] External exercise dataset
* [x] Dataset reader
* [x] Dataset importer
* [x] Idempotent dataset import
* [x] Exercise catalogue database constraints
* [x] Dataset reader tests

### In progress

* [ ] Authentication
* [ ] JWT
* [ ] Exercise search and filtering
* [ ] Routine module
* [ ] Workout module

### Planned

* [ ] Training plans
* [ ] Statistics
* [ ] Angular integration
* [ ] Local data migration
* [ ] API documentation
* [ ] Integration testing
* [ ] Dockerized backend
* [ ] CI/CD
* [ ] Production deployment

---

# License & Third-Party Resources

Gym Tracker is a personal portfolio project.

The project may use third-party libraries, datasets and other resources. Each external resource remains subject to its own license and attribution requirements.

The exercise dataset used by the project is maintained separately from the Gym Tracker domain model. Any redistribution or use of third-party resources must comply with their respective licensing and attribution requirements.

The project does not claim ownership of third-party datasets, media or other external resources.
