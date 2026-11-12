**English** | [Türkçe](README.tr.md)

# Shared Expense Tracking System

A REST API for tracking shared household expenses between roommates — a localized take on Splitwise for students and young professionals in Türkiye.

Users register, create a household, invite their roommates, and record who paid for what (rent, utilities, groceries, …). Every expense can be split equally, by percentage, or by exact amounts. The API keeps a live balance for each member and suggests the fewest payments needed to settle everyone.

The API is the backend for the mobile app: [roommate-expense-app](https://github.com/dogukanTufan00/roommate-expense-app) (Flutter, Android first).

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation, Scheduling) |
| Database | PostgreSQL 16 (Docker) |
| Authentication | JWT (stateless, `jjwt` 0.13), passwords hashed with BCrypt |
| Push notifications | Firebase Admin SDK 9.12 (Firebase Cloud Messaging), optional |
| API documentation | Swagger UI (`springdoc-openapi` 3.1) |
| Build | Gradle, Lombok |

## Features

**Accounts and authentication**
- Registration and login. Passwords are hashed with BCrypt (minimum 8 characters); login returns a JWT that authenticates every other request.
- Profile with name and an optional Turkish IBAN. The IBAN is validated with the ISO 13616 mod-97 check, so typos are rejected before they are saved.
- Password change, which requires the current password.

**Households, roles and invitations**
- Any user can create a household. The creator becomes its **founder** and an **admin**.
- Roles: the founder, admins and members. Admins can promote and demote members and manage invitations. The founder cannot be changed or removed by anyone else while still a member, and can hand the founder role to another member.
- A household has at most 3 active members. Pending invitations count toward this limit.
- Admins invite registered users by email. The invitee must accept or decline; nobody joins without agreeing.
- Members can leave a household. The last admin cannot leave or be demoted.

**Expenses and splitting**
- Full CRUD for expenses with amount, category (`RENT`, `UTILITIES`, `GROCERIES`, `OTHER`), payer, date, and description.
- Three split types: `EQUAL`, `PERCENT` and `AMOUNT`. The payer chooses which members share each expense.
- Splits are calculated in whole cents, so the shares always add up exactly to the amount (e.g. 100 TL / 3 = 33.34 + 33.33 + 33.33).
- Each category can have a saved default split (per-person percentages). It is used only while the same people are active members.
- Only the payer or an admin can edit or delete an expense.

**Recurring expenses**
- An expense can repeat every month on the same day (rent, internet, subscriptions).
- A nightly job at 00:05 (Europe/Istanbul) creates the due copies. The job also runs at application startup and right after a recurring expense is saved.
- Missed months are caught up, up to 12 per run. A copy reuses the previous split if the same people are still members.
- If the payer has left the household, the chain stops, so nobody is charged for an expense they did not pay.
- Admins can trigger generation for their household on demand, which is useful for demos and testing.

**Balances, settlements and payments**
- Each member's net balance is computed live from expenses, shares and payments. Nothing is stored. Former members with a non-zero balance remain visible.
- "Who pays whom": a short list of suggested payments that settles everyone (greedy algorithm, at most n-1 payments). The creditor's IBAN is included when it is set.
- Payments record a repayment between two members, with an optional note. Payments can be edited and deleted by the sender or an admin. Overpayments are allowed and simply appear in the balances.

**Attachments**
- Receipts and bills can be attached to expenses; transfer slips (dekont) to payments.
- Accepted formats: JPEG, PNG, WEBP and PDF. The file type is checked from its first bytes (magic bytes), not from the file name or the client's `Content-Type`.
- Limits: 10 MB per file and 5 attachments per expense or payment.
- Files are stored on disk; metadata is stored in the database. Downloads require active membership. Deletion is allowed for the uploader, the payer or sender, and admins.

**Statements and insights**
- Period statement for any date range (up to 366 days; default: the current month in Turkish time).
- Per-person totals: amount paid, amount owed, net, and payments sent and received.
- "Dominant payer" insight: flags a member who paid at least 50% of the period's expenses.
- Category breakdown, a 6-month trend, and detailed expense and payment rows.

**Notifications**
- In-app inbox for events such as a new expense, a payment, an invitation, or an accepted invitation. Notifications are kept for 90 days.
- Optional Firebase Cloud Messaging push. Each device registers its token; tokens that Firebase reports as dead are removed automatically. Without Firebase configured, the app runs normally and only pushes are disabled.

