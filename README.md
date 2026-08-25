# Gehan Cloud

Spring Boot app behind `gehan.cloud` — a small launcher/portal with form login,
JWT-protected APIs, a grocery list API, and a share endpoint.

## Requirements

- JDK 21 or newer
- Maven (or use the bundled `./mvnw` wrapper — no local Maven install needed)

## Configuration

Runtime secrets come from the environment, not from `application.properties`.

| Variable | Required | Purpose |
| --- | --- | --- |
| `SHARE_KEY` | yes, to use `POST /api/share` | Shared secret sent in the `key` header. If unset, `/api/share` returns 503. |

```bash
export SHARE_KEY=...        # PowerShell: $env:SHARE_KEY='...'
```

## Running

```bash
./mvnw spring-boot:run
```

Serves on http://localhost:8080. Everything except `/login`, `/api/grocery/**`,
`/api/share`, and static assets requires authentication.

## Data files

These are runtime state, created on first use and git-ignored — they do not ship
with the repo:

- `users.db` — SQLite user store (schema auto-created via `ddl-auto=update`)
- `grocery.json` — grocery list state

A fresh clone starts with an empty user table. Insert a user with a BCrypt-hashed
password to log in; `config/DbTestConfig.java` has a commented-out `CommandLineRunner`
that does this.

## Build

```bash
./mvnw clean package     # jar in target/
./mvnw test
```
