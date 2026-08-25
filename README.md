# Gehan Cloud

[![CI](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/ci.yml/badge.svg)](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/ci.yml)
[![Publish image](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/publish.yml/badge.svg)](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/publish.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A self-hosted personal cloud portal, running in production at
[gehan.cloud](https://gehan.cloud). Spring Boot serves a launcher page, an
admin-only user management console, a shared grocery list API, and a share
endpoint that an iOS Shortcut posts links to.

It ships as a container: every push to `main` builds an image, publishes it to
GitHub Container Registry, and Watchtower on the server pulls and restarts.

---

## What's interesting here

**Passwords are claimed at first login, not assigned by an admin.** Creating an
account stores no password at all. The first password typed at the login screen
for an unclaimed account becomes that account's password. This avoids the usual
"admin emails you a temporary password" dance for a handful of family users, and
it is implemented as a custom `AuthenticationProvider` that runs *ahead* of
Spring Security's `DaoAuthenticationProvider` in the `ProviderManager` chain, so
the normal password path is untouched.

The tradeoff is deliberate and documented: an unclaimed account is claimable by
anyone who knows the username until the intended person logs in. See
[Account lifecycle](#account-lifecycle).

**Lockout guards live in the service layer, not the UI.** You cannot delete your
own account, strip your own admin role, or remove the last remaining admin.
Those checks sit in `UserService` and are covered by unit tests, rather than
being enforced by hiding buttons in a template.

**Two authentication schemes, one app.** Browser traffic uses session-based form
login; `/api/**` accepts a bearer token from `POST /auth/login`. A separate
shared-secret filter guards the one write endpoint an iOS Shortcut hits, and it
fails closed: if the secret was never configured, the endpoint returns 503
rather than accepting anything.

**`GET /__auth`** exists so Nginx's `auth_request` directive can gate *other*
services on the same box behind this app's session cookie.

## Stack

| | |
| --- | --- |
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.5 (Web, Security, Data JPA, Thymeleaf) |
| Storage | SQLite for users, JSON file for the grocery list |
| Auth | Session form login + JWT (jjwt), BCrypt hashing |
| Container | Multi-stage Docker build, layered Spring Boot jar, multi-arch |
| CI/CD | GitHub Actions to GHCR, Watchtower on the server |

## Architecture

```
        browser                iOS Shortcut
           |                        |
           v                        v
      +------------------------------------+
      |  Nginx (TLS, reverse proxy)        |
      +----------------+-------------------+
                       v
      +------------------------------------+
      |  Spring Boot                       |
      |                                    |
      |  FirstLoginAuthenticationProvider  |  claims unclaimed accounts
      |  DaoAuthenticationProvider         |  normal BCrypt check
      |  JwtFilter        -> /api/**       |  bearer tokens
      |  ApiKeyFilter     -> /api/share    |  shared secret, fails closed
      +-------+--------------------+-------+
              v                    v
        users.db (SQLite)     grocery.json
```

## Endpoints

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| `GET` | `/` | authenticated | Portal launcher |
| `GET` | `/login` | public | Form login, also claims unclaimed accounts |
| `GET` | `/__auth` | public | Session probe for Nginx `auth_request` |
| `POST` | `/auth/login` | public | Exchange credentials for a bearer token |
| `GET` | `/users` | `ADMIN` | User management console |
| `POST` | `/users`, `/users/{id}/delete`, `/{id}/reset`, `/{id}/role` | `ADMIN` | Create, delete, reset password, change role |
| `GET` | `/api/hello` | bearer token | Liveness sample |
| `GET` / `POST` | `/api/grocery/**` | public | Shared list and pantry state |
| `POST` | `/api/share` | `key` header | Receive a shared link |
| `GET` | `/api/share/latest` | bearer token | Read the most recent link |

## Running locally

Requires JDK 21. Maven is not needed, the wrapper is bundled.

```bash
./mvnw spring-boot:run
```

```bash
./mvnw test
```

```bash
./mvnw clean package
```

Tests run against in-memory H2 and a throwaway JSON file, so they never touch a
real `users.db` or `grocery.json`.

### Configuration

Every secret comes from the environment; nothing sensitive lives in
`application.properties`. See [`.env.example`](.env.example).

| Variable | Default | Purpose |
| --- | --- | --- |
| `SHARE_KEY` | *(unset)* | Shared secret for `POST /api/share`. Unset means the endpoint returns 503. |
| `JWT_SECRET` | *(unset)* | Bearer-token signing key, 32+ characters. Unset means a random key per boot, so tokens die on restart. |
| `APP_DATA_DIR` | `.` | Directory holding `users.db` and `grocery.json`. The container sets it to `/data`. |
| `APP_BOOTSTRAP_ADMIN` | `admin` | Username created when the database is empty. |
| `LOG_LEVEL` / `APP_LOG_LEVEL` | `INFO` | Root and application log levels. |

## Account lifecycle

Accounts are managed in the app, not in code. Sign in as an admin and the portal
shows a **Users** tile, or go straight to `/users`.

**First run on an empty database** creates an admin account (`admin` by default)
with no password and logs a warning. Log in as it immediately to claim it.

**Creating a user** stores no password. The first password typed at the login
screen for that username becomes the account's password.

**Resetting a password** clears it and returns the account to the unclaimed
state, so the user picks a new one at their next login. A reset does not
invalidate the old password *specifically* — it clears the password entirely, so
the old one would be accepted again if it happens to be what gets typed first.

Because of that, create accounts when the person is ready to log in, and tell
them to do it promptly.

**Guards against locking yourself out:** you cannot delete your own account,
cannot remove your own admin role, and the last remaining admin can be neither
deleted nor demoted.

## Deployment

Every push to `main` runs the tests, builds the container, and publishes it to
GitHub Container Registry:

```
ghcr.io/rjgehan/gehan-cloud:latest
```

Images are published for `linux/amd64` and `linux/arm64`. Maven runs natively on
the build platform either way — a Spring Boot jar is architecture-independent, so
only the JRE base layer differs and nothing is built under emulation.

The server pulls that tag; there is no SSH step, no deploy key, and nothing
inbound to the server. The container reads its configuration from the
environment ([`.env.example`](.env.example)) and keeps `users.db` and
`grocery.json` in `/data`, so mount a volume there to persist them across
redeploys.

## Repository layout

```
src/main/java/cloud/gehan/
├── config/       SecurityConfig, AdminBootstrap
├── controller/   HTTP endpoints
├── model/        User entity, grocery records
├── repository/   Spring Data JPA
├── security/     FirstLoginAuthenticationProvider, JwtUtil, ApiKeyFilter
└── service/      UserService, GroceryService — business rules and lockout guards
```

## License

[MIT](LICENSE)