**Quality of the API**
- Consistent JSON error responses with the correct HTTP status codes.
- Non-members receive `404` for household data, so the API does not reveal which households exist.
- Interactive documentation with Swagger UI, including an "Authorize" button for JWTs.

## Architecture

```
controller  →  service  →  repository  →  entity
   (HTTP)    (business rules)  (Spring Data JPA)  (JPA / PostgreSQL)
```

- **Controllers** are thin: they validate the request, call a service and return a DTO.
- **Services** hold all business and authorization rules.
- **DTOs** (Java records) separate the API contract from the database entities, so internal fields such as password hashes never leave the server.
- **Events** (`event` package): services publish a small event after an action; notification and recurring-expense listeners react to it after the transaction commits.
- **`GlobalExceptionHandler`** turns every exception into the same JSON error body.
- **`JwtAuthenticationFilter`** reads the `Authorization: Bearer <token>` header on each request.

### Data model

| Entity | Purpose |
|---|---|
| `User` | An account: name, email, BCrypt password hash, optional IBAN |
| `Household` | A shared home. Stores its founder (`createdBy`) |
| `Membership` | Links a user to a household with a role (`ADMIN` / `MEMBER`). `leftAt` marks a member who has left |
| `Invitation` | An admin's invitation to a registered user. Status: `PENDING`, `ACCEPTED`, `DECLINED`, `CANCELLED` |
| `Expense` | An amount paid by one member: category, description, date, split type, recurring flag and chain link |
| `ExpenseShare` | One member's part of an expense: the amount they owe and, for percentage splits, their percent |
| `CategorySplitDefault` | The saved default split of one category in one household |
| `Payment` | A repayment from one member to another, with amount, note and time |
| `Attachment` | Metadata of a receipt, bill or transfer slip (type, size, original name) linked to an expense or a payment |
| `Notification` | One inbox entry for one recipient |
| `DeviceToken` | A push token for one device of a user |

Enums: `MembershipRole`, `InvitationStatus`, `ExpenseCategory`, `SplitType`, `NotificationType`.

The key idea is the separation of three concepts:
- **Expense** = who paid what.
- **ExpenseShare** = how much each person owes for that expense.
- **Payment** = a real repayment between two people.

Balances and settlement suggestions are **computed** from these three tables on every request and are never stored. This means they cannot drift out of sync when an expense or payment is edited or deleted.

## Design Decisions

- **DTOs instead of entities.** Entities are never returned from controllers. This keeps password hashes out of responses, avoids lazy-loading and JSON recursion problems, and lets the API contract change independently of the database schema.
- **Computed balances.** There is one source of truth (the expenses, shares and payments). Stored balances would need updating on every edit and delete; computing them is simpler and correct by construction. At household scale the cost is negligible.
- **Whole-cent splitting.** Floating-point division loses or gains cents (150.50 / 3 is not exact). Amounts are split in integer cents, and the leftover cents go one by one to the first shares, so the shares always sum to the total.
- **Magic-byte file checks.** The file name and `Content-Type` header are chosen by the client and can be faked (an `.exe` renamed to `receipt.pdf` would pass). The first bytes of a file are fixed by its format, so they are checked instead.
- **`AFTER_COMMIT` events for notifications and recurring expenses.** Notifications, pushes and recurring copies run only after the database transaction has committed. They never announce or copy something that was rolled back, and a failure there does not turn a successful request into an error.
- **Files on disk, metadata in the database.** Binary files are kept out of the database. Each file gets a random UUID name, so user-supplied names cannot cause path traversal or collisions. Files are deleted only after the database change is committed. In a production deployment this layer would be replaced by object storage such as Amazon S3, without changing the rest of the code.
- **Soft delete for memberships.** Leaving a household sets `leftAt` instead of deleting the row. Past expenses keep pointing at a real user, and debts stay visible until they are settled.
- **Locale.ROOT at startup (the "Turkish I" bug).** On a machine with a Turkish default locale, `"HIGH".toLowerCase()` becomes `"hıgh"` (dotless ı). Libraries such as the Firebase SDK lowercase enum names, and the push messages were rejected. The JVM default locale is set to `Locale.ROOT` at startup, and Turkish text is formatted explicitly where it is needed.

## Getting Started

### Prerequisites

- JDK 25 (the Gradle toolchain in `build.gradle` requests it)
- Docker Desktop

### Run locally

Windows (PowerShell):

```powershell
# 1. Create your local environment file (docker compose reads it)
Copy-Item .env.example .env

# 2. Start PostgreSQL 16
docker compose up -d

# 3. Start the API
.\gradlew.bat bootRun
```

