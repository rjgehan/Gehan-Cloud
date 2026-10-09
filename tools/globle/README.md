# Globle data tooling

Builds the three data files the Globle game ships with. Nothing here runs as
part of the Maven build or at runtime: the outputs are committed, and this
directory exists so they can be regenerated and audited.

| File | What it holds | Used by |
| --- | --- | --- |
| `src/main/resources/globle/countries.json` | 248 countries and territories: code, name, flag code, aliases, whether it can be an answer, region, a label point, capital | server |
| `src/main/resources/globle/distances.json` | 248×248 matrix of minimum great-circle km between every pair (0 = they touch) | server |
| `src/main/resources/static/globle/world.json` | TopoJSON, one `countries` object, `id` = code, `properties.name` | browser |

## Regenerating

Needs Node 20 or newer.

```sh
cd tools/globle
npm ci            # exact versions from package-lock.json
npm run build     # writes the three files (a few seconds; uses all cores)
npm run check     # validates them; exits non-zero on any failure
```

The build is deterministic, so a rebuild on unchanged inputs gives no diff.
Edit `lib/catalog.mjs` to rename a country, add an alias or move a capital;
edit `lib/sources.mjs` to change how Natural Earth features become countries.

## Sources and licences

| Source | Used for | Licence |
| --- | --- | --- |
| [Natural Earth](https://www.naturalearthdata.com/) admin-0 countries, 1:10m and 1:50m, via [`world-atlas`](https://github.com/topojson/world-atlas) 2.0.2 | all geometry | public domain (package ISC) |
| [`i18n-iso-countries`](https://github.com/michaelwittig/node-i18n-iso-countries) | ISO numeric → alpha-3/alpha-2 codes, extra English aliases | MIT |
| `lib/catalog.mjs`, written by hand | display names, aliases, regions, answer set, capitals and their coordinates | part of this repo |
| `d3-geo`, `topojson-client`, `topojson-server`, `polylabel` | geometry code only | ISC |
| [`world-countries`](https://github.com/mledoze/countries) 5.1.0 (dev only) | **cross-checks in `check-data.mjs` only** — borders, UN membership, capital names | ODbL-1.0 |

`world-countries` is under the ODbL, a share-alike *database* licence. It is
deliberately only read by the checker and nothing from it is copied into the
generated files, so they carry no ODbL obligations. Keep it that way: if you
ever want to take data from it, the output files would become ODbL derivative
databases.

## How the files are made

**Codes.** ISO 3166-1 alpha-3 where one exists; Kosovo, which has none, gets
the stable made-up code `XKX`. `iso2` is the alpha-2 for flag emoji, `XK` for
Kosovo, `null` where there is none.

**Answers.** The 193 UN members plus Vatican City, Palestine, Taiwan and
Kosovo — 197 in all. The other 51 entries (territories and Antarctica) can be
guessed but never come up as the answer.

**Distances** are measured on the 10m pieces of each country that the globe
actually draws: a 10m polygon counts when it lies within 25 km of the drawn
50m shape (a drawn polygon with no 10m counterpart that close, such as the
Chesterfield Islands in New Caledonia, is measured as drawn). Islets the 50m
globe leaves out no longer count, so no colour comes from land the player
cannot see: Bouvet Island, Isla de Aves, San Andrés and Providencia, the
Kermadec Islands, Minamitorishima, Agaléga, Aldabra, the Îles Éparses and
Minicoy among them.

Every vertex becomes a unit vector in 3-D, edges are densified so no two
points are more than 2 km apart along the great circle, and the closest pair
between two countries is found with a dual k-d tree search (`lib/sphere.mjs`).
Error is under ~2 km; pairs that share a border vertex, or where one lies
inside the other, are 0; everything else is rounded and floored at 1 km so 0
always means "touching". Working in 3-D makes the antimeridian a non-issue
(Russia/USA across the Bering Strait comes out at 72 km; the Diomedes, 4 km
apart, are too small for the globe to draw).

**Label points** (`lat`/`lng`) are the pole of inaccessibility of each
country's largest 10m polygon, found with polylabel in an azimuthal
equidistant projection centred on that polygon (`lib/label.mjs`). That keeps
the point on the main landmass: contiguous USA, mainland Norway, Viti Levu for
Fiji, Kiritimati for Kiribati, Borneo for Indonesia.

**world.json** is the 1:50m geometry, re-encoded at the same 1e5
quantisation it came in. 1:10m is used instead for Tuvalu and Gibraltar (absent
from 50m) and for Pitcairn and Turks and Caicos (50m leaves out the islands
their capitals are on). Each country's pieces are merged with `mergeArcs`,
so nothing folded into a country draws a border through it.

## Choices that differ from Natural Earth

Natural Earth's defaults are used except where the game needs otherwise:

- **Overseas regions split out** so they are not part of their sovereign's
  shape: French Guiana, Guadeloupe, Martinique, Réunion and Mayotte from France
  (so France is not Brazil's neighbour); Svalbard and Jan Mayen from Norway;
  Bonaire, Sint Eustatius and Saba (`BES`) from the Netherlands; Tokelau from
  New Zealand; Christmas Island and the Cocos (Keeling) Islands from the shared
  "Indian Ocean Ter." feature.
- **Ceuta, Melilla and the plazas de soberanía dropped** from Spain (a few km²
  on the Moroccan coast that would make Spain and Morocco neighbours rather
  than 14 km apart across the Strait).
- **Slivers folded in:** the Akrotiri and Dhekelia bases and the UN buffer zone
  into Cyprus (otherwise the UK borders Cyprus), the Siachen Glacier into India,
  Baikonur into Kazakhstan, Guantánamo Bay into Cuba.
- **Dropped:** Ashmore and Cartier, Coral Sea, Spratly, Clipperton, Bajo Nuevo,
  Serranilla and Scarborough reefs, the U.S. Minor Outlying Islands (one
  "country" scattered across the Pacific), and Bouvet Island (Norway; not in
  the 50m globe).
- **UN positions over Natural Earth's de facto lines:** Crimea is drawn as part
  of Ukraine, and all of Western Sahara south of 27°40′N is Western Sahara (Natural
  Earth gives Morocco everything west of the berm). Elsewhere the de facto lines
  stand (Kashmir, Golan Heights, Abkhazia).
- **Somaliland and Northern Cyprus are part of Somalia and Cyprus** (the UN
  position, as in real Globle), so Somalia borders Djibouti and Cyprus is the
  whole island. Natural Earth draws both as countries of their own. Western
  Sahara stays separate.

## Catalog choices

- **Regions** follow UN M49, except Russia → europe and Cyprus → europe.
  Turkey, the Caucasus and Kazakhstan are asia; Egypt is africa; Greenland is
  americas; French Southern Lands and the British Indian Ocean Territory are
  africa (as M49 has them).
- **Capitals with a choice:** Bolivia → Sucre (La Paz is an alias); South Africa
  → Pretoria (Cape Town, Bloemfontein aliases); Netherlands → Amsterdam (The
  Hague alias); Israel → Jerusalem (plotted at the government quarter, as the
  usual Old City point falls just over the Green Line); Palestine → Ramallah (East Jerusalem alias —
  two capitals 2 km apart would make the capitals mode a coin toss); Côte
  d'Ivoire → Yamoussoukro, Tanzania → Dodoma, Burundi → Gitega, Benin →
  Porto-Novo, Sri Lanka → Sri Jayawardenepura Kotte, Eswatini → Mbabane, Myanmar
  → Naypyidaw, Equatorial Guinea → Ciudad de la Paz (decree of 2 Jan 2026),
  each with the better-known city as an alias; Nauru → Yaren (de facto);
  Montserrat → Brades (de facto; Plymouth alias).
- **No capital** (`null`): Antarctica, Hong Kong, Macao, Tokelau, Heard and
  McDonald Islands, British Indian Ocean Territory.
- **Aliases** are hand-written plus `i18n-iso-countries`' English names; any name
  two countries would share is dropped from both (it calls both Congos "Congo",
  so only the Republic of the Congo keeps it, by hand). Duplicates that differ
  only by case or accents are dropped, since the client folds those itself.

## What `check-data.mjs` verifies

Contract shape and field order of every entry; codes identical across all three
files; 197 answers, each with a region and a capital; names and aliases
unambiguous; matrix square, symmetric, integer, zero diagonal; known distances
(France–Spain 0, Italy–Vatican 0, Somalia–Djibouti 0, France–Brazil > 3,000,
UK–France 20–60, Spain–Morocco 5–30, Russia–USA 0–100, Iceland–Greenland
200–400, Norway–South Africa 8,500–10,000, …); every pair that includes an
answer within max(150 km, 15%) of the gap between the two drawn shapes, so no
colour contradicts the globe (the slack covers 10m and 50m drawing land
borders differently); every drawn shape has
positive, less-than-hemisphere area after quantisation; world.json under 1.25 MB;
every capital inside or within 50 km of its country, every label point within
25 km; well-known capitals at the right coordinates; label points for
antimeridian and multi-island countries on the main landmass.

Australia–New Zealand is checked at 550–700 km, not the 1,300–2,300 km of the
Tasman Sea: Macquarie Island (Australia) is ~620 km from the Auckland Islands
(New Zealand), both drawn on the globe, and the rule is the minimum over all
territory.

The world-countries cross-check prints notes, not failures. Expected ones:
Kosovo's borders (world-countries files it as `UNK`, not `XKX`); Spain–Morocco (Ceuta
and Melilla dropped); Sri Lanka–India (28 km across Adam's Bridge);
Namibia–Zimbabwe touching at Kazungula (Natural Earth puts a quadripoint
there); the punctuation of Guernsey's and the United States' capitals; and
world-countries marking Vatican City a UN member (it is an observer).

## Removing

Delete `tools/globle/` and the `tools/globle/node_modules/` line in
`.gitignore`. The generated files go with the rest of the game (see
`docs/globle.md`).
