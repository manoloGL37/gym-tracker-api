# Gym Tracker API

Backend REST para **Gym Tracker**, una aplicación para registrar y consultar entrenamientos, rutinas, ejercicios y progreso.

El proyecto está desarrollado como un **modular monolith**, con una arquitectura orientada a mantener una separación clara entre los distintos dominios de la aplicación y facilitar una futura evolución.

Actualmente, el frontend de Gym Tracker funciona de forma offline mediante `localStorage`. Este backend permitirá incorporar persistencia en PostgreSQL, autenticación de usuarios y sincronización de los datos.

## Tech Stack

### Backend

* Java 21
* Spring Boot 4.1
* Spring Web MVC
* Spring Data JPA
* Hibernate
* Spring Security
* JWT
* Bean Validation

### Database

* PostgreSQL 17
* Flyway
* Docker
* Docker Compose

### Build & Development

* Maven
* Lombok

## Architecture

Gym Tracker está planteado inicialmente como un **modular monolith**.

La aplicación se ejecutará como un único backend, pero los diferentes dominios estarán separados internamente para mantener unos límites claros entre funcionalidades.

Principales módulos previstos:

* Users
* Exercises
* Routines
* Workouts
* Statistics

Esta arquitectura permite mantener la simplicidad de un único despliegue sin renunciar a una organización preparada para crecer.

## Project Structure

La estructura definitiva evolucionará a medida que avance el proyecto.

Actualmente:

```text
gymtracker-api/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── dev/
│   │   │       └── manuel/
│   │   │           └── gymtracker/
│   │   └── resources/
│   │       └── application.yml
│   │
│   └── test/
│
├── docker-compose.yml
├── pom.xml
└── README.md
```

## Requirements

Para ejecutar el proyecto localmente necesitas:

* Java 21
* Docker Desktop
* Git

No es necesario instalar PostgreSQL directamente en el sistema, ya que la base de datos se ejecuta mediante Docker.

## Getting Started

### 1. Clone the repository

```bash
git clone <repository-url>
cd gymtracker-api
```

### 2. Start PostgreSQL

PostgreSQL se ejecuta mediante Docker Compose.

```bash
docker compose up -d
```

Comprobar que el contenedor está funcionando:

```bash
docker compose ps
```

Para consultar los logs:

```bash
docker compose logs postgres
```

La configuración actual de desarrollo utiliza:

```text
Database: gymtracker
Username: gymtracker
Port: 5432
```

### 3. Start the API

El proyecto incluye Maven Wrapper, por lo que no es necesario tener Maven instalado globalmente.

#### Windows

```bash
mvnw.cmd spring-boot:run
```

#### Linux / macOS

```bash
./mvnw spring-boot:run
```

También es posible ejecutar el proyecto utilizando Maven instalado globalmente:

```bash
mvn spring-boot:run
```

Cuando la aplicación esté iniciada, estará disponible en:

```text
http://localhost:8080
```

> Actualmente el backend se encuentra en desarrollo y todavía no dispone de todos los endpoints de la aplicación.

## Docker & PostgreSQL

El entorno de desarrollo utiliza Docker Compose para ejecutar PostgreSQL.

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: gymtracker
      POSTGRES_USER: gymtracker
      POSTGRES_PASSWORD: gymtracker
    ports:
      - "5432:5432"
    volumes:
      - gymtracker_data:/var/lib/postgresql/data
```

Los datos de PostgreSQL se almacenan en un volumen Docker llamado:

```text
gymtracker_data
```

Esto permite que los datos sobrevivan a la eliminación del contenedor.

### Stop PostgreSQL

Para detener los contenedores:

```bash
docker compose down
```

Esto elimina el contenedor, pero **mantiene los datos almacenados en el volumen**.

### Remove PostgreSQL data

Para detener los contenedores y eliminar también el volumen:

```bash
docker compose down -v
```

> ⚠️ Este comando elimina los datos persistidos de PostgreSQL. Utilízalo únicamente cuando quieras empezar con una base de datos limpia.

## Database Migrations

El esquema de PostgreSQL será gestionado mediante **Flyway**.

Las migraciones estarán versionadas dentro del proyecto y se ejecutarán automáticamente cuando se inicie la aplicación.

Ejemplo:

```text
V1__create_users.sql
V2__create_exercises.sql
V3__create_routines.sql
```

Flyway permite mantener el esquema de la base de datos sincronizado entre diferentes entornos y versiones del proyecto.

## Configuration

La configuración principal de Spring Boot se encuentra en:

```text
src/main/resources/application.yml
```

La configuración sensible no deberá almacenarse directamente en el repositorio cuando pasemos a entornos reales.

Para producción se utilizarán variables de entorno o mecanismos equivalentes de gestión de secretos.

## Development Workflow

El flujo básico para trabajar con el proyecto es:

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

Comandos principales:

```bash
# Start PostgreSQL
docker compose up -d

