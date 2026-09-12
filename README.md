# Gehan Cloud

[![CI](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/ci.yml/badge.svg)](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/ci.yml)
[![Publish image](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/publish.yml/badge.svg)](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/publish.yml)
[![CodeQL](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/codeql.yml/badge.svg)](https://github.com/rjgehan/Gehan-Cloud/actions/workflows/codeql.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A self-hosted launcher for the services running at
[gehan.cloud](https://gehan.cloud) — one page the family can remember, with a tile
for everything else.

The hard part of a family server is not running the services. It is that photos
live on one box, groceries on another, two dashboards sit on two different home
networks, and none of it is worth publishing to the internet. This is the page
that ties them together: a phone-style home screen, a login the family already
knows, and enough awareness of *where you are standing* to know which tiles can
actually be reached from there.

Spring Boot serves the launcher, the login, and an admin-only user console. That
is the whole app — no API, no SPA, no database server. It ships as a container:
every push to `main` builds an image, publishes it to GHCR, and Watchtower on the
server pulls and restarts.

**Running it yourself:** [Running it](docs/running.md) ·
[Networks and tiles](docs/networking.md) · [Deploying](docs/deploying.md)

---

## What's interesting here

**Passwords are claimed at first login, not assigned by an admin.** Creating an
account stores no password at all. The first password typed at the login screen
for an unclaimed account becomes that account's password. This avoids the usual
"admin emails you a temporary password" dance for a handful of family users, and
it is implemented as a custom `AuthenticationProvider` that runs *ahead* of Spring
Security's `DaoAuthenticationProvider` in the `ProviderManager` chain, so the
normal password path is untouched.

The tradeoff is deliberate and documented: an unclaimed account is claimable by
anyone who knows the username until the intended person logs in.

**Lockout guards live in the service layer, not the UI.** You cannot delete your
own account, strip your own admin role, or remove the last remaining admin. Those
checks sit in `UserService` and are covered by unit tests, rather than being
enforced by hiding buttons in a template.

**The tile list is configuration, not markup.** The launcher renders from
[`apps.yml`](src/main/resources/apps.yml), so adding a service is one entry rather
than a template edit. Tiles fill a page and overflow onto the next one you swipe
to, the way a phone home screen does, and the page size is derived from the CSS
breakpoint rather than hard-coded — so it re-pages itself instead of having a tile
budget to stay under.

**The launcher degrades without JavaScript.** The pager is an enhancement layered
over a plain grid the server already rendered; with scripting off the tiles stay
one grid and every link still works.

**A tile for an unpublished service is rendered without an href at all.** Not
disabled in CSS, not hidden with `pointer-events` — the private address is simply
not in the HTML you were served. Off-network there is nothing to click, focus, or
copy out of the page.

## The two houses problem

The most interesting constraint in this codebase, and a good example of the shape
of the whole thing.

Two houses, each with a dashboard box on its own LAN. Neither is published to the
internet — they live at private addresses that do not route from anywhere else.
One tile on the launcher should reach whichever one you are standing in.

The first version leaned on the addresses themselves. Both boxes answered at
`192.168.1.23:8088`, so a single hard-coded URL resolved to whichever house you
were in — your browser is what connects, not the server, so the address means
something different on each network. Elegant, and it worked.

It also quietly forced both boxes onto the same address, the same port, and the
same LAN range. When one of them was handed a different DHCP lease, the tile
became a live link to nothing. And it failed *silently*: the other house still
worked, so nothing looked broken from there.

The portal already knew more than it was using. Behind Cloudflare it reads
`CF-Connecting-IP`, so it sees the public address each house arrives from, and the
two were already configured separately. It just collapsed them into one boolean —
*am I in one of my houses?* — and threw away which one.

So the networks are named, and a tile can carry one URL per network:

```yaml
- label: Dashboard
  lanOnly: true
  url: http://192.168.1.23:8088      # fallback when no group matches
  urls:
    home:  http://192.168.1.23:8088
    beach: http://192.168.1.210:8088
```

`LocalNetwork.nameFor(address)` sits next to the older `includes(address)`: same
matching, but it answers *which* group rather than *whether* — and naming a house
implies trusting it, so the two groups are the whole configuration. Each house now names
its own address and they need not agree. A visitor no group claims falls back to
the plain `url`, and a `lanOnly` tile with neither renders greyed out with no
href — so one house's private address is never in the page you are served in the
other. That last property is asserted by a test, because it is the one that would
be easy to lose in a refactor.

Full detail in [Networks and tiles](docs/networking.md#two-houses-one-tile).

## Architecture

```
                    browser
                       |
                       v   https
      +------------------------------------+
      |  Cloudflare edge (TLS, DNS)        |
      +----------------+-------------------+
                       |   tunnel, dialled outbound from the server:
                       |   no open port, no forwarded port, no public IP
                       v
      +------------------------------------+
      |  cloudflared                       |
      +----------------+-------------------+
                       |   http://gehan-cloud:8080 over a Docker network
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

Nothing listens on the server's public interface. The tunnel is dialled
*outbound*, so there is no open port to find and no port forwarding on the router.
Services on other machines are reached over Tailscale, which means a remote box
needs no public address of its own either.

## Stack

| | |
| --- | --- |
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.5 (Web, Security, Data JPA, Thymeleaf) |
| Storage | SQLite, one table of users |
| Auth | Session form login, BCrypt hashing |
| Container | Multi-stage Docker build, layered Spring Boot jar, multi-arch |
| Ingress | Cloudflare Tunnel — nothing listens on the server's public interface |
| CI/CD | GitHub Actions to GHCR, Watchtower on the server |

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

The two `__` probes exist so other services can sit behind this same login without
each growing its own: a reverse proxy asks `/__auth` whether the visitor is signed
in, or `/__lan` whether they are on a network the answer should depend on, and acts
on the reply. See [Networks and tiles](docs/networking.md).

## Security

Every route is a page behind a session; there is no API surface and no token to
leak. What that leaves:

| | |
| --- | --- |
| Passwords | BCrypt. Never stored or logged in the clear. |
| CSRF | On. Every state-changing route is a form and carries a token. |
| Session cookie | `Secure`, `HttpOnly`, `SameSite=Lax`, rotated on login. |
| Headers | CSP with `script-src 'self'`, plus HSTS, `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`. |
| Redirects | The `continue` parameter is validated against `BASE_DOMAIN`; off-domain targets are dropped. |
| Errors | No stack traces or internal messages returned to clients. |
| Container | Runs as a non-root user. Devtools is excluded from the packaged jar. |

### Known limitations

These are deliberate, and worth knowing before you deploy this yourself.

**An unclaimed account can be claimed by anyone who knows the username.** That is
how first-login password setting works, and this repo being public means the
mechanism is public too. Create accounts when the person is ready to use them. The
same applies to the admin account created on an empty database — sign in as it
immediately.

**Nothing rate-limits the login.** There is no lockout, delay, or captcha, so an
exposed instance can be guessed at as fast as the network allows. Put a rate limit
in the reverse proxy if that matters to you.

**`lanOnly` and `/__lan` are presentation, not access control.** They read an
address from a header the proxy sets. What actually keeps those services private is
that they are not published. Do not let a proxy trust client-supplied forwarding
headers — in Traefik, leave `forwardedHeaders.insecure` off.

**The login is the only thing standing between the internet and the portal.** The
tunnel publishes `gehan.cloud` to everyone; Cloudflare does not authenticate anyone
on its own. If you want a second gate in front, that is Cloudflare Access,
configured on the Cloudflare side rather than here.

## Repository layout

```
src/main/java/cloud/gehan/
├── config/       SecurityConfig, AdminBootstrap, PortalProperties
├── controller/   Home (launcher), Login, UserAdmin, AuthProbe
├── model/        User entity
├── repository/   Spring Data JPA
├── security/     FirstLoginAuthenticationProvider, RedirectTargets,
│                 LocalNetwork, ClientAddress
└── service/      UserService — business rules and lockout guards

src/main/resources/
├── apps.yml      the portal tile list — add services here
├── static/css/   home.css (portal), users.css
└── templates/    index, login, users

docs/                the operator's half: running, networking, deploying
docker-compose.yml   the deployment: portal, optional Watchtower, optional tunnel
.env.example         every setting the server needs, with the reasoning
deploy/casaos/       the same deployment as an importable CasaOS app
```

## Documentation

| | |
| --- | --- |
| [Running it](docs/running.md) | Build, test, configure, add a tile, manage accounts |
| [Networks and tiles](docs/networking.md) | Forward-auth, `lanOnly`, the two houses, which address the app believes |
| [Deploying](docs/deploying.md) | The image, the server, CasaOS, the Cloudflare side, CI |

## License

[MIT](LICENSE)
