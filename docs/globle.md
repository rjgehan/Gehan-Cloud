# Globle

A geography game at `/globle`, after [globle-game.com](https://globle-game.com).
Guess a country, and it lights up on a globe coloured by how close it is to the
mystery country: pale for the far side of the world, deep red for next door, green
when you find it. For what the rest of the app is, see the [README](../README.md).

The game is an optional guest in this codebase. It can be switched off with one
property, and deleted with the checklist at the end of this page, without touching
anything the launcher depends on.

## Modes

| Mode | The mystery | Counts toward |
| --- | --- | --- |
| **Daily** | One country per day, the same for everybody. A new one at midnight in `globle.zone`. | Streaks, stats, the Today / Week / All time boards |
| **Unlimited** | A random country whenever you like, optionally inside one region: world, Africa, Americas, Asia, Europe, Oceania. Never the same country twice in a row. | The Unlimited board (Countries) |
| **Capitals** | A random capital city, with the same region filter, never the same one twice in a row. You guess capitals ("Paris — France"); distance is city to city, and each guess is a dot on the globe. | The Unlimited board (Capitals) |
| **Blind** | Not a mode but a switch on top of any of them: the globe draws only sea, graticule and the places you have guessed. Purely cosmetic, remembered per device. | Nothing |

Rules shared by all of them:

- Guesses are unlimited (the server stops a game at `globle.max-guesses`, 400, purely
  as a guard). Guessing the same place twice is refused, not counted.
- Each guess shows its distance, and the list is sorted closest first.
- Giving up reveals the answer and counts as played, not won.
- For countries, distance is the **shortest gap between the two borders**, so every
  neighbour of the answer is 0 km and deep red without being the answer. For
  capitals it is the great-circle distance between the two cities.
- Territories (Greenland, Puerto Rico, ...) can be guessed but are never the answer.
  The 197 possible answers are the 193 UN members plus Vatican City, Palestine,
  Taiwan and Kosovo.

An Unlimited or Capitals game in progress resumes on reload and on any other
device. Starting a new one while one is in progress records the old one as given
up, so a bad start cannot be quietly discarded; the page warns before it does that.
A game with no guesses yet is simply dropped, since nothing about its answer was
shown.

## How it works

```
 browser                                           server (cloud.gehan.globle)
 -------                                           ---------------------------
 templates/globle/index.html  <- GET /globle ----- GloblePageController   (main security chain)
 world.json  <- GET /globle/world.json             static file, 726 KiB TopoJSON
 main.js     -- GET/POST /globle/api/** ---------> GlobleApiController    (Globle's own chain)
                                                     GameService          rules, one write at a time
                                                     DailyPuzzles         the day's answer, drawn once
                                                     GameViews            distances; hides the answer
                                                     LeaderboardService   counted in memory
                                                     CountryCatalog       countries.json + distances.json
                                                   globle_day, globle_game  (SQLite, beside users)
```

**The server is the source of truth.** Every guess, give-up and new game returns
the whole game as the server now sees it, and the page redraws from that. A reload,
a second device or a midnight rollover cannot leave the screen disagreeing with the
database.

**The daily answer is drawn, not derived.** The obvious design is a seeded shuffle
of the date. In a public repository that is a spoiler anyone can run. Instead
`DailyPuzzles` draws a country with `SecureRandom` the first time a date is needed
(the day's first guess or give-up, by anyone), stores it in `globle_day`, and
everyone uses that row. Looking at the page stores nothing. The draw skips
countries used within `globle.answer-repeat-window-days` (60) either side of the
date, but never more than a third of the 197 answers (65), whatever is configured.
Every finished game reveals its answer, so a long window would be a clue: a 365-day
one would leave a single candidate on day 197 and fifteen on every day after, for
anyone keeping a list. Capped at a third, the day's pool is always over a hundred
countries.

**The answer never leaves the server mid-game.** `GameViews` attaches it only when
a game is WON or GAVE_UP. Tests assert that the answer's code appears nowhere in an
in-progress response for any mode, nor in the rendered page. The country list the
autocomplete uses does say which places can be the answer (`canBeAnswer`), so the
list can mark territories as "Never the answer". That is no clue: the rule above is
public.

**Distances are precomputed.** Border-to-border distance is expensive to compute
and impossible to approximate well with centre points: Russia's middle is nowhere
near Finland. So `tools/globle` measures every pair of Natural Earth shapes once,
counting only the parts the globe draws, and the server loads the 248 × 248
matrix at startup. It refuses to start if the matrix and the country list
disagree. Capital distances are cheap and computed on the fly with the haversine
formula.

**One write at a time.** Every Globle write runs in one transaction under a single
app-wide lock, so a double-tapped guess lands once and there is at most one practice
game in progress per kind. The lock is app-wide, not per player, because SQLite has
one writer at a time: two players' transactions open at once deadlock, and SQLite
reports that with an immediate `SQLITE_BUSY` instead of waiting, which the player
would see as an error. At family scale a guess waits a few milliseconds at most.
`SqliteWritesTest` checks this against a real SQLite file, since the rest of the
suite runs on H2. One app instance is assumed. The unique `daily_key` column and
`globle_day`'s primary key are the backstop if that ever changes.

### Security

- The page, its scripts and `world.json` sit under the app's main security chain,
  so signing in works as it does for the launcher. They need a session, and
  a signed-out visitor is redirected to the login page.
- The static files (`world.json`, scripts, styles) are served `no-cache, private`
  with an ETag of their content (`GlobleStaticCaching`): the browser keeps them and
  revalidates each visit, getting a 304 until a deploy changes one, and no shared
  cache stores them. The page and the API stay `no-store`.
- `/globle/api/**` has its own filter chain, `GlobleSecurityConfig`, ordered ahead
  of the main one. It shares the session but answers a signed-out request with
  `401 {"error":"signed-out"}` instead of a redirect, so the page can say "session
  expired" rather than parse the login form as data.
- CSRF stays on. The page renders Spring Security's token into
  `<meta name="_csrf">` and the script sends it as the `X-CSRF-TOKEN` header on every
  POST. A signed-in POST with a missing or stale token gets
  `403 {"error":"forbidden"}`, which the page treats as "reload".
- API responses carry `Content-Security-Policy: default-src 'none'`. The page gets
  the site's usual CSP (`script-src 'self'`, no inline script, no inline handlers).
  d3 and topojson-client are vendored under `static/globle/vendor` rather than
  loaded from a CDN for the same reason.
- Globle's own errors are `{"error":"<code>"}` and nothing else: no messages, no
  stack traces, never the answer. A path under `/globle/api/` that does not exist
  (404), or a wrong method (405), never reaches Globle's controller. It gets
  Spring Boot's standard error body (`timestamp`, `status`, `error`, `path`),
  which the app's `server.error.*` settings keep free of messages and traces.
- The boards are on the honour system. The answer cannot be known before
  playing, but distances are exact whole km and `distances.json` is public, so
  one guess plus a lookup in the guess's row identifies the answer. In Capitals,
  the capital coordinates that `/globle/api/countries` sends are enough on their
  own. Coarser distance bands would raise the bar, at the cost of how the game
  feels.

### API

All JSON, all requiring a signed-in session. POSTs need the CSRF header.

| Method | Path | Returns |
| --- | --- | --- |
| `GET` | `/globle/api/countries` | Every guessable place: code, name, flag code, aliases, region, whether it can be the answer, capital |
| `GET` | `/globle/api/daily` | Today's game for you (playing with no guesses if you have not started) |
| `POST` | `/globle/api/daily/guess` `{"code":"FRA","date":"2026-10-09"}` | The game after the guess |
| `POST` | `/globle/api/daily/give-up` `{"date":"2026-10-09"}` | The game, finished, with the answer |
| `GET` | `/globle/api/unlimited/current?kind=country\|capital` | Your practice game in progress, or `204` |
| `POST` | `/globle/api/unlimited/new` `{"kind":"country","region":"europe"}` | A new practice game |
| `POST` | `/globle/api/unlimited/{id}/guess` `{"code":"FRA"}` | The game after the guess (for capitals, the code is the capital's country) |
| `POST` | `/globle/api/unlimited/{id}/give-up` | The game, finished, with the answer |
| `GET` | `/globle/api/leaderboard` | Today, Week, All time, Unlimited, and your own stats |

The daily POSTs' `date` (`yyyy-MM-dd`) is optional: it is the puzzle the page is
showing, so a tab left open across midnight cannot guess or give up on a puzzle the
player has not seen. Without it the move applies to today.

Refusals: `400` with `unknown-country`, `already-guessed`, `finished`, `no-capital`
or `bad-request`; `404 not-found` for a game id that is not yours; `409 new-day`
for a daily move whose `date` is no longer today (nothing is stored; load the new
puzzle); `401 signed-out`; `403 forbidden`. An unexpected failure is logged and answered `500
server`.

## Leaderboard and streak rules

Only the daily counts toward streaks and the Today / Week / All time boards.

| | Rule |
| --- | --- |
| **Played** | A daily you won or gave up, or left unfinished on a past date. Today's game still in progress does not count until it ends, so win rates do not dip with every first guess. Giving up before guessing still counts. |
| **Today** | Everyone who has guessed or given up today. Winners first, by fewest guesses and then earliest finish. Equal guess counts share a rank (1, 1, 3). Then those still playing, then those who gave up. Shows guess counts only, never countries, so it is visible while you are still playing. |
| **Week** | The last seven puzzle dates, today included. Sorted by wins, then lower average. |
| **All time** | Sorted by wins, then win rate, then lower average. Shows current and best streak and fewest guesses. |
| **Average guesses** | Over wins only. |
| **Current streak** | Consecutive puzzle dates won, ending today, or ending yesterday while today is unplayed or still in progress. So a streak survives until midnight, but a give-up today ends it at once. |
| **Distribution** | Your daily wins by guess count: 1–6, 7–9, 10–14, 15+. |
| **Unlimited** | Finished practice games only, per kind (Countries, Capitals): played, wins, average, best. |
| **Who is listed** | Only accounts that still exist. Games belong to a user id *and* username. On SQLite a deleted account's id can be handed to the next one created, and the newcomer should not inherit a stranger's streak. |

## Configuration

Every setting has a default in `GlobleProperties`, so nothing needs to be set for
the game to run. `application.properties` carries a commented block listing these.

| Property | Default | Purpose |
| --- | --- | --- |
| `globle.enabled` | `true` | `false` switches the game off; see below. |
| `globle.zone` | `America/New_York` | Whose midnight starts the next daily. One zone for the whole family, because the point is that everyone guesses the same country. |
| `globle.epoch` | `2026-10-09` | The date of puzzle #1. Puzzle numbers count days from here. |
| `globle.answer-repeat-window-days` | `60` | A daily answer is not reused within this many days either side. Capped at a third of the answers (65), so the day's pool stays large. |
| `globle.max-guesses` | `400` | A defensive ceiling on guesses per game. |

Like any Spring property these can also come from the environment, e.g.
`GLOBLE_ZONE=Europe/London`.

### Switching it off

`globle.enabled=false` (or `GLOBLE_ENABLED=false`) removes every Globle bean, the
API's filter chain and both controllers. `/globle` and `/globle/api/**` then
answer 404 from the packaged jar, and the rest of the app behaves exactly as it did
before the game existed. `GlobleDisabledTest` boots the app this way and checks
it. Three things it cannot do on its own:

- **The launcher tile stays.** Tiles come from `apps.yml`. Comment out the Globle
  entry there too.
- **The two tables are still created**, empty, while the code is present.
  Hibernate scans every entity in the app's package.
- **The static files stay servable** to signed-in users, as any file under
  `static/` is.

Deleting the code (below) removes all three.

## Storage

Two tables beside `users` in `users.db`, both prefixed `globle_` and created by
Hibernate on startup:

| Table | One row per | Notes |
| --- | --- | --- |
| `globle_day` | Puzzle date | The day's answer code, its puzzle number, when it was drawn. |
| `globle_game` | Game | Owner (`user_id` + `username`, deliberately not a foreign key, so deleting a user is never blocked by their games), mode, kind, region, status, the guesses as a comma-separated list of codes, timestamps. Daily rows have a `puzzle_date` and a unique `daily_key`; practice rows carry their own `answer_code`. |

Distances are not stored with guesses. They are recomputed from the data files on
every response, so a data correction fixes old games too.

The browser keeps a few per-device preferences in `localStorage` under keys starting
`globle.`: last mode, region per mode, blind, km/mi, whether the rules were seen,
last leaderboard tab.

## Data

Three generated files, committed so the build needs no Node:

| File | Size | Used by |
| --- | --- | --- |
| `src/main/resources/globle/countries.json` | 49 KiB | Server: 248 places (codes, names, aliases, answer flag, region, label point, capital) |
| `src/main/resources/globle/distances.json` | 315 KiB | Server: the 248 × 248 border-to-border km matrix |
| `src/main/resources/static/globle/world.json` | 726 KiB | Browser: the globe's shapes (Natural Earth 1:50m, TopoJSON) |

To regenerate (Node 20+):

```bash
cd tools/globle
npm ci
npm run build    # writes the three files, deterministically
npm run check    # validates them; non-zero exit on any failure
```

Then `./mvnw test`: `CountryCatalogTest` loads the real files and checks them
against each other. Keep country codes stable across rebuilds. Stored games refer
to places by code, and a game whose answer code disappeared could not be shown.

Sources, licences and every judgement call the data makes are listed in
[tools/globle/README.md](../tools/globle/README.md). That covers which territories
are split from their sovereign, Crimea and Western Sahara, why Somaliland and
Northern Cyprus are part of Somalia and Cyprus, which islets too small for the
globe are left out of the distances, which capital a country with several gets,
and why Australia–New Zealand is 616 km. Geometry is
[Natural Earth](https://www.naturalearthdata.com/) (public domain).

## Testing

```bash
./mvnw test
```

Everything for the game is under `src/test/java/cloud/gehan/globle/`. The suite
uses a movable clock registered only under the bean name `globleTimeSource`, which
nothing outside Globle looks for. The suite covers the data files, the daily draw,
every API route and refusal, the leaderboard rules across days, the security chain
(including a real form login), the rendered page, simultaneous writes on a real
SQLite file, and the app with the game switched off.

## Removing Globle

Follow this list in order and the game is gone, with the app as it was before it
was added. Every path is from the repository root.

1. **Delete the game's directories and files:**

   ```bash
   rm -rf src/main/java/cloud/gehan/globle \
          src/test/java/cloud/gehan/globle \
          src/main/resources/globle \
          src/main/resources/templates/globle \
          src/main/resources/static/globle \
          tools/globle \
          docs/globle.md
   ```

   `tools/` is empty afterwards. Remove it too if nothing else lives there.

2. **`src/main/resources/apps.yml`:** delete the Globle tile: the entry with
   `label: Globle`, the three comment lines above it and the blank line after it.

3. **`src/main/resources/application.properties`:** delete the block from
   `# --- Globle ---` down to `# globle.max-guesses=400`, and the blank line after
   it.

4. **`.gitignore`:** delete the `### Globle data tooling (tools/globle/README.md) ###`
   line, the `tools/globle/node_modules/` line under it, and the blank line before
   them.

5. **`README.md`:**
   - Delete the whole `## Globle` section, from its heading down to the line before
     `## Architecture`.
   - Delete these lines: `|  /globle  the game (optional)      |` in the
     Architecture box. The three Endpoints rows whose paths start `/globle`. The
     `globle/` and `tools/globle/` lines in Repository layout. The `Globle` row in
     the Documentation table.
   - Put these back as they were. Line breaks included, they read:
     - The intro, from "Spring Boot serves" to "It ships as a container:":

       ```
       Spring Boot serves the launcher, the login, and an admin-only user console. That
       is the whole app — no API, no SPA, no database server. It ships as a container:
       ```

     - Under Endpoints, the line after the table:

       ```
       That is every route. There is no API surface.
       ```

     - Under Security, the lines before the table:

       ```
       Every route is a page behind a session; there is no API surface and no token to
       leak. What that leaves:
       ```

     - The Security table's CSRF row:
       `| CSRF | On. Every state-changing route is a form and carries a token. |`
     - The Stack table's Storage row: `| Storage | SQLite, one table of users |`

6. **Check it:**

   ```bash
   git grep -i globle      # expect no output
   ./mvnw -B clean verify  # expect green; clean drops stale Globle files from target/
   unzip -l target/gehan-cloud-*.jar | grep -i globle   # expect no output
   ```

7. **Optional, on the server:** the two tables are inert once the code is gone.
   Nothing reads them, and Hibernate's `ddl-auto=update` neither uses nor removes
   them. To reclaim the space:

   ```bash
   sqlite3 "$APP_DATA_DIR/users.db" 'DROP TABLE IF EXISTS globle_game; DROP TABLE IF EXISTS globle_day;'
   ```

Browsers that played keep a few `globle.*` keys in `localStorage`. They are
harmless and nothing reads them.
