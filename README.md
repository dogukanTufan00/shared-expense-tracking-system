**English** | [Türkçe](README.tr.md)

# Shared Expense Tracking System

A REST API for tracking shared household expenses between roommates — a localized take on Splitwise for students and young professionals in Türkiye.

Users register, create a household, invite their roommates, and record who paid for what (rent, utilities, groceries, …). The API is the backend for a planned mobile app (Flutter, Android first).

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation) |
| Database | PostgreSQL 16 (Docker) |
| Authentication | JWT (stateless, `jjwt`), passwords hashed with BCrypt |
| API documentation | Swagger UI (`springdoc-openapi`) |
| Build | Gradle |

## Features

- **Registration & login** — returns a JWT that authenticates every other request.
- **Households** — create, list, view, rename and delete households. The creator becomes the household's `ADMIN`.
- **Members** — admins add roommates by email and remove them. A household has at most 3 active members.
- **Expenses** — full CRUD for expenses with amount, category (`RENT`, `UTILITIES`, `GROCERIES`, `OTHER`), payer, date and a recurring flag.
- **Authorization rules enforced in the backend**
  - Only active members can see a household and its data. Non-members get `404`, so the API does not reveal which households exist.
  - Only admins can rename/delete a household or manage members.
  - Only the payer or an admin can edit or delete an expense.
  - Removing a member is a soft delete (`leftAt` is set), so past expenses keep pointing at a real user.
- **Consistent error responses** — validation errors, missing resources, conflicts and forbidden actions all return a uniform JSON error body with the correct HTTP status code.

## Architecture

```
controller  →  service  →  repository  →  entity
   (HTTP)    (business rules)  (Spring Data JPA)  (JPA / PostgreSQL)
```

- **Controllers** are thin: validate the request, call a service, return a DTO.
- **Services** hold all business and authorization rules.
- **DTOs** (Java records) separate the API contract from the database entities, so internal fields such as password hashes never leak into responses.
- **`GlobalExceptionHandler`** turns exceptions into consistent error responses.
- **`JwtAuthenticationFilter`** reads the `Authorization: Bearer <token>` header on each request.

### Data model

`User`, `Household`, `Membership` (user ↔ household with a role), `Expense`, `ExpenseShare`.

## Getting Started

### Prerequisites

- JDK 25
- Docker Desktop

### Run locally

```bash
# 1. Create your local environment file
cp .env.example .env

# 2. Start PostgreSQL
docker compose up -d

# 3. Start the API (Windows: gradlew.bat bootRun)
./gradlew bootRun
```

The API starts on `http://localhost:8080`.

Optional database GUI (pgAdmin at `http://localhost:5050`):

```bash
docker compose --profile tools up -d
```

### Configuration

Settings are read from environment variables, with local-development defaults in `application.yaml`. See [`.env.example`](.env.example) for the full list. For anything other than local development, set your own `JWT_SECRET` (at least 32 characters) and database password.

## API Documentation

Swagger UI is available at **http://localhost:8080/swagger-ui/index.html** once the app is running.

To call protected endpoints from Swagger:

1. `POST /api/auth/register` to create a user.
2. `POST /api/auth/login` and copy the `token` from the response.
3. Click **Authorize** and paste the token.

### Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register a new user |
| `POST` | `/api/auth/login` | Log in and receive a JWT |
| `POST` | `/api/households` | Create a household |
| `GET` | `/api/households` | List my households |
| `GET` | `/api/households/{id}` | Get a household |
| `PUT` | `/api/households/{id}` | Rename a household (admin) |
| `DELETE` | `/api/households/{id}` | Delete a household (admin) |
| `POST` | `/api/households/{householdId}/members` | Add a member by email (admin) |
| `GET` | `/api/households/{householdId}/members` | List active members |
| `DELETE` | `/api/households/{householdId}/members/{userId}` | Remove a member (admin) |
| `POST` | `/api/households/{householdId}/expenses` | Create an expense |
| `GET` | `/api/households/{householdId}/expenses` | List expenses |
| `GET` | `/api/households/{householdId}/expenses/{expenseId}` | Get an expense |
| `PUT` | `/api/households/{householdId}/expenses/{expenseId}` | Update an expense (payer or admin) |
| `DELETE` | `/api/households/{householdId}/expenses/{expenseId}` | Delete an expense (payer or admin) |

All endpoints except `/api/auth/**` require a valid JWT.

## Roadmap

- Split expenses between members (`ExpenseShare`) and calculate balances ("who owes whom")
- Settlement tracking with Turkish payment methods (IBAN / FAST / Papara)
- Automated tests
- Database migrations with Flyway
- Flutter mobile client
