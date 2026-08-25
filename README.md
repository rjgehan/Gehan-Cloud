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
| `GET` | `/__auth` | public | Forward-auth probe: is this visitor signed in? |
| `GET` | `/__lan` | public | Forward-auth probe: is this visitor on the local network? |
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
| `lanOnly` | `true` for a service that only routes on the host network — see [Services that only work at home](#services-that-only-work-at-home). |

Tiles fill a page and overflow onto the next one you swipe to, the way a phone
home screen does, so there is no tile count to keep under. How many fit per page
comes from `--cols` / `--rows` in `home.css`, so it re-pages itself at each
breakpoint rather than hard-coding a number. Without JavaScript the tiles stay a
single grid and every link still works.


## Putting another service behind this login

Services running on other machines can be reached through this domain without
being exposed themselves. The server is on both the public internet and the
Tailscale network, so it bridges them: Traefik terminates TLS publicly and
forwards over Tailscale. The remote machine needs no public IP and no port
forwarding, and visitors never install Tailscale.

```
  visitor ──https──▶ server (public IP + tailnet) ──WireGuard──▶ app (tailnet only)
                     Traefik: TLS, routing, auth
```

Point a hostname at the server, then add a router and service in Traefik's
**file** provider — Docker labels do not apply, since the backend is not a
container on that host:

```yaml
http:
  middlewares:
    portal-auth:
      forwardAuth:
        address: "http://gehan-cloud:8080/__auth"
        authResponseHeaders: ["X-Auth-User"]

  routers:
    plex:
      rule: "Host(`plex.gehan.cloud`)"
      entryPoints: [websecure]
      middlewares: [portal-auth]        # omit for services with their own login
      service: plex
      tls:
        certResolver: letsencrypt

  services:
    plex:
      loadBalancer:
        servers:
          - url: "http://100.101.102.103:8080"    # the device's Tailscale IP
```

`GET /__auth` answers that middleware. A signed-in visitor gets 200 and the
request proceeds; anyone else gets a 302 to the portal login and is returned to
where they were going. Attach the middleware only to the routers that need it —
services with their own login work fine without it.

Two environment variables are required for this, both in
[`.env.example`](.env.example):

| Variable | Value | Why |
| --- | --- | --- |
| `BASE_DOMAIN` | `gehan.cloud` | Builds the login URL, and limits where a login may return you |
| `SERVER_SERVLET_SESSION_COOKIE_DOMAIN` | `gehan.cloud` | Sends the session cookie to every subdomain |

### Things that will catch you out

- **No leading dot on the cookie domain.** Tomcat validates it per RFC 6265 and
  rejects `.gehan.cloud`, turning every login into a 500. Plain `gehan.cloud`
  already covers subdomains.
- **Use the Tailscale IP, not the MagicDNS name.** A Traefik container will not
  resolve `*.ts.net`; `100.x.y.z` routes through the host and is stable.
- **The remote app must bind `0.0.0.0`.** Bound to loopback, or published as
  `-p 127.0.0.1:8080:8080` in Docker, the server cannot reach it.
- **Set `server.forward-headers-strategy=framework` in the remote app too**, or
  its redirects will point at `http://100.x.y.z:8080/...` instead of the public
  URL.
- **Tailscale ACLs** have to permit the server to reach that device and port.


## Services that only work at home

Some things should not be published at all — a NAS admin page, a router, a
hypervisor. Those get a tile that links straight at the private address:

```yaml
    - label: NAS
      url: http://192.168.1.50:5000
      icon: bi-hdd-network-fill
      color: cyan
      lanOnly: true
```

The browser is what connects, not the server, so on the home network that link
just works. From outside, the address does not route and the tab would hang.
`lanOnly` prevents that: off the network the tile is greyed out, carries a small
lock, and is rendered **without an href at all** — so there is nothing to click,
focus, or copy out of the page, and the private address never appears in the HTML.

Which networks count is `portal.trusted-networks`, defaulting to the private
ranges:

```properties
portal.trusted-networks=${TRUSTED_NETWORKS:10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.1/32,::1/128}
```

If the defaults do not match, sign in as an admin and open `/users` — it shows the
address the app actually saw you arrive from. Set `TRUSTED_NETWORKS` to whatever
that is. Two common cases need it:

- **Hairpin NAT.** Visiting the public hostname from inside the house can make
  every visitor arrive as one address, often the router's. Add it as a `/32`.
- **Split-horizon DNS.** If local DNS points the hostname at the server's LAN
  address, visitors keep their real LAN address and the defaults are fine.

### Turning a hostname into the launcher when you are away

A tile greys out, but a bookmark or a typed address still lands somewhere. For a
hostname that should only work at home, `GET /__lan` answers a forward-auth
middleware with 200 on the local network and a 302 to the portal everywhere else,
so being away quietly puts you on the launcher instead of an error page:

```yaml
http:
  middlewares:
    home-only:
      forwardAuth:
        address: "http://gehan-cloud:8080/__lan"

  routers:
    thing:
      rule: "Host(`thing.gehan.cloud`)"
      entryPoints: [websecure]
      middlewares: [home-only]
      service: thing
      tls:
        certResolver: letsencrypt

  services:
    thing:
      loadBalancer:
        servers:
          - url: "http://100.86.236.12:8088"
```

No session is needed for this one: it is a question about where you are, not who
you are. Chain it with `portal-auth` when you want both — `middlewares:
[home-only, portal-auth]` requires being at home *and* signed in.

`/__lan` shares `portal.trusted-networks` with the tiles, so it cannot tell one
site from another — everything gated this way is gated to the same set of
networks. That is fine as things stand and is worth understanding before adding a
second gate. See [Two houses, one tile](#two-houses-one-tile).

Nothing attaches this middleware today, and neither probe is in use: both
dashboards are private and Photoprism brings its own login. They are kept as the
answer for the next service that needs one.

### Two houses, one tile

Both dashboards answer at the same private address, `192.168.1.23:8080`, one on
each house's network. So a single tile serves both: the address itself resolves to
whichever house you are standing in, and neither dashboard is published or
reachable from outside.

That also keeps the network list simple. Gating two different services to two
different houses would need the list split into named groups, or standing in
either house would unlock both. Pointing both houses at one address turns the
question into "am I in one of my houses?", which one list answers:

```properties
TRUSTED_NETWORKS=65.84.61.185/32,<home public address>/32
```

It costs some uniformity. Both boxes have to answer on the same address and port,
and both networks have to use the same range, or one house will not find its own
dashboard.

The greying matters more here than usual. `192.168.1.x` is the most common home
network there is, so on someone else's wifi `.23` is quite likely to be a real
device — a printer, a router page, a camera. `lanOnly` is what keeps the tile from
being a live link to a stranger's hardware.

### Why the network list is safe to rely on here

A list of addresses is a weak thing to protect a service with: it says where a
request came from, not who sent it, it arrives in a header the proxy fills in, and
home internet addresses rotate. All true — which is why nothing is protected by
one here.

The services these tiles point at are never published. They live at private
addresses that do not route from anywhere else, so they cannot be reached from the
internet whatever the launcher renders. The list decides only whether a tile looks
live. Get it wrong and a tile is greyed when it should not be, or links somewhere
that does not answer. Nobody gets in.

Anything that does need protecting gets a login instead: its own, or this portal's
through `/__auth`.

**This is cosmetic, not a security boundary.** What keeps those services private
is that their addresses do not route from the internet. `lanOnly` only stops the
launcher offering a link that cannot work. A visitor who forged an
`X-Forwarded-For` header could make the tile appear, and would still not be able
to reach the service. Keep your proxy from trusting client-supplied forwarding
headers regardless — in Traefik that means leaving `forwardedHeaders.insecure`
off.


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
├── controller/   Home (launcher), Login, UserAdmin, AuthProbe
├── model/        User entity
├── repository/   Spring Data JPA
├── security/     FirstLoginAuthenticationProvider, RedirectTargets, LocalNetwork
└── service/      UserService — business rules and lockout guards

src/main/resources/
├── apps.yml      the portal tile list — add services here
├── static/css/   home.css (portal), users.css
└── templates/    index, login, users
```

## License

[MIT](LICENSE)