macOS / Linux: use `cp .env.example .env` and `./gradlew bootRun`.

The API starts on `http://localhost:8080`.

Optional database GUI (pgAdmin at `http://localhost:5050`):

```powershell
docker compose --profile tools up -d
```

### Environment variables

Spring Boot does not read the `.env` file by itself. The defaults in `src/main/resources/application.yaml` match the values in `.env.example`, so `bootRun` works without exporting anything. Set a variable only when you need a different value. For anything other than local development, set your own `JWT_SECRET` and database password.

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/roommate_db` | JDBC URL of the PostgreSQL database |
| `DB_USERNAME` | `roommate_user` | Database user |
| `DB_PASSWORD` | `change_me` | Database password (change it outside local development) |
| `JWT_SECRET` | placeholder | HS256 signing key. Must be at least 32 characters |
| `JWT_EXPIRATION_MS` | `86400000` (24 hours) | Lifetime of a JWT in milliseconds |
| `UPLOADS_DIR` | `./uploads` | Folder for attachment files. Relative paths are resolved from the folder the app starts in. Git-ignored |
| `FIREBASE_CREDENTIALS` | empty | Path to a Firebase service-account JSON file, stored **outside** the repository. Empty = push notifications disabled; everything else works |

Setting the Firebase key for one PowerShell session:

```powershell
$env:FIREBASE_CREDENTIALS = "C:\secrets\firebase-adminsdk.json"
.\gradlew.bat bootRun
```

## API Documentation

Swagger UI is available at **http://localhost:8080/swagger-ui/index.html** once the app is running.

To call protected endpoints from Swagger:

1. `POST /api/auth/register` to create a user.
2. `POST /api/auth/login` and copy the `token` from the response.
3. Click **Authorize** and paste the token.

### Access rules

- **Public**: no token needed.
- **Logged-in user**: any valid JWT; the endpoint works on the caller's own data.
- **Member**: an active member of the household. Non-members receive `404`.
- **Admin**: an active member with the `ADMIN` role.
- **Founder**: the household's creator, while still an active member.

All endpoints except `/api/auth/**` and the Swagger paths require a valid JWT.

### Auth

| Method | Path | Description | Access |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register a new user (201) | Public |
| `POST` | `/api/auth/login` | Log in and receive a JWT | Public |

### Profile and devices

| Method | Path | Description | Access |
|---|---|---|---|
| `GET` | `/api/users/me` | Get my profile | Logged-in user |
| `PUT` | `/api/users/me` | Update name and IBAN (blank IBAN removes it) | Logged-in user |
| `PUT` | `/api/users/me/password` | Change password (204) | Logged-in user |
| `PUT` | `/api/users/me/devices` | Register the push token of this device (204) | Logged-in user |
| `DELETE` | `/api/users/me/devices` | Remove a push token, e.g. on logout (204) | Logged-in user |

### Households

| Method | Path | Description | Access |
|---|---|---|---|
| `POST` | `/api/households` | Create a household; the creator becomes founder and admin (201) | Logged-in user |
| `GET` | `/api/households` | List the households I belong to | Logged-in user |
| `GET` | `/api/households/{id}` | Get a household | Member |
| `PUT` | `/api/households/{id}` | Rename a household | Admin |
| `DELETE` | `/api/households/{id}` | Delete a household and all its data (204) | Founder (any admin once the founder has left) |
| `POST` | `/api/households/{id}/transfer-ownership` | Hand the founder role to another active member | Founder |

### Members

| Method | Path | Description | Access |
|---|---|---|---|
| `GET` | `/api/households/{householdId}/members` | List active members | Member |
| `PATCH` | `/api/households/{householdId}/members/{userId}` | Change a member's role (`ADMIN` / `MEMBER`) | Admin |
| `DELETE` | `/api/households/{householdId}/members/{userId}` | Remove a member (204). A member can leave by using their own user id | Admin, or the member themselves |

### Invitations

| Method | Path | Description | Access |
|---|---|---|---|
| `POST` | `/api/households/{householdId}/invitations` | Invite a registered user by email (201) | Admin |
| `GET` | `/api/households/{householdId}/invitations` | List pending invitations | Member |
| `DELETE` | `/api/households/{householdId}/invitations/{invitationId}` | Cancel a pending invitation (204) | Admin |

### My invitations

| Method | Path | Description | Access |
|---|---|---|---|
| `GET` | `/api/invitations` | List my pending invitations, newest first | Logged-in user |
| `POST` | `/api/invitations/{invitationId}/accept` | Accept an invitation and join the household | Invitee |
| `POST` | `/api/invitations/{invitationId}/decline` | Decline an invitation (204) | Invitee |

### Expenses

| Method | Path | Description | Access |
|---|---|---|---|
| `POST` | `/api/households/{householdId}/expenses` | Create an expense with an optional split (201) | Member |
| `GET` | `/api/households/{householdId}/expenses` | List expenses | Member |
| `GET` | `/api/households/{householdId}/expenses/{expenseId}` | Get an expense | Member |
| `PUT` | `/api/households/{householdId}/expenses/{expenseId}` | Update an expense | Payer or admin |
| `DELETE` | `/api/households/{householdId}/expenses/{expenseId}` | Delete an expense (204) | Payer or admin |

### Recurring expenses

| Method | Path | Description | Access |
|---|---|---|---|
| `GET` | `/api/households/{householdId}/expenses/recurring` | List recurring chains with the date of the next copy | Member |
| `POST` | `/api/households/{householdId}/expenses/recurring/run` | Create the due copies now (for demos and testing) | Admin |

### Split defaults

| Method | Path | Description | Access |
|---|---|---|---|
| `GET` | `/api/households/{householdId}/split-defaults` | List the default split of every category | Member |
| `PUT` | `/api/households/{householdId}/split-defaults/{category}` | Replace the default split of one category | Admin |
| `DELETE` | `/api/households/{householdId}/split-defaults/{category}` | Delete the default split of one category (204) | Admin |

### Payments

| Method | Path | Description | Access |
|---|---|---|---|
| `POST` | `/api/households/{householdId}/payments` | Record a payment (201) | Sender or admin |
| `GET` | `/api/households/{householdId}/payments` | List payments | Member |
| `GET` | `/api/households/{householdId}/payments/{paymentId}` | Get a payment | Member |
| `PUT` | `/api/households/{householdId}/payments/{paymentId}` | Edit a payment. Only an admin can change the sender to another member | Sender or admin |
| `DELETE` | `/api/households/{householdId}/payments/{paymentId}` | Delete a payment (204) | Sender or admin |

### Attachments

| Method | Path | Description | Access |
|---|---|---|---|
| `POST` | `/api/households/{householdId}/expenses/{expenseId}/attachments` | Upload a receipt or bill (multipart, 201) | Member |
| `GET` | `/api/households/{householdId}/expenses/{expenseId}/attachments` | List the attachments of an expense | Member |
| `POST` | `/api/households/{householdId}/payments/{paymentId}/attachments` | Upload a transfer slip (multipart, 201) | Member |
| `GET` | `/api/households/{householdId}/payments/{paymentId}/attachments` | List the attachments of a payment | Member |
| `GET` | `/api/households/{householdId}/attachments/{attachmentId}/file` | Download or view the file | Member |
| `DELETE` | `/api/households/{householdId}/attachments/{attachmentId}` | Delete an attachment (204) | Uploader, payer or sender, or admin |

### Balances and settlements

| Method | Path | Description | Access |
|---|---|---|---|
| `GET` | `/api/households/{id}/balances` | Net balance of each member | Member |
| `GET` | `/api/households/{id}/settlements` | Suggested payments: who pays whom | Member |

### Statement

| Method | Path | Description | Access |
|---|---|---|---|
| `GET` | `/api/households/{id}/statement?from=yyyy-MM-dd&to=yyyy-MM-dd` | Period statement and per-person statistics. Give both dates or neither | Member |

### Notifications

| Method | Path | Description | Access |
|---|---|---|---|
| `GET` | `/api/notifications?limit=50` | My notifications, newest first (limit 1–100) | Logged-in user |
| `GET` | `/api/notifications/unread-count` | Number of unread notifications | Logged-in user |
| `POST` | `/api/notifications/{notificationId}/read` | Mark one notification as read (204) | Logged-in user (recipient) |
| `POST` | `/api/notifications/read-all` | Mark all my notifications as read (204) | Logged-in user |

## Roadmap

- Cloud deployment: container image for the API, managed PostgreSQL, and object storage for attachments instead of local disk
- Automated tests for the business rules: splits, balances, recurring dates, and invitation limits
- Database migrations with Flyway (the schema is currently managed by `ddl-auto: update`)
- Google Play release of the Flutter client: [roommate-expense-app](https://github.com/dogukanTufan00/roommate-expense-app)
- Refresh tokens, and token revocation on logout (logout currently only removes the push token)
- One-tap payment records created from the suggested settlements
- Rate limiting on login and registration
