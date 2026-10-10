# Globle

A geography game at `/globle`, after [globle-game.com](https://globle-game.com).
Guess a country, and it lights up on a globe coloured by how close it is to the
mystery country: pale for the far side of the world, deep red for next door, green
when you find it. For what the rest of the app is, see the [README](../README.md).

The game is an optional guest in this codebase. It can be switched off with one
property, and deleted with the checklist at the end of this page, without touching
anything the launcher depends on.

## Modes

`/globle` opens on a home screen, and the game is chosen there. At the top are two
daily cards, **Classic · Daily #N** and **Hard · Daily #N**. Each shows where you
stand today (Play, In progress · 3 guesses, Solved in 4 ✓, Gave up) and your streak
for that daily. Below them, under "More ways to play", are the practice presets, and
a compact row for the remaining regions.

| Mode | The mystery | Counts toward |
| --- | --- | --- |
| **Classic daily** | One country per day, the same for everybody. A new one at midnight in `globle.zone`. | The classic streak, stats and Today / Week / All time boards |
| **Hard daily** | A second country per day ("Hard #N", same N), the same for everybody, never the day's classic answer, and weighted toward countries few people could place (see [Difficulty](#difficulty)). Blind globe, no suggestions. | Its own streak, stats and boards, separate from classic |
| **Unlimited** | A random country whenever you like, anywhere in the world. | The practice board (Countries) |
| **Europe only**, **Africa**, **Americas**, **Asia**, **Oceania** | The same, inside one region. | Countries |
| **Mainland only** | The same, without the small island nations: Japan and New Zealand stay, Tonga and Malta go (see [Mainland](#mainland)). | Countries |
| **Capitals** | A random capital city. You guess capitals ("Paris — France"); distance is city to city, and each guess is a dot on the globe. | The practice board (Capitals) |
| **Hard practice** | Unlimited rounds played the Hard way: blind globe, no suggestions, weighted toward hard countries. | The practice board (Hard) |

**The globe.** In every mode except Hard, all land is drawn as one shape with its
coastline and nothing else: no country borders, so the outline of an unguessed
country gives nothing away. A guessed country is filled in its distance colour, with
at most a hairline of that same colour to close the seam between two guessed
neighbours. In **Hard** the globe is blind: sea and a faint graticule only, with each
guess filled in where it lies. When a game ends the answer appears, solid green if
you found it and hatched if you gave up. A finished Hard game also fades the land in,
still with no borders, so you can see where the answer is.

**Hard input.** Hard has no dropdown. The box is plain text with autocomplete,
autocorrect and spellcheck off, and Enter submits. The text must match a place's
name or one of its aliases exactly, ignoring case, accents and punctuation ("ivory
coast", "CÔTE D'IVOIRE"). Anything else gets "Not recognised — check the spelling",
with no suggestions, and nothing is sent. The list of guesses still shows names and
distances.

Rules shared by all modes:

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

**Practice slots.** Each preset is its own slot, identified by (kind, region,
variant): Europe, Mainland, Capitals and Hard practice each keep their own game in
progress. Picking a preset with nothing in progress starts a game at once. A game
resumes on reload and on any other device, and once it has a guess the home screen
marks it "Resume · 3 guesses". Starting a new game abandons only the game in the
same slot. With guesses it is recorded as given up, so a bad start cannot be quietly
discarded, and the page warns first. With no guesses it is simply dropped, since
nothing about its answer was shown.

**Getting around.** The page uses hash routes, so the browser's and the phone's back
button return home and a reload stays where it was: `#/` is home, `#/classic` and
`#/hard` are the dailies, and `#/play/<preset>` is a practice game, where preset is
`world`, `europe`, `africa`, `americas`, `asia`, `oceania`, `mainland`, `capitals` or
`hard`. The game view has a Home button and a badge naming the game ("Hard · Daily
#3", "Europe only"). After the classic daily, the end card offers "Play today's Hard
→". A first visit shows one sentence, the colour legend and a Play button. The ?
sheet has a line per mode.

## How it works

```
 browser                                           server (cloud.gehan.globle)
 -------                                           ---------------------------
 templates/globle/index.html  <- GET /globle ----- GloblePageController   (main security chain)
 world.json  <- GET /globle/world.json             static file, 726 KiB TopoJSON
 main.js     -- GET/POST /globle/api/** ---------> GlobleApiController    (Globle's own chain)
                                                     GameService          rules, one write at a time
                                                     DailyPuzzles         each day's two answers, drawn once
                                                     AnswerPools          who can be drawn, and how likely
                                                     HomeService          the home screen's summary
                                                     GameViews            distances; hides the answer
                                                     LeaderboardService   counted in memory
                                                     CountryCatalog       countries.json + distances.json
                                                   globle_day, globle_hard_day, globle_game
                                                   (SQLite, beside users)
```

**The server is the source of truth.** Every guess, give-up and new game returns
the whole game as the server now sees it, and the page redraws from that. A reload,
a second device or a midnight rollover cannot leave the screen disagreeing with the
database.

**The daily answers are drawn, not derived.** The obvious design is a seeded shuffle
of the date. In a public repository that is a spoiler anyone can run. Instead
`DailyPuzzles` draws a country with `SecureRandom` the first time a date is needed
(the day's first guess or give-up, by anyone), stores it, and everyone uses that row.
The classic answer goes in `globle_day` and the Hard one in `globle_hard_day`.
Looking at the page or the home screen stores nothing.

Each draw skips countries that variant used within `globle.answer-repeat-window-days`
(60) either side of the date, but never more than a third of its pool, whatever is
configured: 65 of the 197 classic answers, and 55 of the 167 Hard ones. Every
finished game reveals its answer, so a long window would be a clue. A 365-day one
would leave a single candidate on day 197 and fifteen on every day after, for anyone
keeping a list. Capped at a third, the day's pool always holds two thirds of the
answers.

The Hard answer is never the day's classic answer. The first Hard move of a date
first settles the classic answer, drawing and storing it if nobody has played
classic yet, and then leaves it out. The two can never be drawn side by side and
collide. That reveals nothing: the classic answer is drawn exactly as it would have
been at the first classic guess.

**The answer never leaves the server mid-game.** `GameViews` attaches it only when
a game is WON or GAVE_UP. Tests assert that the answer's code appears nowhere in an
in-progress response for any mode, nor in the rendered page, the home summary or the
leaderboard. The country list the autocomplete uses does say which places can be the
answer (`canBeAnswer`), so the list can mark territories as "Never the answer". That
is no clue: the rule above is public. A country's difficulty tier and small-island
flag stay on the server.

**Distances are precomputed.** Border-to-border distance is expensive to compute
and impossible to approximate well with centre points: Russia's middle is nowhere
near Finland. So `tools/globle` measures every pair of Natural Earth shapes once,
counting only the parts the globe draws, and the server loads the 248 × 248
matrix at startup. It refuses to start if the matrix and the country list
disagree. Capital distances are cheap and computed on the fly with the haversine
formula.

**One write at a time.** Every Globle write runs in one transaction under a single
app-wide lock, so a double-tapped guess lands once and there is at most one practice
game in progress per slot. The lock is app-wide, not per player, because SQLite has
one writer at a time: two players' transactions open at once deadlock, and SQLite
reports that with an immediate `SQLITE_BUSY` instead of waiting, which the player
would see as an error. At family scale a guess waits a few milliseconds at most.
`SqliteWritesTest` checks this against a real SQLite file, including a classic and a
Hard daily started at the same moment, since the rest of the suite runs on H2. One
app instance is assumed. The unique `daily_key` column and the day tables' primary
keys are the backstop if that ever changes.

### Difficulty

Every answer country has a hand-picked difficulty tier from 1 to 5. The question
behind it is how readily a typical player thinks of the country and finds it on a
blind globe:

| Tier | Meaning | Examples | Answers | Hard weight | Share of one Hard draw |
| --- | --- | --- | --- | --- | --- |
| 1 | household names anyone can point to | USA, China, France, Egypt | 30 | 0 | never |
| 2 | well known, and most adults can place them | Netherlands, Iran, Kenya | 40 | 1 | 5.4% |
| 3 | familiar names in a busier part of the map | Hungary, Oman, Ghana | 45 | 3 | 18.2% |
| 4 | heard of but rarely guessed | Baltics, Caucasus, Central Asia, West Africa | 45 | 6 | 36.4% |
| 5 | obscure | microstates, small island nations, Eswatini, Djibouti, Bhutan | 37 | 8 | 39.9% |

Classic modes ignore the tiers and draw evenly. Hard draws by weight: a tier-5
country is eight times as likely as a tier-2 one, and tier 1 never comes up. That
leaves 167 of the 197 answers. Each practice round gets the table's shares. The Hard
daily also skips its own answers from the last 55 days, and those are mostly tier 4
and 5. Over time that flattens the daily to about 8% tier 2, 23% tier 3, 35% tier 4
and 34% tier 5. A region filter applies before the weights, so a Hard game in Europe
gets Europe's hardest.

To retune:

- **The weights** are configuration: `globle.hard-weights.1` to `.5` (see
  [Configuration](#configuration)). Set any tier to 0 to leave it out. At least one
  tier must stay above 0, and the app refuses to start otherwise.
  `tools/globle/check-data.mjs` keeps its own copy of the default weights and
  repeat window. With them it prints the single-draw shares above and the Hard
  daily's long-run mix, simulated day by day. Change the copy too if the defaults
  change.
- **A country's tier** is data. The tiers are the `DIFFICULTY` lists in
  `tools/globle/lib/catalog.mjs`, one list per tier, a line per region. Move a code
  between lists, then run `npm run build` and `npm run check` (see [Data](#data)) and
  `./mvnw test`. The build fails if an answer is missing from the tiers or listed
  twice. The rubric is in
  [tools/globle/README.md](../tools/globle/README.md#catalog-choices).

### Mainland

"Mainland only" leaves out the **small island nations**. An answer country is one
when all three of these hold:

- None of its land is on a continental mainland (Afro-Eurasia, the Americas,
  Australia), so it is islands only.
- Its land area is under 30,000 km².
- It has no land border with another answer country.

The rule is applied by `tools/globle` from the 10m Natural Earth shapes and stored
as each country's `smallIsland` flag. The result is 31 small island nations, leaving
166 answers in the Mainland pool. They are the Pacific and Caribbean island states,
plus Malta, Cyprus, Bahrain, Singapore, the Maldives, Mauritius, Seychelles, Comoros,
São Tomé and Príncipe and Cabo Verde. Large islands stay in: Japan, the UK, Ireland,
Iceland, Cuba, New Zealand, Madagascar, Sri Lanka, the Philippines, Indonesia,
Taiwan. So do countries that share their island with a neighbour: Haiti, the
Dominican Republic, Papua New Guinea, Timor-Leste, Brunei. Mainland microstates such
as Andorra and Vatican City stay in too. `npm run check` asserts the exact list.

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
`variant` is `classic` or `hard` wherever it appears. It is optional and means
`classic` when left out, so every request shape from before Hard keeps its meaning.
`region` is `world`, `africa`, `americas`, `asia`, `europe`, `oceania` or `mainland`.

| Method | Path | Returns |
| --- | --- | --- |
| `GET` | `/globle/api/countries` | Every guessable place: code, name, flag code, aliases, region, whether it can be the answer, capital |
| `GET` | `/globle/api/home` | Today's puzzle number, date and next midnight; for each daily (`classic`, `hard`) its `status` (`NEW`, `PLAYING`, `WON`, `GAVE_UP`), `guessCount` and `currentStreak`; and `inProgress`, your practice games in progress (`id`, `kind`, `region`, `variant`, `guessCount`), newest first. Stores nothing. |
| `GET` | `/globle/api/daily?variant=hard` | Today's game of that daily for you (playing with no guesses if you have not started) |
| `POST` | `/globle/api/daily/guess` `{"code":"FRA","date":"2026-10-09","variant":"hard"}` | The game after the guess |
| `POST` | `/globle/api/daily/give-up` `{"date":"2026-10-09","variant":"hard"}` | The game, finished, with the answer |
| `GET` | `/globle/api/unlimited/current?kind=country&region=europe&variant=classic` | Your game in progress in that slot, or `204`. Without `region`, your newest game in progress of that kind and variant in any region, which is how this answered before slots existed. |
| `POST` | `/globle/api/unlimited/new` `{"kind":"country","region":"mainland","variant":"classic"}` | A new practice game. A game in progress in the same slot is given up (or dropped, with no guesses). |
| `POST` | `/globle/api/unlimited/{id}/guess` `{"code":"FRA"}` | The game after the guess (for capitals, the code is the capital's country) |
| `POST` | `/globle/api/unlimited/{id}/give-up` | The game, finished, with the answer |
| `GET` | `/globle/api/leaderboard?variant=hard` | That daily's Today, Week, All time and your own stats (`variant` says which), and all three practice boards under `unlimited`: `country`, `capital`, `hard` |

Every game carries `variant` too. Hard is countries only: Hard capitals is refused.

The daily POSTs' `date` (`yyyy-MM-dd`) is optional: it is the puzzle the page is
showing, so a tab left open across midnight cannot guess or give up on a puzzle the
player has not seen. Without it the move applies to today.

Refusals: `400` with `unknown-country`, `already-guessed`, `finished`, `no-capital`
or `bad-request` (also for an unknown `kind`, `region` or `variant`, and for Hard
capitals); `404 not-found` for a game id that is not yours; `409 new-day` for a daily
move whose `date` is no longer today (nothing is stored; load the new puzzle);
`401 signed-out`; `403 forbidden`. An unexpected failure is logged and answered
`500 server`.

## Leaderboard and streak rules

Only the dailies count toward streaks and the Today / Week / All time boards. Classic
and Hard are separate puzzles, each with its own boards, streak and distribution. The
leaderboard and stats sheets switch between them with a Classic | Hard toggle.

| | Rule |
| --- | --- |
| **Played** | A daily you won or gave up, or left unfinished on a past date. Today's game still in progress does not count until it ends, so win rates do not dip with every first guess. Giving up before guessing still counts. |
| **Today** | Everyone who has guessed or given up today. Winners first, by fewest guesses and then earliest finish. Equal guess counts share a rank (1, 1, 3). Then those still playing, then those who gave up. Shows guess counts only, never countries, so it is visible while you are still playing. |
| **Week** | The last seven puzzle dates, today included. Sorted by wins, then lower average. |
| **All time** | Sorted by wins, then win rate, then lower average. Shows current and best streak and fewest guesses. |
| **Average guesses** | Over wins only. |
| **Current streak** | Consecutive puzzle dates won, ending today, or ending yesterday while today is unplayed or still in progress. So a streak survives until midnight, but a give-up today ends it at once. |
| **Distribution** | Your daily wins by guess count: 1–6, 7–9, 10–14, 15+. |
| **Unlimited** | Finished practice games only, in three boards: Countries (classic country games in every region, Mainland included), Capitals, and Hard (Hard practice). Played, wins, average, best. |
| **Who is listed** | Only accounts that still exist. Games belong to a user id *and* username. On SQLite a deleted account's id can be handed to the next one created, and the newcomer should not inherit a stranger's streak. |

## Configuration

Every setting has a default in `GlobleProperties`, so nothing needs to be set for
the game to run. `application.properties` carries a commented block listing these.

| Property | Default | Purpose |
| --- | --- | --- |
| `globle.enabled` | `true` | `false` switches the game off; see below. |
| `globle.zone` | `America/New_York` | Whose midnight starts the next dailies. One zone for the whole family, because the point is that everyone guesses the same country. |
| `globle.epoch` | `2026-10-09` | The date of puzzle #1. Puzzle numbers count days from here, the same for Classic and Hard. |
| `globle.answer-repeat-window-days` | `60` | A daily answer is not reused within this many days either side, per daily. Capped at a third of that daily's pool (65 classic, 55 Hard), so the day's pool stays large. |
| `globle.hard-weights.1` … `.5` | `0`, `1`, `3`, `6`, `8` | How strongly Hard's draw favours each difficulty tier; `0` means never. Set one tier and the others keep their defaults. Weights are 0–1000, and at least one must be above 0. |
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
- **The three tables are still created**, empty, while the code is present.
  Hibernate scans every entity in the app's package.
- **The static files stay servable** to signed-in users, as any file under
  `static/` is.

Deleting the code (below) removes all three.

## Storage

Three tables beside `users` in `users.db`, all prefixed `globle_` and created by
Hibernate on startup:

| Table | One row per | Notes |
| --- | --- | --- |
| `globle_day` | Puzzle date | The day's classic answer code, its puzzle number, when it was drawn. |
| `globle_hard_day` | Puzzle date | The same for the Hard daily. |
| `globle_game` | Game | Owner (`user_id` + `username`, deliberately not a foreign key, so deleting a user is never blocked by their games), mode, kind, region, `variant` (`CLASSIC` or `HARD`), status, the guesses as a comma-separated list of codes, timestamps. Daily rows have a `puzzle_date` and a unique `daily_key`: `userId/username/date` for classic, the same plus `/HARD` for Hard. Practice rows carry their own `answer_code`. |

Distances are not stored with guesses. They are recomputed from the data files on
every response, so a data correction fixes old games too.

**Why Hard has its own day table.** `globle_day` is keyed by the date alone, and
`ddl-auto=update` can add tables and columns but never change a primary key, so that
table can never hold two answers for one date. A second table needs nothing done by
hand. It also keeps the date as the key, which is what makes the first draw of a day
race-safe: two inserts for one date cannot both succeed.

### Upgrading from the first release

The first release (commit `59ea8d8`) created `globle_day` and `globle_game` without
Hard. On the first start of this version Hibernate adds a nullable `variant` column
to `globle_game` and creates `globle_hard_day`. Nothing else in the schema changes,
and nothing rewrites existing rows:

- A row with no `variant` is a classic game. Every lookup reads a missing variant
  that way. Old daily rows keep their `daily_key`, which is still the classic format,
  so they still hold their date.
- Streaks, boards and distributions carry straight on.
- A practice game left in progress resumes in its preset's slot. An old Capitals
  game started in a region resumes under Capitals, labelled "Capitals · Europe" for
  example.

`LiveDatabaseUpgradeTest` boots the app on a SQLite file with the first release's
exact schema and rows and checks all of this.

**Rolling back to `59ea8d8`** needs the database cleaned first, once anyone has
played Hard or Mainland. The old version expects one daily row per player per date
and one practice game in progress per kind, and it fails on a region it does not
know. With the app stopped:

```bash
sqlite3 "$APP_DATA_DIR/users.db" "
  DELETE FROM globle_game WHERE variant = 'HARD';
  UPDATE globle_game SET region = 'WORLD' WHERE region = 'MAINLAND';
  UPDATE globle_game SET status = 'GAVE_UP', finished_at = CAST(strftime('%s','now') AS INTEGER) * 1000
   WHERE mode = 'UNLIMITED' AND status = 'PLAYING' AND id NOT IN
         (SELECT max(id) FROM globle_game WHERE mode = 'UNLIMITED' AND status = 'PLAYING'
          GROUP BY user_id, username, kind);"
```

This deletes Hard history and gives up all but each player's newest practice game
per kind. The extra column and `globle_hard_day` are harmless to the old version.

### In the browser

The browser keeps a few per-device preferences in `localStorage` under keys starting
`globle.`: km or mi (`units`), whether the welcome was seen (`seenHelp`), and the
leaderboard's and stats sheet's last choices (`boardTab`, `boardKind`,
`boardVariant`, `statsVariant`). Keys from the first release (`mode`, `region.*`,
`blind`) are no longer read.

## Data

Three generated files, committed so the build needs no Node:

| File | Size | Used by |
| --- | --- | --- |
| `src/main/resources/globle/countries.json` | 58 KiB | Server: 248 places (codes, names, aliases, answer flag, region, difficulty, small-island flag, label point, capital) |
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
against each other. The server refuses to start on a `countries.json` without a
difficulty and small-island flag for every place. Keep country codes stable across
rebuilds. Stored games refer to places by code, and a game whose answer code
disappeared could not be shown.

Sources, licences and every judgement call the data makes are listed in
[tools/globle/README.md](../tools/globle/README.md). That covers which territories
are split from their sovereign, Crimea and Western Sahara, why Somaliland and
Northern Cyprus are part of Somalia and Cyprus, which islets too small for the
globe are left out of the distances, which capital a country with several gets,
why Australia–New Zealand is 616 km, every difficulty tier, how small island
nations are found, and how the build keeps land borders out of the drawn
coastline. Geometry is [Natural Earth](https://www.naturalearthdata.com/)
(public domain).

## Testing

```bash
./mvnw test
```

Everything for the game is under `src/test/java/cloud/gehan/globle/`. The suite
uses a movable clock registered only under the bean name `globleTimeSource`, which
nothing outside Globle looks for. It covers:

- the data files, the difficulty tiers and the Mainland pool;
- both daily draws, including that Hard never repeats the classic answer and draws
  from its tiers in the configured proportions;
- every API route and refusal, in both variants and every practice slot;
- the home summary, and the leaderboard rules across days, per variant;
- rows written by the first release (`LegacyRowsTest`), and booting on its schema
  (`LiveDatabaseUpgradeTest`);
- the security chain (including a real form login) and the rendered page;
- simultaneous writes on a real SQLite file, and the app with the game switched off.

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

7. **Optional, on the server:** the three tables are inert once the code is gone.
   Nothing reads them, and Hibernate's `ddl-auto=update` neither uses nor removes
   them. To reclaim the space:

   ```bash
   sqlite3 "$APP_DATA_DIR/users.db" 'DROP TABLE IF EXISTS globle_game; DROP TABLE IF EXISTS globle_day; DROP TABLE IF EXISTS globle_hard_day;'
   ```

Browsers that played keep a few `globle.*` keys in `localStorage`. They are
harmless and nothing reads them.
