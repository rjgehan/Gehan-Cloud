# Deploying

The image, the server, the tunnel, and what runs in CI. For local development see
[Running it](running.md).

## Deployment

### The image

Every push to `main` runs the tests, builds the container, and publishes it to
GitHub Container Registry:

```
ghcr.io/rjgehan/gehan-cloud:latest
```

Images are published for `linux/amd64` and `linux/arm64`. Maven runs natively on
the build platform either way — a Spring Boot jar is architecture-independent, so
only the JRE base layer differs and nothing is built under emulation.

Alongside `latest`, every build is tagged `sha-<commit>`, and `v*` tags publish
semver tags. Pin `IMAGE_TAG` to a `sha-` tag when you want to hold a known-good
build.

The server pulls that tag; there is no SSH step, no deploy key, and nothing
inbound to the server.

### The server

[`docker-compose.yml`](../docker-compose.yml) is the whole deployment. It runs the
portal with no published port at all — the only route in is the tunnel, which
`cloudflared` dials outward. The server needs no open port, no port forwarding on
the router, and no public IP.

`cloudflared` already runs on this host for the other services, so the portal
joins the Docker network it is on rather than starting a second tunnel:

```bash
docker network create edge                  # once, if it does not exist
docker network connect edge cloudflared     # once, so the tunnel can resolve us

git clone https://github.com/rjgehan/Gehan-Cloud.git && cd Gehan-Cloud
cp .env.example .env
$EDITOR .env                                # see the notes in the file
docker compose up -d
```

Compose gives the container the network alias `gehan-cloud`, which is the name
the tunnel routes to. Check it came up before touching the Cloudflare side:

```bash
docker compose ps                           # healthy, not just running
docker compose logs -f gehan-cloud
docker run --rm --network edge curlimages/curl -sI http://gehan-cloud:8080/login
```

That last one should return `302` to `/login` or `200` — proof the tunnel will be
able to reach it.

`users.db` lives in the named volume `gehan-cloud-data`, mounted at `/data`.
Without it, every redeploy starts from an empty database: a fresh unclaimed admin
account and the loss of every password the family has claimed.

### On CasaOS

[`deploy/casaos/docker-compose.yml`](../deploy/casaos/docker-compose.yml) is the whole
thing as one importable CasaOS app — the portal **and its own cloudflared** —
rather than joining the tunnel already on the host. **+ → Custom Install →
Import**, paste, fill in the two `CHANGE ME` values.

One command first, or the app starts and immediately dies:

```bash
sudo mkdir -p /DATA/AppData/gehan-cloud
sudo chown -R 10001:10001 /DATA/AppData/gehan-cloud   # the UID the image runs as
```

That chown is the thing that catches people. The image runs as UID 10001 and a
bind mount arrives owned by root, so without it the app cannot create `users.db`.

**Use a new tunnel, not the one already serving another hostname.** The same token
in two places makes two replicas of one tunnel, and Cloudflare hands either
replica any hostname that tunnel serves — so about half the traffic for the other
site would land on this container, which cannot route it. Create a second tunnel
in Zero Trust, give it a Public Hostname of `gehan.cloud` → `HTTP` →
`gehan-cloud:8080`, and paste its token into `TUNNEL_TOKEN`.

Because CasaOS stores the compose as you paste it, that token is visible in the
app's settings. Treat the CasaOS UI as somewhere the secret lives; if it leaks,
delete the tunnel and the token dies with it.

Three things differ from the root compose file, each forced by CasaOS:

| | |
| --- | --- |
| Settings are inline, not `env_file` | CasaOS keeps the compose in a directory of its own, where a relative `.env` path does not resolve — the app would come up unconfigured |
| `/DATA/AppData` bind mount, not a named volume | So CasaOS's file manager and backups can see the database |
| An `x-casaos` block | Gives it a real tile, which opens `https://gehan.cloud` rather than a LAN port |

The two services share a user-defined network so Docker's embedded DNS resolves
`gehan-cloud`. Do not set `network_mode: bridge` on either: the default bridge
does not resolve container names, and cloudflared would never find the portal.

### Why no published port

Both compose files leave `ports:` out entirely. Two reasons:

- `CLIENT_IP_HEADER` only holds while the tunnel is the single route in. Anything
  that can open a socket to 8080 can send `CF-Connecting-IP` itself.
