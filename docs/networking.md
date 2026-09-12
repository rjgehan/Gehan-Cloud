# Networks, tiles and forward-auth

How the portal decides what a visitor can reach, and how services that are not
published to the internet still get a working tile. For the short version, see
[the README](../README.md#the-two-houses-problem).

## Putting another service behind this login

> **This needs a reverse proxy, and the tunnel is not one.** `cloudflared` routes
> a hostname to a backend and nothing else — it has no forward-auth, so it cannot
> call `/__auth` and act on the answer. Two ways to have it:
>
> - **Run a proxy behind the tunnel.** Point `cloudflared` at Traefik instead of
>   at each service, and let Traefik do the `forwardAuth` exactly as below. The
>   tunnel becomes one more hop and the rest of this section is unchanged.
> - **Use Cloudflare Access.** Cloudflare authenticates at the edge, before the
>   request reaches the server. It does not use this portal's accounts, so the
>   family would have a second set of credentials.
>
> Nothing uses either today: both dashboards are private and Photoprism brings
> its own login. `/__auth` and `/__lan` are kept for the first service that needs
> one, and the recipes below are the Traefik-behind-the-tunnel shape.

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
          - url: "http://100.64.0.10:8080"    # the device's Tailscale IP
```

`GET /__auth` answers that middleware. A signed-in visitor gets 200 and the
request proceeds; anyone else gets a 302 to the portal login and is returned to
where they were going. Attach the middleware only to the routers that need it —
services with their own login work fine without it.

Two environment variables are required for this, both in
[`.env.example`](../.env.example):

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
          - url: "http://100.64.0.10:8088"
```

No session is needed for this one: it is a question about where you are, not who
you are. Chain it with `portal-auth` when you want both — `middlewares:
[home-only, portal-auth]` requires being at home *and* signed in.

`/__lan` answers from `portal.trusted-networks`, so it cannot tell one site from
another — everything gated this way is gated to the same set of networks. Tiles can
now tell the houses apart via `portal.networks`, but this probe still answers a
plain yes/no. Worth understanding before gating a hostname to one house rather than
to "any of mine". See [Two houses, one tile](#two-houses-one-tile).

Nothing attaches this middleware today. As above, `cloudflared` cannot call a
forward-auth probe by itself — this shape needs a proxy behind the tunnel.

### Two houses, one tile

Each house has its own dashboard box, and one tile serves both. Name the two houses
and that is the whole configuration — a named group counts as one of your own
networks on its own, so it decides both *whether* the tile is live and *which* box
it points at:

```properties
HOME_NETWORKS=<home public address>/32
BEACH_NETWORKS=<beach public address>/32
```

`TRUSTED_NETWORKS` is still there for somewhere that is not one of the named houses,
and it is fine to leave empty. It used to be required *as well*, which was a trap:
replacing it with the two named groups — the obvious reading — greyed out every
`lanOnly` tile in both houses, because the tile gated on the combined list while the
url resolved from the groups. Naming a house now implies trusting it.

```yaml
    - label: Dashboard
      icon: bi-speedometer2
      color: teal
      lanOnly: true
      urls:
        home: http://192.168.1.23:8088
        beach: http://192.168.1.210:8088
```

These are the **public** addresses of each house, not their `192.168` ranges: a
visitor loads `gehan.cloud` over the internet, so they arrive looking like their
router. `/users` shows an admin the address they arrived from and now names the
group it matched, which is the quickest way to fill these in — and to notice when a
residential address has rotated.

Groups are matched in configured order, first match wins. A tile whose `urls` has
no entry for your group falls back to its plain `url`, and a `lanOnly` tile with
neither renders greyed out with no href — so one house's private address is never
in the page you are served in the other.

**This replaced a stricter arrangement**, worth knowing about if you see traces of
it. Both boxes used to answer at the same address, `192.168.1.23:8088`, so one
tile with one url served both — the address resolved to whichever house you stood
in. It worked, but it forced both boxes onto the same address, the same port, and
the same LAN range. The beach box was then handed a different DHCP lease, nothing
answered at `.23` there any more, and the tile became a live link to nothing.
Per-network urls remove the constraint: each house names its own address, and they
need not agree.

The greying matters more here than usual. `192.168.1.x` is the most common home
network there is, so on someone else's wifi those addresses are quite likely to be
a real device — a printer, a router page, a camera. `lanOnly` is what keeps the
tile from being a live link to a stranger's hardware.

One thing still needs a hand: a box on DHCP can be moved again, and the url here
would go stale. A reservation on the router is the usual fix; failing that, some
routers resolve their DHCP clients by hostname, in which case a name like
`http://beachserver:8088` follows the box on its own.

### Which address the app believes

`TRUSTED_NETWORKS` is only as good as the address it is compared against, and
behind Cloudflare the obvious one is wrong.

Spring's `ForwardedHeaderFilter` resolves `getRemoteAddr()` to the **first** entry
of `X-Forwarded-For`. That is right for a proxy that owns the header. Cloudflare
does not own it: the edge *appends* the real address to whatever the visitor sent,
so a request carrying `X-Forwarded-For: 203.0.113.9` arrives as

```
X-Forwarded-For: 203.0.113.9, <the visitor's real address>
```

and the first entry — the one the app would read — is the visitor's own invention.
Anyone could claim to be standing in either house.

`CF-Connecting-IP` is written by the edge on every request and cannot be supplied
by the visitor. Setting `CLIENT_IP_HEADER=CF-Connecting-IP` reads that instead,
falling back to the remote address when the header is absent so a direct hit on
the LAN still resolves sensibly. That is what [`ClientAddress`](../src/main/java/cloud/gehan/security/ClientAddress.java)
does, and `.env.example` sets it.

It holds only while the tunnel is the sole route to the container: anything that
can open a socket to port 8080 can send the header itself. Which is why
[`docker-compose.yml`](../docker-compose.yml) publishes no port.

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


