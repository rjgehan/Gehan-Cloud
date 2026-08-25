# Gehan Cloud

[![CI](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/ci.yml/badge.svg)](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/ci.yml)
[![Publish image](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/publish.yml/badge.svg)](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/publish.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A self-hosted launcher for the services running at
[gehan.cloud](https://gehan.cloud): one page the family can remember, with a
tile for everything else. Spring Boot serves the launcher, form login, and an
admin-only user management console. That is the whole app.

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

**The tile list is configuration, not markup.** The launcher renders from
[`apps.yml`](src/main/resources/apps.yml), so adding a service is one entry
rather than a template edit. Tiles fill a page and overflow onto the next one
you swipe to, the way a phone home screen does, and the page size is derived
from the CSS breakpoint rather than hard-coded — so it re-pages itself instead
of having a tile budget to stay under.

**The launcher degrades without JavaScript.** The pager is an enhancement layered
over a plain grid the server already rendered; with scripting off the tiles stay
one grid and every link still works.

## Stack

| | |
| --- | --- |
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.5 (Web, Security, Data JPA, Thymeleaf) |
| Storage | SQLite, one table of users |
| Auth | Session form login, BCrypt hashing |
| Container | Multi-stage Docker build, layered Spring Boot jar, multi-arch |
| CI/CD | GitHub Actions to GHCR, Watchtower on the server |

## Architecture

```
                    browser
                       |
                       v
      +------------------------------------+
      |  Traefik (TLS, reverse proxy)      |
      +----------------+-------------------+
                       v
      +------------------------------------+
      |  Spring Boot                       |
      |                                    |
      |  FirstLoginAuthenticationProvider  |  claims unclaimed accounts
      |  DaoAuthenticationProvider         |  normal BCrypt check
      |                                    |
      |  /        launcher, from apps.yml  |
      |  /login   form login               |
      |  /users   admin console            |
      +------------------+-----------------+
                         v
                  users.db (SQLite)
```

## Endpoints

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| `GET` | `/` | authenticated | The launcher |
| `GET` | `/login` | public | Form login, also claims unclaimed accounts |
| `POST` | `/logout` | authenticated | Sign out |
| `GET` | `/users` | `ADMIN` | User management console |
| `POST` | `/users`, `/users/{id}/delete`, `/{id}/reset`, `/{id}/role` | `ADMIN` | Create, delete, reset password, change role |

That is every route. There is no API surface.

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

Tests run against in-memory H2, so they never touch a real `users.db`.

### Configuration

Nothing sensitive lives in `application.properties`. See
[`.env.example`](.env.example).

| Variable | Default | Purpose |
| --- | --- | --- |
| `APP_DATA_DIR` | `.` | Directory holding `users.db`. The container sets it to `/data`. |
| `APP_BOOTSTRAP_ADMIN` | `admin` | Username created when the database is empty. |
| `LOG_LEVEL` / `APP_LOG_LEVEL` | `INFO` | Root and application log levels. |

## Adding a service to the portal

The home screen is driven by [`apps.yml`](src/main/resources/apps.yml). Adding a
service is one entry; nothing else changes.

```yaml
portal:
  apps:
    - label: Plex
      url: https://plex.gehan.cloud
      icon: bi-film
      color: indigo
```

| Key | Purpose |
| --- | --- |
| `label` | Text under the icon. One short word reads best. |
| `url` | Absolute for another service, or `/path` for a page in this app. Absolute URLs open in a new tab. |
| `icon` | Any [Bootstrap Icons](https://icons.getbootstrap.com) name. |
| `color` | A palette from `home.css`: violet indigo blue cyan teal green lime amber orange red pink slate. Defaults to slate. |
| `adminOnly` | `true` hides the tile from everyone who is not an admin. |

Tiles fill a page and overflow onto the next one you swipe to, the way a phone
home screen does, so there is no tile count to keep under. How many fit per page
comes from `--cols` / `--rows` in `home.css`, so it re-pages itself at each
breakpoint rather than hard-coding a number. Without JavaScript the tiles stay a
single grid and every link still works.


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
environment ([`.env.example`](.env.example)) and keeps `users.db` in `/data`,
so mount a volume there to persist it across redeploys.

## Repository layout

```
src/main/java/cloud/gehan/
├── config/       SecurityConfig, AdminBootstrap, PortalProperties
├── controller/   Home (launcher), Login, UserAdmin
├── model/        User entity
├── repository/   Spring Data JPA
├── security/     FirstLoginAuthenticationProvider
└── service/      UserService — business rules and lockout guards

src/main/resources/
├── apps.yml      the portal tile list — add services here
├── static/css/   home.css (portal), users.css
└── templates/    index, login, users
```

## License

[MIT](LICENSE)
