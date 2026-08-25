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

## Users

Accounts are managed in the app, not in code. Only admins can manage them.

Sign in as an admin and the portal shows a **Users** tile (or go straight to
`/users`). From there you can:

- **Create** a user, as `USER` or `ADMIN`
- **Reset** a password
- **Promote / demote** between `USER` and `ADMIN`
- **Delete** a user

### Passwords are set by the user, on first login

A new account is created with *no* password. The first password typed at the login
screen is saved as that account's password. Resetting a password clears it and puts
the account back in that state, so the user picks a new one at their next login.

Two consequences worth knowing:

- An account is claimable by anyone who reaches the login page and knows the
  username, until the intended person logs in. Create accounts when the person is
  ready to log in, and tell them to do it promptly.
- A reset does not invalidate the old password specifically — it clears the password
  entirely, so the old one would be accepted again if it is what gets typed first.

Guards that prevent locking yourself out: you cannot delete your own account, you
cannot remove your own admin role, and the last remaining admin can be neither
deleted nor demoted.

### First run on an empty database

If the database has no users at all, the app creates an admin account (named `admin`
by default, override with `app.bootstrap-admin`) with no password and logs a warning.
Log in as it immediately to set the password.

## Data files

These are runtime state, created on first use and git-ignored — they do not ship
with the repo:

- `users.db` — SQLite user store (schema auto-created via `ddl-auto=update`)
- `grocery.json` — grocery list state

## Build

```bash
./mvnw clean package     # jar in target/
./mvnw test
```