- The session cookie is `Secure`, so it is never sent over plain HTTP. Reaching
  the portal at `http://<server-ip>:8080` would serve a login form that silently
  refuses to log you in — a miserable thing to debug.

Use `https://gehan.cloud`, at home as much as anywhere else.

### The Cloudflare side

Add `gehan.cloud` to the existing tunnel, pointing at `http://gehan-cloud:8080`.
Where that goes depends on how the tunnel is configured — check which one you
have with `docker inspect cloudflared --format '{{join .Config.Cmd " "}}'`:

**Dashboard-managed** (the command contains `--token`, or `TUNNEL_TOKEN` is in the
environment). Zero Trust → Networks → Tunnels → your tunnel → **Public Hostname**
→ Add:

| Field | Value |
| --- | --- |
| Subdomain | *(blank)* |
| Domain | `gehan.cloud` |
| Path | *(blank)* |
| Service | `HTTP` → `gehan-cloud:8080` |

Add a second hostname for `www` if you want it. Saving creates the DNS record.

**Locally-managed** (the command names a `config.yml`). Add an ingress rule
*above* the catch-all, which must stay last:

```yaml
ingress:
  - hostname: gehan.cloud
    service: http://gehan-cloud:8080
  # ... the existing rules, e.g. meals.gehan.cloud ...
  - service: http_status:404      # always last
```

Then create the DNS record and restart the tunnel:

```bash
cloudflared tunnel route dns <tunnel-name> gehan.cloud
docker restart cloudflared
```

Either way, on the DNS page the record for `gehan.cloud` must be **proxied**
(orange cloud). Grey-clouded, the tunnel is bypassed and `CF-Connecting-IP` never
arrives.

### Go-live checklist

1. `BASE_DOMAIN=gehan.cloud` and `SERVER_SERVLET_SESSION_COOKIE_DOMAIN=gehan.cloud`
   in `.env`. No leading dot on the cookie domain — Tomcat rejects `.gehan.cloud`
   and every login 500s.
2. `CLIENT_IP_HEADER=CF-Connecting-IP` — see
   [Which address the app believes](networking.md#which-address-the-app-believes).
3. In Cloudflare, SSL/TLS encryption mode **Full (strict)** and **Always Use
   HTTPS** on. The session cookie is `Secure`, so it is never sent over plain HTTP
   and login silently fails without this.
4. Load `https://gehan.cloud` and **sign in as `admin` immediately.** The first
   password typed claims the account, and until then anyone who reaches the login
   page can claim it. This is the one step with a clock on it.
5. Create the family's accounts from `/users`, and tell each person to log in
   promptly — the same claiming rule applies to them.
6. Still on `/users`, read off the address it says you arrived from. Do this once
   from each house and put both in `TRUSTED_NETWORKS` as `/32`s, then
   `docker compose up -d` to apply. Until then the Dashboard tile stays grey at
   home. Home addresses rotate, so expect to redo this occasionally.
7. Consider a rate limit in front of `/login`. Nothing in the app limits guessing,
   and the tunnel publishes it to everyone. A Cloudflare WAF rate-limiting rule on
   `POST /login` is the least effort here.

### Updates

Watchtower polls GHCR and restarts the container when `latest` moves. If the
server already runs one for its other stacks, that instance covers this container
too — it opts in with the `com.centurylinklabs.watchtower.enable` label. Otherwise
start the bundled one:

```bash
docker compose --profile watchtower up -d
```

To deploy by hand instead, or to roll back to a pinned `IMAGE_TAG`:

```bash
docker compose pull && docker compose up -d
```

### Continuous integration

| Workflow | Trigger | What it does |
| --- | --- | --- |
| [`ci.yml`](../.github/workflows/ci.yml) | push, PR | `./mvnw verify` |
| [`publish.yml`](../.github/workflows/publish.yml) | push to `main`, `v*` tags | Tests, then builds and pushes multi-arch to GHCR |
| [`codeql.yml`](../.github/workflows/codeql.yml) | push, PR, weekly | Static analysis; results in the Security tab |
| [`dependabot.yml`](../.github/dependabot.yml) | weekly | Grouped update PRs for Maven, base images and Actions |

CodeQL and Dependabot need **Code scanning** and **Dependabot alerts** switched on
under Settings → Code security. Both are free on public repositories.

