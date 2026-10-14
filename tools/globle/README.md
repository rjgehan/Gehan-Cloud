# Globle data tooling

Builds the three data files the Globle game ships with. Nothing here runs as
part of the Maven build or at runtime: the outputs are committed, and this
directory exists so they can be regenerated and audited.

| File | What it holds | Used by |
| --- | --- | --- |
| `src/main/resources/globle/countries.json` | 248 countries and territories: code, name and Spanish name, flag code, aliases in English and Spanish, whether it can be an answer, region, difficulty (1–5), whether it is a small island nation, a label point, capital (with its English and Spanish names and aliases) | server |
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
Edit `lib/catalog.mjs` to rename a country, add an alias, move a capital or
change a difficulty tier; edit `lib/catalog-es.mjs` for a Spanish name or alias
(see [Spanish names](#spanish-names)); edit `lib/sources.mjs` to change how Natural Earth
features become countries.

## Sources and licences

| Source | Used for | Licence |
| --- | --- | --- |
| [Natural Earth](https://www.naturalearthdata.com/) admin-0 countries, 1:10m and 1:50m, via [`world-atlas`](https://github.com/topojson/world-atlas) 2.0.2 | all geometry | public domain (package ISC) |
| [`i18n-iso-countries`](https://github.com/michaelwittig/node-i18n-iso-countries) | ISO numeric → alpha-3/alpha-2 codes, extra English and Spanish aliases | MIT |
| `lib/catalog.mjs`, written by hand | display names, aliases, regions, answer set, capitals and their coordinates, difficulty tiers | part of this repo |
| `lib/catalog-es.mjs`, written by hand | Spanish names and aliases of every place and capital | part of this repo |
| `d3-geo`, `topojson-client`, `topojson-server`, `polylabel` | geometry code only | ISC |
| [`world-countries`](https://github.com/mledoze/countries) 5.1.0 (dev only) | **cross-checks in `check-data.mjs` only** — borders, UN membership, capital names, Spanish names | ODbL-1.0 |

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

**Small island nations** (`smallIsland`) are what "mainland only" games
leave out: Tonga goes, Japan stays. An answer country is one when all three
hold:

- none of its land is on a continental mainland (Afro-Eurasia, the Americas,
  Australia), so it is islands only;
- its 10m land area is under 30,000 km²;
- it has no land border with another answer country.

The mainlands are found from the geometry. Every 10m polygon is a node, and
two polygons are one landmass when they share a vertex. Natural Earth shares
arcs along land borders, so a continent's countries join up, while an island
shares a vertex with nothing. A few countries' largest polygons anchor each
mainland: Russia, China, India, France, Egypt and South Africa for
Afro-Eurasia; the USA, Canada, Mexico, Brazil and Argentina for the Americas;
Australia. The build fails if an anchor lands on the wrong landmass. Vatican
City's 10m polygon collapses to a line, but its vertices still join Italy's.

That gives these 31: Antigua and Barbuda, Bahamas, Bahrain, Barbados, Cabo
Verde, Comoros, Cyprus, Dominica, Fiji, Grenada, Jamaica, Kiribati, Maldives,
Malta, Marshall Islands, Mauritius, Micronesia, Nauru, Palau, Saint Kitts and
Nevis, Saint Lucia, Saint Vincent and the Grenadines, Samoa, São Tomé and
Príncipe, Seychelles, Singapore, Solomon Islands, Tonga, Trinidad and Tobago,
Tuvalu, Vanuatu. The edges of the rule:

- Solomon Islands (27,300 km²) is under the area cap. Taiwan (36,300 km²),
  Sri Lanka, Iceland and Cuba are over it.
- Haiti (27,000 km²), Timor-Leste and Brunei are under the cap and islands
  only, but each shares its island with another answer, so a neighbour
  lights up next to it the way it would on a mainland. They stay.
- Singapore is 2 km from Malaysia, not touching, so it is out. Cyprus has no
  land border once the UK bases are folded into it, so it is out too.

Territories are never the answer, so they are never small island nations.

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

The globe draws its coastline as the arcs only one country uses, so every land
border has to come out as one arc both sides share. Natural Earth does not
always put a vertex on both sides of a junction: Algeria's straight border has
no point where Morocco and Western Sahara meet across it, so the two sides used
to become separate one-country arcs and the globe drew 26 km of border as
coast, a white tick in the Sahara. Before encoding, the build therefore inserts
any vertex of another piece that lies on a segment (within one unit of the
1e5 grid topojson quantises to) into that segment. Today that adds 9 points:
Algeria's junction, Macau–China, Gibraltar–Spain, and a vertex one grid unit
off the other side's line on China–Mongolia, Croatia–Serbia and
Ethiopia–South Sudan. No drawn area moves by more than 1 km².

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

- **Difficulty** (`difficulty`, 1–5) is a hand judgement in `DIFFICULTY` in
  `lib/catalog.mjs`. It rates how readily a typical player (English-speaking,
  US-based) thinks of a country and finds it on a blind globe. That is recall
  and placement, not size or population alone:

  | Tier | Meaning | Examples | Answers |
  | --- | --- | --- | --- |
  | 1 | household names anyone can point to | USA, China, France, Egypt | 30 |
  | 2 | well known, and most adults can place them | Netherlands, Iran, Kenya | 40 |
  | 3 | familiar names in a busier part of the map | Hungary, Oman, Ghana | 45 |
  | 4 | heard of but rarely guessed | the Balkans, Baltics, Caucasus, Central Asia, West Africa, Central America | 45 |
  | 5 | obscure | microstates, small island nations, Eswatini, Djibouti, Bhutan, Timor-Leste | 37 |

  Hard mode draws its answers by tier, with tickets per tier set in
  `globle.hard-weights` (default 0/1/3/6/8). Tier 1 is never drawn.
  `npm run check` prints the share of a single draw each tier gets, which is
  what Hard practice sees. It also prints the Hard daily's long-run mix: the
  daily skips its own answers from the last 55 days, mostly tier 4 and 5, which
  flattens it to about 8% / 23% / 35% / 34% for tiers 2–5. Territories are
  never the answer and are written out as 5. The build fails unless every answer is in
  exactly one tier.

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

## Spanish names

Every place has a Spanish name (`nameEs`) and Spanish aliases (`aliasesEs`),
and so does every capital. They come from `lib/catalog-es.mjs`, written by
hand with one entry per code: the build fails if it misses a code or has one
`lib/catalog.mjs` lacks, if a place has a Spanish capital without an English
one or the other way round, or if an entry repeats itself (an alias equal to
its own name or an earlier alias, case and accents aside). The page shows the
names in its language and accepts both languages' names whichever one it
shows (`match.js`), so a Spanish speaker can type "Germany" and an English
speaker "Alemania". The server keeps naming places in English.

- **Sources.** The names follow the country list of the RAE's *Diccionario
  panhispánico de dudas* and Fundéu's recommendations: the forms in common use
  across Latin America and Spain (Alemania, Países Bajos, Arabia Saudita, Catar,
  Baréin, Yibuti, Esuatini, Fiyi, Palaos, Surinam, Belice, Chequia, Macedonia
  del Norte, Guinea-Bisáu, Bangladés, Birmania, Timor Oriental), and their
  capitals (Pekín, Nueva Delhi, Adís Abeba, Yamena, Abuya, Nom Pen, Pionyang,
  Taipéi, Reikiavik, Liubliana, Nasáu, Puerto España). On top, the build
  borrows `i18n-iso-countries`' Spanish names (MIT) as aliases, under the
  same rule as the English ones: kept only when exactly one place has them,
  and skipped when they are sort keys ("Congo (República Democrática del)").
  Today that adds Brunei Darussalam, Guinea Bissau, Heard e Islas McDonald,
  República Democrática Popular de Lao, Georgia del Sur y las
  Islas Sandwich del Sur and República Árabe Siria. world-countries (ODbL) is
  only read by the checker, as for everything else.
- **Aliases** are the other Spanish names people type: Spain against Latin
  America (Arabia Saudí, Kazajstán, Nueva Zelandia, Suráfrica), the
  international spelling where Spanish texts use it too (Kenya, Rwanda, Malawi,
  Botswana, Qatar, Iraq, Fiji, Palau), spellings Fundéu records in use beside
  the ones it recommends (Kazakistán, and Riyad for Riad), Congo-Kinsasa
  beside Congo-Kinshasa (Kinsasa being the capital's Spanish spelling), the
  "Rep." people write for República (Rep. Checa, Rep. Dominicana, Rep.
  Centroafricana, Rep. Democrática del Congo), colloquial and older names
  (Holanda, EE. UU., EEUU, EUA, Inglaterra, Suazilandia, Zaire, Rodesia,
  Ceilán, Persia, Siam), and the capital forms beside them (Beijing, La Haya,
  Ciudad del Cabo, Rangún). Each language's list stands on its own, so a
  Spanish alias may be spelled like an English one (Malvinas, Zaire); spellings
  that differ only by case or accents are left out, since the matcher folds
  both ("Peru" is not listed beside Perú).
- **Capitals.** `nameEs` repeats the English name where Spanish uses it
  unchanged (Lima, Ottawa, Kingston), so it is never missing. A capital alias
  that would only repeat its own country's name (Guatemala, Panamá, Kuwait) is
  left out: the capitals list already finds a capital by its country's name.
- **Articles.** A leading article is not written as an alias (Las Bahamas, el
  Perú, los Estados Unidos, la India): the matcher drops "el", "la", "los" and
  "las" the way it drops "the". Where the article is part of the name it stays
  (El Salvador, El Cairo, La Habana, La Paz, La Haya, La Valeta, El Aaiún).
- **Questionable cases mirror the English data.** English lists England,
  Scotland, Wales and Northern Ireland for the United Kingdom, so Spanish has
  Inglaterra, Escocia, Gales and Irlanda del Norte. "Bretaña" is left out: in
  Spanish it is Brittany. "América" is not added for the United States, since
  to a Spanish speaker it is the continent; the English alias "America" still
  finds the United States, as it always has.
- **Judgement calls.** Falkland Islands → Islas Malvinas (Malvinas an alias),
  its capital → Puerto Argentino (Stanley and Puerto Stanley aliases): the
  names Spanish speakers use, as Fundéu records. Myanmar → Birmania, the DPD's
  preference (Myanmar an alias). Timor-Leste → Timor Oriental. Czechia →
  Chequia (República Checa an alias). Belarus → Bielorrusia (Belarús an alias).
  Moldova → Moldavia. Ukraine's capital → Kiev, the traditional Spanish form
  (Kyiv an alias). Myanmar's capital → Naipyidó. Grenada → Granada. Saint
  Kitts and Nevis → San Cristóbal y Nieves. Saint Martin (the French part) →
  San Martín, and Sint Maarten keeps its name, as English keeps them apart.
  Guam's capital → Agaña, its Spanish name. Romania → Rumania (Rumanía is the
  same once accents are folded).
- **Uniqueness.** Every name and alias in both languages together names one
  place: a hand-written clash fails the build (`es alias "…" of KEN clashes
  with TZA`), and `check-data.mjs` checks it again on the output. The
  capitals are checked too, under the client's matcher; the only capital
  names two places share are the English ones that already did (Kingston,
  George Town/Georgetown, Basse-Terre/Basseterre).

## What `check-data.mjs` verifies

Contract shape and field order of every entry, a Spanish name on every place
and capital; codes identical across all three files; 197 answers, each with a
region and a capital; names and aliases unambiguous in English and Spanish
together, also under the client's matcher (match.js findExact, with the page
in either language), which Hard relies on because it has no dropdown; the
same for capital names in both languages, apart from the three homonym pairs
above; well-known Spanish names (Alemania, Estados Unidos, Reino Unido, Países
Bajos and its alias Holanda, Islas Malvinas, Costa de Marfil, Londres, Pekín,
Ciudad de México, ...) and what Spanish speakers type ("los Estados Unidos",
"el Perú", "Sta. Lucía", "EE. UU.", "Rep. Checa", "congo kinsasa", "Germany"
on a page in Spanish, and capitals such as "riyad"), with
match.js labelling places and capitals in the page's language; the answer
countries whose name or alias, in either language, is the start of another
answer's are exactly the expected pairs (Niger/Nigeria, Guinea/Guinea-Bissau
and Guinea Ecuatorial, Sudán/Sudán del Sur, Dominica/Dominican Republic,
Congo/Congo-Kinshasa, Corea/Corea del Norte, UK/Ukraine, and Irlanda/Irlanda
del Norte for the United Kingdom), since Name them all waits after such a
name to see whether more is coming;
every answer has a difficulty of 1–5 and every territory 5;
each tier holds 20–55 answers; the owner's examples are at the right end of
the scale (tier 1: USA, China, Russia, Brazil, India, Australia, Canada,
France, Germany, UK, Japan, Italy, Spain, Mexico, Egypt; tier 5:
microstates, Tonga, Tuvalu, Nauru, Saint Kitts and Nevis, Eswatini,
Lesotho, Djibouti, Comoros, São Tomé, Equatorial Guinea, Guinea-Bissau,
Bhutan, Brunei, Timor-Leste); the small island nations are exactly the
expected 31; Japan, the UK, Ireland, Iceland, Cuba, New Zealand,
Madagascar, Sri Lanka, the Philippines, Indonesia, Taiwan, Haiti, the
Dominican Republic, Papua New Guinea, Timor-Leste, Brunei, Australia and
the mainland microstates stay in the mainland pool; no small island nation
has a land border with an answer or a drawn shape over 30,000 km²; matrix square, symmetric, integer, zero diagonal; known distances
(France–Spain 0, Italy–Vatican 0, Somalia–Djibouti 0, France–Brazil > 3,000,
UK–France 20–60, Spain–Morocco 5–30, Russia–USA 0–100, Iceland–Greenland
200–400, Norway–South Africa 8,500–10,000, …); every pair that includes an
answer within max(150 km, 15%) of the gap between the two drawn shapes, so no
colour contradicts the globe (the slack covers 10m and 50m drawing land
borders differently); every drawn shape has
positive, less-than-hemisphere area after quantisation; no border drawn as
coast (no two one-country arcs within a grid unit of each other for more than
half a unit); world.json under 1.25 MB;
every capital inside or within 50 km of its country, every label point within
25 km; well-known capitals at the right coordinates; label points for
antimeridian and multi-island countries on the main landmass.

Australia–New Zealand is checked at 550–700 km, not the 1,300–2,300 km of the
Tasman Sea: Macquarie Island (Australia) is ~620 km from the Auckland Islands
(New Zealand), both drawn on the globe, and the rule is the minimum over all
territory.

It also prints the answers per tier, the share of a single Hard draw each
tier gets under the default weights, the Hard daily's tier mix over 100,000
simulated days (same weights, its 55-day no-repeat window, never the day's
classic answer, fixed seed), and the size of the mainland pool.

The world-countries cross-check prints notes, not failures. Expected ones:
Kosovo's borders (world-countries files it as `UNK`, not `XKX`); Spain–Morocco (Ceuta
and Melilla dropped); Sri Lanka–India (28 km across Adam's Bridge);
Namibia–Zimbabwe touching at Kazungula (Natural Earth puts a quadripoint
there); the punctuation of Guernsey's and the United States' capitals;
world-countries marking Vatican City a UN member (it is an observer); and its
Spanish names that are not ours and that the matcher does not find: Alandia
(Islas Åland), "Congo (Rep. Dem.)" (a sort key), Islas Cocos o Islas Keeling,
Islas Faroe (Feroe), Islas Svalbard y Jan Mayen, Islas Turks y Caicos (Turcas),
Islas Tokelau and Islas Vírgenes del Reino Unido (Británicas). They are left
as notes rather than copied in, to keep the ODbL out of the output.

## Removing

Delete `tools/globle/` and the `tools/globle/node_modules/` line in
`.gitignore`. The generated files go with the rest of the game (see
`docs/globle.md`).
