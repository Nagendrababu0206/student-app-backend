# EDUAI Java Backend

Spring Boot backend for login, registration, and school-only recommendation chatbot.

## Tech
- Java 17
- Spring Boot 3
- Maven
- PostgreSQL

## Run
1. Open terminal in `backend/`
2. Run:

```bash
mvn spring-boot:run
```

Backend starts on: `http://localhost:3001`

## Database configuration
Set these environment variables before running:

- `SPRING_DATASOURCE_URL` (example: `jdbc:postgresql://<host>:5432/<db>`)
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- optional: `SPRING_JPA_HIBERNATE_DDL_AUTO` (default: `update`)

## Endpoints
- `GET /api/health`
- `POST /api/register`
- `POST /api/login`
- `POST /api/recommend-chat`

### Register request
```json
{
  "name": "Student",
  "phone": "9876543210",
  "email": "student1@eduai.com",
  "password": "Password1"
}
```

### Login request
```json
{
  "username": "student@eduai.com",
  "password": "Password1"
}
```

### Recommend chat request
```json
{
  "message": "recommend for math low score 45",
  "scope": "school_students_only",
  "latestAssessment": null
}
```

## Frontend integration
- `Script.js` -> `POST /api/login`
- `Signup.js` -> `POST /api/register`
- `homePage_resources.js` live AI toggle -> `POST /api/recommend-chat`

## Notes
- User data is stored in PostgreSQL.
- Passwords are stored as BCrypt hashes.