# Check containers
docker compose ps

# Start API - Windows
mvnw.cmd spring-boot:run

# Stop PostgreSQL
docker compose down
```

## Planned Features

### Authentication

* User registration
* Login
* Password hashing
* JWT authentication
* Authorization
* Protected resources

### Exercises

* Global exercise catalogue
* User-created exercises
* Exercise translations
* Exercise aliases
* Exercise search
* Exercise information

### Routines

* Create routines
* Edit routines
* Add/remove exercises
* Configure planned sets
* User-specific routines

### Workouts

* Record completed workouts
* Record exercises performed
* Record sets, repetitions and weight
* Add observations
* Register workouts retroactively
* Preserve historical workout data independently from current routines

### Statistics

* Exercise progression
* Weight progression
* Repetition progression
* Training history
* Future workout statistics

### Frontend Integration

The backend will eventually replace the current `localStorage` persistence used by the Gym Tracker frontend.

The migration process will be designed so that existing local data can be transferred to the backend when the user creates an account, avoiding data loss.

## Exercise Dataset

The project will use an external exercise dataset as an initial source of exercise information.

The dataset provides information such as:

* Exercise names
* Categories
* Body parts
* Equipment
* Target muscles
* Secondary muscles
* Instructions
* Translations

The external dataset will be imported into our own database model rather than being queried directly by the application at runtime.

The application's domain model will remain independent from the external dataset.

External exercise IDs will therefore not be used as the primary identifiers of our domain entities.

## Internationalization

The exercise catalogue is designed with internationalization in mind.

Exercise identity and localized information are kept conceptually separate so that the same exercise can have different names and instructions depending on the selected language.

Initially, the application will support:

* Spanish
* English

Additional languages may be added in the future.

## Testing

Testing will be progressively added throughout the project.

Planned testing strategy:

* Unit tests
* Repository tests
* Service/application tests
* Controller/API tests
* Integration tests
* PostgreSQL integration tests
* Testcontainers

## API Documentation

OpenAPI/Swagger documentation will be added once the main API endpoints are implemented.

The documentation will provide information about:

* Available endpoints
* Request bodies
* Response models
* Authentication requirements
* Validation errors

## Deployment

The application is being developed with deployment in mind.

Planned deployment:

```text
Angular Frontend
       │
       ▼
   REST API
       │
       ▼
   PostgreSQL
```

The backend will eventually be containerized and deployed together with a PostgreSQL database using an appropriate hosting solution.

## Security

Security is a core part of the backend architecture.

Planned security measures include:

* Password hashing
* JWT authentication
* Authorization based on the authenticated user
* Input validation
* Protection against unauthorized access to other users' resources
* Environment-based configuration for secrets
* Database constraints and foreign keys

Security through UUIDs alone will **not** be relied upon. Authorization will always be enforced on the server side.

## Project Status

🚧 **Work in progress**

Current progress:

* [x] Spring Boot project created
* [x] Java 21 configured
* [x] Maven configured
* [x] PostgreSQL configured with Docker
* [x] Docker Compose configured
* [x] Persistent PostgreSQL volume configured
* [x] Spring Security dependency added
* [x] Spring Data JPA configured
* [x] Flyway configured
* [ ] Connect Spring Boot to PostgreSQL
* [ ] First database migration
* [ ] Domain model
* [ ] Exercise catalogue
* [ ] User authentication
* [ ] Routines
* [ ] Workouts
* [ ] Statistics
* [ ] Angular integration
* [ ] Automated tests
* [ ] OpenAPI documentation
* [ ] Dockerize backend
* [ ] CI/CD
* [ ] Deployment

## License

This project is a personal portfolio project.

Third-party datasets, libraries and resources used by the project remain subject to their respective licenses and attribution requirements.
