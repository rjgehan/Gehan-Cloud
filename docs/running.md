# Running it

How to build, configure and operate this thing. For what it is and why it is built
this way, see the [README](../README.md).

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
[`.env.example`](../.env.example).

| Variable | Default | Purpose |
| --- | --- | --- |
| `APP_DATA_DIR` | `.` | Directory holding `users.db`. The container sets it to `/data`. |
| `APP_BOOTSTRAP_ADMIN` | `admin` | Username created when the database is empty. |
| `CLIENT_IP_HEADER` | unset | Header carrying the visitor's address. `CF-Connecting-IP` behind Cloudflare — see [Which address the app believes](networking.md#which-address-the-app-believes). |
| `LOG_LEVEL` / `APP_LOG_LEVEL` | `INFO` | Root and application log levels. |

## Adding a service to the portal

The home screen is driven by [`apps.yml`](../src/main/resources/apps.yml). Adding a
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
| `urls` | A url per named network, for a service that sits at a different address in each place. Keys are groups from `portal.networks`; the visitor's network picks one, and `url` is the fallback — see [Two houses, one tile](networking.md#two-houses-one-tile). |
| `icon` | Any [Bootstrap Icons](https://icons.getbootstrap.com) name. |
| `color` | A palette from `home.css`: violet indigo blue cyan teal green lime amber orange red pink slate. Defaults to slate. |
| `adminOnly` | `true` hides the tile from everyone who is not an admin. |
| `lanOnly` | `true` for a service that only routes on the host network — see [Services that only work at home](networking.md#services-that-only-work-at-home). |

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

