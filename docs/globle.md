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
for that daily. Below them, under "More ways to play", are the practice presets with
**Name them all** last, and a compact row for the remaining regions. The whole page
is in English or Spanish (see [Languages](#languages)).

| Mode | The mystery | Counts toward |
| --- | --- | --- |
| **Classic daily** | One country per day, the same for everybody. A new one at midnight in `globle.zone`. | The classic streak, stats and Today / Week / All time boards |
| **Hard daily** | A second country per day ("Hard #N", same N), the same for everybody, never the day's classic answer, and weighted toward countries few people could place (see [Difficulty](#difficulty)). Blind globe, no suggestions. | Its own streak, stats and boards, separate from classic |
| **Unlimited** | A random country whenever you like, anywhere in the world. | The practice board (Countries) |
| **Europe only**, **Africa**, **Americas**, **Asia**, **Oceania** | The same, inside one region. | Countries |
| **Mainland only** | The same, without the small island nations: Japan and New Zealand stay, Tonga and Malta go (see [Mainland](#mainland)). | Countries |
| **Capitals** | A random capital city. You guess capitals ("Paris — France"); distance is city to city, and each guess is a dot on the globe. | The practice board (Capitals) |
| **Hard practice** | Unlimited rounds played the Hard way: blind globe, no suggestions, weighted toward hard countries. | The practice board (Hard) |
| **Name them all** | No mystery: name as many countries as you can, in the whole world or one continent. Each one turns green on the globe. No time limit, but a stopwatch runs. See [Name them all](#name-them-all). | Its own board (whole-world runs) |

**The globe.** In every mode except Hard, all land is drawn as one shape with its
coastline and nothing else: no country borders, so the outline of an unguessed
country gives nothing away. A guessed country is filled in its distance colour, with
at most a hairline of that same colour to close the seam between two guessed
neighbours. In **Hard** the globe is blind: sea and a faint graticule only, with each
guess filled in where it lies. When a game ends the answer appears, solid green if
you found it and hatched if you gave up. A finished Hard game also fades the land in,
still with no borders, so you can see where the answer is. The globe spins under a
drag everywhere. On a phone's home screen it spins sideways only, so an up or down
swipe over it still scrolls the page (`touch-action: pan-y`).

**Hard input.** Hard has no dropdown. The box is plain text with autocomplete,
autocorrect and spellcheck off, and Enter submits. The text must match a place's
name or one of its aliases exactly, in English or Spanish whichever language the
page shows, ignoring case, accents, punctuation and a leading article ("ivory
coast", "CÔTE D'IVOIRE", "Costa de Marfil", "los Estados Unidos"; the full rules are
under [Languages](#languages)). Anything else gets "Not recognised — check the
spelling", with no suggestions, and nothing is sent. The list of guesses still shows
names and distances.

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
`hard`. `#/all` is Name them all for the whole world and `#/all/<region>` for one
continent; `#/all/study` and `#/all/<region>/study` study the last game there (see
[Study what you missed](#study-what-you-missed)), and `#/requests` is the admin's list
of name-change requests (see [Name-change requests](#name-change-requests)). The game
view has a Home button and a badge naming the game ("Hard · Daily #3", "Europe only",
"Name them all · Europe", "Study · Europe"). After the classic daily, the end card
offers "Play today's Hard →". A first visit shows one sentence, the colour legend, a
Play button and an English | Español toggle. The ? sheet has a line per mode and the
same toggle, and the header has a language chip.

**Typing on a phone.** The globe is what a guess is read against, so it stays in view
while the keyboard is open. On the single-column layout (under 960 px wide), once a
guess box in any mode has focus and the visible area has shrunk by at least 120 px (an
on-screen keyboard), `typing.js` pins the app to exactly the part of the screen above
the keyboard. The header, the game bar, the practice note and Name them all's region
chips are put away, and the globe shrinks to fill whatever the box and its feedback
line leave (Name them all's Restart and I'm done sit beside the count, above the box,
so they stay in view). The layout follows the keyboard only once it has stopped
sliding (the visible height unchanged for 120 ms), so the globe is resized once, not
on every frame of the slide. While typing, the line under the box keeps one height
(empty, "✓ France" or a "Did you mean" button), and while the visible height stays the
same the globe only ever gives room up, never takes it back: an earlier version
resized the globe with every name, which read as it zooming in and out. Touching the
globe or its zoom buttons does not take the focus from the box, so the keyboard stays
up and the globe can be spun while typing. Suggestions open upwards over the globe,
taking at most a little over half of it. When the keyboard closes or the box loses
focus, the page goes back as it was, scrolled to the top. A hardware keyboard, a pinch-zoomed
page or a narrow desktop window never shrinks the visible area that way, so they keep
the normal layout. The size comes from the visual viewport, not from scrolling, which
is what works on iOS, where the keyboard pans the visible area over a page that does
not get shorter.

### Name them all

Name every country you can. There is no mystery and no distance: each country named
turns green on the globe, and a stopwatch runs, with no time limit. Only the 197
answer countries count, each by a name of the whole country: a territory, a part of a
country, a former name or "America" alone never does (see Which names count, below).
Spelling is forgiven, and a finished game can be studied (see [Study what you
missed](#study-what-you-missed)). Internally the mode is called roll call (`rollcall`
in the API and the code).

**Regions.** `#/all` is the whole world (197) and `#/all/<region>` one continent:
`africa` (54), `americas` (35), `asia` (48), `europe` (46) or `oceania` (14).
`#/all/world` is read as `#/all`, and any other region goes home. A row of region
buttons sits at the top of the panel. Picking one replaces the history entry, so Back
still goes home in one step, and leaves focus and the scroll on the button picked. A
dot marks each other region with a run in progress. The API also accepts `mainland` (166), which the page does not offer. Each
player has at most one run in progress per region, the way each practice preset keeps
one game.

**Autofill.** The setup card offers to fill some countries in before the run starts,
so the ones you always get need not be typed. The server works them out when it
creates the run and stores them first, flagged as filled in:

| Choice | Fills in | World |
| --- | --- | --- |
| Off (`off`) | Nothing | 0 |
| Easy ones (`easy`) | The region's difficulty tier 1 countries (see [Difficulty](#difficulty)) | 30 |
| Easy + well known (`known`) | Tiers 1 and 2 | 70 |
| My sure things (`mine`) | Every country named, typed or filled in, in each of your last three runs that count in this region (each of the last two, when there are only two) | Yours |

- "My sure things" opens after two runs that count in the region. Until then the card
  shows it locked ("after 2 games", "after 1 more game"), and the server refuses it
  with `400 bad-request`. It is worked out from the runs that counted before the
  start, which is what the card showed. A run that the start abandons counts from the
  next start on.
- Any choice that would fill in the whole region is refused the same way, since
  nothing would be left to name. The card sees it coming, because the choice's count
  equals the region's total, and shows it disabled: "that's all of them — nothing
  left to name". With today's data only "My sure things" can get there, after naming
  all of a small region such as Oceania in each recent run. A run therefore never
  starts finished.
- Filled-in countries count toward the run's total, and the run records how many
  there were. The tiers stay on the server: the page learns only which codes were
  filled in.
- The last choice picked is remembered on the device. If a start is refused anyway
  (a run finished on another device changed the numbers), the card says "That choice
  isn't open any more. Pick another." and reloads them.

**Typing.** The box is plain text with no list of suggestions, since a list would
give the countries away, and nothing from the browser either (see No dropdown,
below). A name is matched as in Hard (see Hard input under [Modes](#modes)), in
English or Spanish whichever language the page shows, and Enter adds a lenient spell
check on top. Start puts focus in the box on a desktop; on a touch screen, where that
would bring the keyboard up unasked, it goes to the counter, so a screen reader is
not left on the Start button that just went.

- A country is accepted the moment the text names exactly one country still to name
  in the region. The box empties but keeps focus, so a phone's keyboard stays up. The
  line under it shows "✓ 🇫🇷 France", and the country pulses where it is. Only a
  country off screen makes the globe glide to it, and without the zoom dip other modes
  use, so names typed one after another do not keep pulling the globe back and in, or
  undo a spin.
- If the text is also the start of another unnamed country's name or alias, in
  either language, it waits 0.7 s for more typing (2 s when the text ends in a space
  or a hyphen), and Enter accepts it at once. The pairs are Niger/Nigeria,
  Guinea/Guinea-Bissau and Guinea Ecuatorial, Sudán/Sudán del Sur,
  Dominica/Dominican Republic, Congo/Congo-Kinshasa, Corea/Corea del Norte,
  UK/Ukraine and Irlanda/Irlanda del Norte (a Spanish alias of the United Kingdom,
  which no longer counts but still makes "Irlanda" wait).
  `npm run check` fails if the data adds or drops one. Once the longer one is named,
  the short one is accepted at once.
- After a short name is accepted, the rest of its long form typed within 2 s is
  ignored, so "Bosnia" then " and Herzegovina" names Bosnia and Herzegovina once and
  leaves the box empty.
- Text that names something that does not count is explained: "You already named
  Niger.", "Greenland doesn't count: it isn't one of the 197 countries.", "Mexico
  isn't in Europe.", "England is part of the United Kingdom, so it doesn't count —
  name the country." The explanation shows after a 0.9 s pause, once the text cannot
  still grow into a country to name, or at once on Enter, which also clears the box.
  Escape clears the box.
- Anything else waits for Enter, which hands it to the spell check (below). Only
  text it cannot place at all gets "Not recognised — check the spelling", and stays
  in the box to be fixed.

**Which names count.** Only a name of one of the 197 countries, the whole country,
counts. Everything else is explained, or at most offered as "Did you mean …?" a
country, which then counts only if the player takes it:

- Territories, and Antarctica, which is not one: "Greenland doesn't count: it isn't
  one of the 197 countries."
- A part of a country: England, Scotland, Wales and Northern Ireland (Inglaterra,
  Escocia, Gales, Irlanda del Norte) for the United Kingdom, and the West Bank and
  Gaza (Cisjordania, Franja de Gaza) for Palestine: "England is part of the United
  Kingdom, so it doesn't count — name the country." The second half of a two-part
  name is a part too, never a name: "Tobago", "Barbuda", "Herzegovina", "Nevis"
  ("Nieves"), "Príncipe", "the Grenadines" ("las Granadinas"). These are worked out
  from the names themselves: the words after the last "and", "&", "y" or "e" in any
  name of an answer country, when they come to four letters or more. A part that
  needs an article in the sentence gets it ("The West Bank is part of Palestine", "La
  Franja de Gaza es parte de Palestina"; `rollcall.term.*` in `i18n.js`), and the
  Grenadines have a sentence of their own: "The Grenadines are part of Saint Vincent
  and the Grenadines, so they don't count".
- A former name: Persia, Zaire, Rhodesia, Siam, Ceylon, Dahomey, Upper Volta,
  Abyssinia, Kampuchea, East Pakistan, Byelorussia, British Honduras, British and
  Dutch Guiana, Kirghizia, Formosa, the New Hebrides, Western Samoa and the Ellice
  Islands, and their Spanish forms ("Islas Ellice"), except those the data leaves out
  because they would start another country's name (Honduras Británica, Pakistán
  Oriental, Rusia Blanca; see [Data](#data)): "Persia is an old name for
  Iran, so it doesn't count — use today's name."
- "America" (or "América") alone, which in Spanish is the continent. It only ever
  offers "Did you mean 🇺🇸 United States?", on Enter.
- The word for a country's people: "Russian", "German", "Yemeni", "Swiss", "chino",
  "argentino", "sueca". Usually a slip or two from the country's name, it is only
  ever offered ("Did you mean Russia?"), never counted at once. A capital's exact
  name ("Tunis") is likewise offered at most.
- Cities, continents, regions, historical states (the USSR, Yugoslavia,
  Czechoslovakia, Prussia) and random words name nothing in the data. They get "Not
  recognised", or an offer where they are genuinely close to a country's name
  ("Prussia" offers Russia, "Perth" Peru).

The names that do not count are `NOT_COUNTING` in `rollcall.js`: a short list keyed
by country code, in three kinds (`part`, `former`, `ambiguous`), matched through
`match.js`'s `normalize()` like any name, in both languages. The data keeps them as
aliases, so classic, Hard and practice still take "England" for the United Kingdom
and "Persia" for Iran. `npm run check` fails if one stops being an alias of its
country, becomes its main name, or belongs to a place that is not an answer. Every
other alias of the 197 was audited and counts on purpose: Great Britain and Britain,
Holland and Holanda (each country's everyday name); short forms of a country's own
name (Bosnia, Antigua, Trinidad, Saint Kitts, Saint Vincent, São Tomé, Burkina);
names still in use (Burma, Swaziland, Macedonia, East Timor, Ivory Coast, Cape Verde,
Czech Republic, Türkiye, Kirguisia); the countries' own names for themselves
(Deutschland, Nippon, Hellas, Eire); Palestinian Territories; and Korea, for South
Korea.

**The spell check.** Enter on text that names nothing exactly reads it leniently
(`decideEnter()` in `rollcall.js`, with the measuring tools in `match.js`), against
every name and alias of every place in both languages, whichever one the page shows.
Both are normalised as for an exact match, then folded so that spellings which sound
alike agree (ph → f; h dropped except in ch and sh; qu and q → k; c → s before e, i
or y, else k; z → s; y → i; v → b; w → u; doubled letters as one), and compared by
optimal string alignment: the fewest letters added, dropped, changed, or swapped with
the next. A slip in the first letter costs two, since people rarely get it wrong and
"Prussia" is not Russia, and half a slip when only the fold makes the first letters
agree ("Kuba" for Cuba). Abbreviations and names under four letters only ever match
exactly, and text under four letters is never spell-checked, so "xq" or "abc" gets
no offer. One of three things happens:

1. **It counts at once**, exactly like a typed name, when one country is clearly
   meant. The text must be within the slips that country's name can take: none under
   5 letters unless the spelling folds the same ("Irann", "Chadd"; "Kuba" is only
   offered), one up to 11 ("kyrigistan", "naru"), two from 12 ("papa new guinia"),
   and none for an alias under 8 letters unless it folds the same, since a short
   alias is so often a word of its own ("Brittany" is not Britain). Every other place
   must be at least a slip further away, and the text must be neither a capital's
   exact name nor the word for the country's people. The line under the box says what
   it read, "✓ 🇰🇬 Kyrgyzstan (from “kyrigistan”)" (in Spanish "(escribiste
   “kyrigistan”)"), and typing goes straight on.
2. **"Did you mean …?"**, when one or two countries are close but not certain:
   within one slip under 6 letters, two under 12, three from 12, counted on the
   shorter of the name and the text (so "hola" is not Holland); or every letter of
   the name but one in order, starting with the same two letters and about as long
   ("naruto" for Nauru); or the first word or words of exactly one country's name,
   typed whole ("Marshall", "Saudi"; "Costa" and "North" begin several, and offer
   nothing). Two countries are offered when they are equally close ("nigera": Niger
   or Nigeria).
3. **Nothing close**: "Not recognised — check the spelling", and the text stays to
   be fixed.

When the closest place cannot count, that is said, rather than offering a worse one:
"Did you mean Greenland? It doesn't count: it isn't one of the 197 countries.", "Did
you mean Kyrgyzstan? You already named it.", "Did you mean Kenya? It isn't in
Europe.", "Did you mean England? It's part of the United Kingdom, so it doesn't count
— name the country." A country that can count and is as close is offered instead. A
part or a former name is only mentioned when the text is within one slip of it. A
country's name with the word for islands around it ("Comoro Islands", "Fiji Islands",
"islas Filipinas") counts as the name inside, when that would count by itself; one
island ("Great Britain island") may be only part of a country, so it does not. The
limits are `SPELLING`, `countLimit()` and `offerLimit()` in `rollcall.js`.

**Did you mean.** The offer is a line of one or two buttons under the box, "Did you
mean [🇳🇷 Nauru]?" ("¿Quisiste decir …?", joined by "or", or in Spanish "o", which
becomes "u" before an o sound). Tapping a button counts that country. With one country
offered, pressing Enter again on the same text counts it too, and a screen reader is
told so ("Press Enter to count it."). Only a fresh press does: a held-down Enter
repeating never takes an offer. Typing anything clears the offer, and picking a
country that was named meanwhile on another device just clears it too.

**Keeping the keyboard up.** The box keeps focus from one country to the next,
whatever Enter did: it is emptied in place when a name counts, left as it is on an
offer or "Not recognised", and cleared after an explanation. Enter is handled on
`keydown`, so nothing is submitted and nothing ends the edit (the Enter that ends an
IME composition is left to the keyboard). The ↵ button and the "Did you mean" buttons
cancel `mousedown`, as the classic suggestion list does, so pressing one with a
finger, a mouse or a pen never takes focus from the box. After a press, focus is put
back in the box if it ever left (a keyboard user may Tab to a button), except that on
a touch screen a tap brings it back only if the box had it when the tap began, so the
keyboard never comes up unasked. The phone's Enter key is `enterkeyhint="enter"`, a
plain return key. `done` would ask for the keyboard's Done action, which some phone
keyboards answer by closing. `typing.js` keeps the globe above the keyboard
throughout (see Typing on a phone under [Modes](#modes)).

**No dropdown.** Name them all has no list of its own, and the browser and password
managers are kept out too, since an autofill dropdown under a box that says "Type a
country…" could offer countries. The box has `autocomplete="off"`, as does its form
(Chrome and Firefox then keep no history for it); it has no `name` attribute
(Safari's AutoFill reads names first); it is the only field in its form (Chrome's
address autofill needs at least three fields it recognises); and it carries the
password managers' own opt-outs: `data-1p-ignore` (1Password), `data-lpignore="true"`
(LastPass), `data-bwignore` (Bitwarden) and `data-form-type="other"` (Dashlane).
Autocapitalize, autocorrect and spellcheck are off, so the phone does not rewrite a
misspelling the spell check would have read. The other modes' guess box carries the
same opt-outs, which change nothing else there.

**How well it spells.** The limits were calibrated against a corpus of realistic
misspellings of all 197 countries, as English and Spanish speakers type them
(phonetic spellings, dropped, doubled and swapped letters, missing spaces and
hyphens, the usual learner errors), and of things that are not countries: cities,
regions, parts of countries, continents, territories, former and historical states,
made-up places and everyday words, in both languages. With the page in either
language (the results are the same) and a whole-world run with nothing named yet:

| | 1,138 misspellings | 361 non-countries |
| --- | --- | --- |
| Counted at once | 1,063 (93.4%), none as the wrong country | 0 |
| Offered ("Did you mean") | 67 (5.9%), each with the right country | 31 (8.6%): "Prussia" → Russia, "Perth" → Peru, "America" → United States |
| Explained | 0 | 61: territories, parts of countries, former names |
| Nothing | 8 (0.7%): "tchad" (twice), "gine", "seashells", "xina", "lao", "llemen", "ostralia" | 269 |

The corpus had 1,244 lines, 106 of them already an exact name or alias, which count
as typed and are left out above; 30 scraps of gibberish of one to three letters got
nothing at all. An Enter took under a millisecond, in Node on a desktop. A second
corpus, written separately to check the first (530 misspellings, 1,005
non-countries, 311 of them nationalities), first found seven of its non-countries
counting: everyday words a slip from a country's name ("hungry" as Hungary, "chilly"
Chile, "island" Iceland, "kenny" Kenya, "georgie" Georgia, "argentine" Argentina,
"tunes" Tunisia, whose Spanish "Túnez" folds to the same letters). Nothing in the
letters tells such a word from a typo, so `rollcall.js` lists them (`ORDINARY_WORDS`)
and treats them like nationalities: only ever offered, never counted at once. Add to
that list when another turns up. With it the second corpus gives 96–98% counted at
once, the rest offered with the right country, none missed, none counted as the
wrong country, and none of its 1,005 non-countries counted. Both corpora were working
files and are not in the repository.

What must keep holding is `tools/globle/check-rollcall.mjs` (`npm run check:rollcall`,
and part of `npm run check`; it needs no `npm ci`). It loads `match.js` and
`rollcall.js` over the real country list, with the page in each language, and
checks: every `NOT_COUNTING` name as above; what Enter does with 43 texts (the
user's own "kyrigistan", which counts as Kyrgyzstan, and "naruto", which offers
Nauru; parts, halves, former names, territories, "America", the island forms, the
two-country offers), and with six more once a country is already named or outside
the region; that 24 nationalities are offered and never counted; that 72
misspellings count at once as their country (the learner errors, such as
"phillipines", "columbia", "kazakstan", "isreal", and Spanish ones, such as
"quirguistán", "kazajastan", "filipnas"); that 51 cities, continents, regions,
historical states and words never count; that 9 scraps of gibberish get nothing;
and that no single slip (a letter dropped, doubled, or swapped with the next) in any
name or alias of the 197 of five letters or more, abbreviations aside, counts as a
different country. Today it prints:

    rollcall: 456 cases in both page languages; 12547 one-slip variants of the 197's names, 11154 counted at once (88.9%), none as another country

**Saving.** A name shows the moment it is accepted and is queued. The queue sends one
name at a time, in order, and typing never waits for it. A name the server refuses
is taken back with the reason. When a request fails (offline, a server error, a
timeout), that name and every name queued after it are taken back, and the line says
how many ("Chile and 1 more weren't saved."). After a timeout the page reads the run
again, since the name may have landed. When the session has ended (signed out, or the
page is out of date) the names waiting are taken back the same way, the line says why
("Chile wasn't saved. Your session has expired, so guesses can't be saved."), and the
session banner shows.

**What counts.** A run counts once it is finished with at least one country typed.
A run with nothing typed, ended at once with "I'm done" or holding only what was
filled in, keeps its end card but counts nowhere: not in the setup card's best or
its count of runs, not toward "My sure things", and not on the board. Its end card
says "You didn't type any, so this game doesn't count."

**Ending.** A run ends when you press "I'm done", when a new run in the same region
replaces it after something was typed, or by itself when the last country in the
region is named. "I'm done" asks first, then waits for any names still being saved.
"Restart" (beside the count, with "I'm done") asks too, then starts a new run in the same region with the same
fill-in (Off if that is no longer open). The run it replaces is finished and counts
if anything was typed ("Your last game counted: 57 of 197."), and is deleted
otherwise. Runs in other regions and practice games are never touched.

**End card.** "You named 162 of 197" ("Every single one!" when nothing was missed),
the time, and how many were filled in. "A new personal best!" shows when the run
beat an earlier best in that region; a first run is just a best. The missed
countries are listed by continent with flags, each with a pencil to ask for a name
change (see [Name-change requests](#name-change-requests)), and the globe hatches
them beside the named ones in green. Share copies, or on a phone shares, `Globle ·
Name them all 🌍 162/197 · 14:03` and the site, with the continent in brackets for a
continent run, and never a country's name. Then Play again, "Study what you missed"
when something was missed (see [Study what you missed](#study-what-you-missed)),
Leaderboard (opening on the Name them all board for that viewing, without changing
the tab the other boards open on) and Back home. Focus moves to Share when the card
appears, on every device.

**While playing**, the globe is given only the countries named, the filled-in ones
a deeper green, and its tooltip names only those: hovering or tapping an unnamed
country shows nothing. A world run shows its progress per continent. The named list
is grouped by continent, alphabetical in the page's language, with the filled-in
ones marked and a pencil beside each.

**Resuming and other devices.** The run in progress resumes on reload and on any
device. The stopwatch counts on from the server's `elapsedSeconds` by the browser's
own clock. It stops while the tab is hidden or another view shows, and resyncs with
the server when the tab comes back. The end card shows the server's final time.
Coming back to the tab also reads the run again, so countries named on another
device appear. A run finished or restarted on another device shows "That game ended
on another device", and the page moves to what the server has. A country already
named elsewhere is explained ("You already named …") and the run is read again.
Start checks the server first: if another device has started a run in that region,
the page shows that run rather than ending it. A finished run's end card lasts as
long as the page does. The server returns runs in progress only, so after a reload
the setup card shows.

**Home, leaderboard and stats.** The home row's chip says "Resume · 57 named" while a
whole-world run is in progress ("Resume" before anything is named), and otherwise
"Best 162/197" once there is a whole-world run that counts. The
leaderboard's practice tab has a Name them all board beside Countries, Capitals and
Hard: Player, Played, Best (162/197), Time and Auto (how many were filled in; hidden
on narrow screens). The stats sheet's Practice section has "Name them all: best
162/197 · 14:03".

#### Study what you missed

`#/all/study` (the whole world) and `#/all/<region>/study` study your newest finished
game there: the countries it missed, most worth learning first, with where each one
is and how often people get it. The end card offers it as "Study what you missed"
when something was missed, and the setup card as "Study your last game" once a game
there has finished, whether it counts or not (the options' `studyable`). `study.js`
draws the page; its words and rules are the pure `study-logic.js`.

The page opens with a line about the game ("Your last game in Europe (Oct 14): you
named 30 of 46 and missed 16.", in Spanish "te faltó 1" or "te faltaron 16" by the
number missed, or "You named all 14 in your last game here. Nothing to study!"),
then lists the first 10 missed countries in the server's order, then
"Show all N", which moves focus to the first one it adds. A region with no finished
game says so and offers Play. Each card is headed by the flag and the name in the
page's language, with a pencil to ask for a name change (see [Name-change
requests](#name-change-requests)), and then says, where it can:

- **Where it is:** "In the north-west of Africa, bordering Senegal." The compass part
  places the country's label point (`lat` and `lng` in the study response) in a 3 × 3
  grid over a rough box around its continent's mainland (`AREA_BOXES`). The Americas
  are four boxes (North America, Central America, the Caribbean, South America), so
  Chile is "In the west of South America". Asia's box is set so Thailand comes out in
  the south-east, Pakistan in the south and Kazakhstan in the middle, and three
  countries the grid would misplace are set by hand (`COMPASS_OVERRIDES`): Nigeria in
  the west of Africa, Denmark in the north of Europe, Taiwan in the east of Asia. A
  point outside its box counts as the nearest edge, so Cabo Verde comes out in the
  north-west of Africa and Mauritius in the south-east, and Oceania's box runs past
  the date line. The borders are the `nearby` countries 0 km away, and every
  bordering country is named (all fourteen of China's); with none, "Nearest country:
  Japan, 200 km away."
- **Its capital:** "Capital: Banjul".
- **The closest country on your list:** "Closest on your list: Senegal, 300 km away",
  or "Next to Senegal, which is on your list" when they share a border. A filled-in
  country is on your list too, which is why it never says you named it.
- **How often it is named:** "Usually named: in 9 of 11 games here." when more than
  half the games named it, "Named in half the games here: 2 of 4." at exactly half,
  "Usually missed: named in only 2 of 11 games here." when fewer than half did, or
  "Nobody has named it yet in 11 games." It speaks of games, not players: one
  player's games can be the whole sample, and a filled-in country counts as named.
  The counts are `namedRate` × `sampleRuns`, rounded, of `sampleRuns`, and the
  sentence is chosen from that rounded count, so the two always agree. With fewer than three games to
  go on, a sentence from its familiarity instead: "Not enough games yet to say, but
  it's one of the well-known ones." (or "fairly well known", or "one of the
  less-known ones").
- **Your own record:** "You've missed it in 3 of your 4 games here", "You missed it
  in your only game here", "You've missed it in every one of your 4 games here", or,
  when the game studied did not count, "You named it in each of your 3 other games
  here". Nothing when you have no game there that counts.

A country whose name takes an article inside a sentence gets it there ("bordering
the United States", "Limita con el Reino Unido", "Show the Netherlands on the
globe", "Suggest a name for the Philippines"): `names.js` `nameInSentence()` reads
`place.inSentence.<code>` in `i18n.js`, with the same codes in both languages, and
falls back to the plain name. Headings, labels and Copy all keep the plain name. The
Spanish templates never put such a name after "a" or "de" (which would need "al" or
"del").

Before a card is picked the globe frames the region, with the missed countries
hatched as on the end card. Tapping a card (or its name, a button) flies the globe to
the country and draws it solid and labelled, its bordering countries in a soft
yellow, and the other missed ones still hatched; the tooltip names all three kinds
("Studying", "Borders the Gambia", "Missed"). Play again goes to the region's setup card,
and Back home goes home. The page reads the study afresh each time it opens, forgets
it when a run in that region ends, and redraws from the same data when the language
changes.

**How the numbers are worked out.** `GET /globle/api/rollcall/study?region=africa`
(`RollCallStudyService`) answers for your newest finished run in that region, whether
it counts or not (its end card offers the study either way), or `204` when there is
none. It is read-only and stores nothing. The JSON is under [API](#api).

- `namedCount`, `total` and `missedCount` are the run's end card's, and `missed` is
  exactly its list: the region's countries the run did not name, filled-in ones
  counting as named.
- `nearby` is every answer country it borders (0 km), then the nearest others, to at
  least four: from any continent and never a territory, by the precomputed
  border-to-border km, nearest first. `0` is a shared
  border, and ties go to the one whose label point is closer, then to the code.
  `nearestNamed` is the same over the countries the run named, filled-in ones
  included, or null when it named none.
- The sample is every run in that region that counts (finished, with at least one
  country typed), by every account that still exists, yours included. `sampleRuns` is
  how many there are, the same on every entry. `namedRate` is the share of them that
  named the country, to four places, and null when there are fewer than three. A
  filled-in country counts as named here too, as on the end card, so autofill lifts
  the rates of the tier 1 and 2 countries. `yourRuns` is how many of those runs are
  yours, and `yourMisses` how many of yours missed it.
- The order is most worth learning first: the highest `namedRate` first, since
  missing what most players name is the gap most worth closing; then well-known,
  familiar, obscure; then by code. `order` says which applied: `named-rate`, or
  `familiarity` when there are too few runs for a rate.

**Familiarity, and why the page may know it.** Each missed country carries
`familiarity`, worked out from its difficulty tier (see [Difficulty](#difficulty)):
`well-known` for tiers 1 and 2, `familiar` for 3, `obscure` for 4 and 5. It orders the
list when there are too few games for a rate, and words the card then. This is the
one place anything of a tier reaches the page, and it is deliberate: the tiers
themselves are public in this repository (the `DIFFICULTY` lists in
`tools/globle/lib/catalog.mjs`), the label is three coarse bands rather than the tier,
and it is only given for the missed countries of a run that is already over.

## Name-change requests

Name them all counts only the names the data has, near misses aside. A player who
thinks a name should count, a spelling the game refused or a name it never knew,
sends it from the game, and the admin works through the requests at the next change
to the data. Nothing in the game changes until then.

**Sending one.** Every country listed in Name them all (the named list while
playing, the missed list on the end card) and every study card has a small pencil
(`bi-pencil-square`, named "Request a name change for France"). It opens a sheet,
"Suggest a name for 🇫🇷 France", with one box for the spelling that should count
(autofill, autocorrect and spellcheck off), and, when this run had any, up to 5 of
its texts that came back "Not recognised", newest first, as chips that fill the box.
Those texts are kept in memory only, per run, and are noted at one place in
`rollcall-view.js` (`noteUnrecognised`, called where Enter ends in "not
recognised"); nothing is sent unless a chip is picked and sent. Send checks the
server's rule first (below), then posts the place, the suggestion, the page's
language and where the pencil was (`named`, `missed` or `study`). Once it is sent the
sheet closes and a toast says "Thanks — sent for review." A refusal (`too-many`,
`bad-request`), being offline, a timeout or an ended session is explained in the
sheet, which stays open with the text in it; after a timeout Send can simply be
pressed again. `requests.js` draws the pencil, the sheet, the admin's count and
`#/requests`.

**The rules** are `NameRequestService`'s, under the same write lock as the games:

- Any signed-in player may send one, for any place in the country list, territories
  included.
- The suggestion is cleaned first (Unicode NFC, every run of spaces, line breaks and
  control characters one space, the ends trimmed), and must then be 1 to 80
  characters, counted as code points, with at least one letter or digit. Anything
  else is `400 bad-request`, as is an unknown code, `lang` or `source`.
- Sending the same place and the same suggestion again, as the page's matcher reads
  it ("Great Britain", "great-britain", "GreatBritain" and "the Great Britain" are
  one), while the first is still open returns that one unchanged, with `200` rather
  than `201`, so a double tap or a retry stores nothing new. Text in another script,
  which the matcher reads as nothing, is compared as cleaned, ignoring case. Once the
  first is closed, the same suggestion makes a new request.
- A player may have at most 30 open at once. The next is refused with `400
  too-many`, and the sheet says to wait until some have been looked at. Requests the
  data already answers count toward the 30 until the admin next looks (below).
- A request outlives its sender: one from an account deleted since stays listed
  under the username it was sent with.

**For the admin.** The admin is any session with the `ROLE_ADMIN` authority: the
same role, read the same way, that guards the user console at `/users` (so an admin
demoted while signed in keeps it until that session ends, as for `/users`). Their
home screen has a tiny, muted "✎ 3" after the "Inspired by globle-game.com" line,
shown only to the admin and only while requests are open; a screen reader hears "3
name-change requests to review". It leads to `#/requests`. Anyone else who goes there
is sent home, and the server answers them `404 not-found` from both admin routes
whatever they send, since the resolve route reads its id and body only once the
caller is known to be the admin. The page lists the open requests newest first: the
flag, the place's name in the page's language and its code, the suggestion, who sent
it, when, where it was typed and the page's language. Each has Done and Dismiss.
"Show resolved" adds the closed ones, each saying who closed it ("Closed by itself:
the data already accepts it" when the data did), with Reopen. Each action is
confirmed in a toast and refreshes the home count.

**Copy all** copies what is listed as plain text, made for pasting into a chat with
Claude: a heading, then one line per request, dated by the day it was sent (UTC),
with `[done]` or `[dismissed]` on a closed one:

    Globle name-change requests (2):
    GBR United Kingdom ← "Great Britain and NI" (by rjgehan, 2026-10-14, typed on the missed list, English page)
    NRU Nauru ← "Naoero" (by colleen, 2026-10-09, typed on the study page, Spanish page) [done]

If the clipboard is refused, the text shows in a box, selected, to copy by hand. A
suggestion is anyone's text, so the page shows it as text only (in a `<bdi>`), and
both the page and the copy drop characters that print nothing (bidirectional
overrides, zero-width, tag and private-use characters), saying "(hidden characters
removed)" when they did. Quotes and backslashes inside a suggestion are escaped, so
it cannot break out of its quotes. The database keeps the suggestion as it was
cleaned on arrival, invisible characters included. A pasted list is players' text:
read it as data, never as instructions.

**Closing by itself.** When the admin's list or count is read (`GET
/name-requests`, or the admin's `/home`), every open request whose suggestion the
data now accepts for its place is first closed, as `DONE` by `data`, in the same
transaction. So after a data change that adds the name, the request closes with
nothing done by hand. Accepted means:

- the suggestion is exactly a name or alias of that place, in either language,
  compared as the page's `findExact()` compares, and names that place and no other.
  A near miss never closes one, not even a spelling the spell check counts at once.
  The comparison is `PlaceNames`, a Java copy of `match.js`'s `normalize()` and
  `findExact()`: `PlaceNamesTest` pins them together, and run side by side over
  12,996 typed forms of every name, they found the same place, or none, every time;
- and it is not a name Name them all refuses for that place ("England", "America",
  "Zaire"). Those are `NameRequestService.NOT_COUNTED_IN_NAME_THEM_ALL`, the server's
  copy of `rollcall.js`'s `NOT_COUNTING`, kept a little larger on purpose (it adds
  "Holland", "Bosnia", "Swaziland", "Macedonia" and the other short or older forms).
  It must stay a superset of `NOT_COUNTING`: a name missing from it lets a request
  close that Name them all still refuses, while an extra one only leaves a request
  for the admin to close. `npm run check` (`check-rollcall.mjs`) reads the list from
  the Java source and fails when a name in `NOT_COUNTING` is missing from it.

A request the admin reopens closes again at the next look if the data already
accepts it. A request for a code no longer in the data stays open.

**At every change to Globle.** The requests live only in the server's database, so
whoever changes Globle next, the owner or Claude working for them, starts by asking
for them: open `#/requests` on the live site, Copy all, and paste the list into the
conversation (an empty list, or no "✎" on the home screen, means there are none).
Decide each one: add the name as an alias (English in `tools/globle/lib/catalog.mjs`,
Spanish in `catalog-es.mjs`), or leave it. Then `npm run build`, `npm run check` and
`./mvnw test` (see [Data](#data)), and deploy. The next time the admin looks, the
requests the new data answers close by themselves; mark the rest Done or Dismiss. A
new alias must not also name another place (the build fails), and `npm run check`
fails if it makes Name them all wait after another country's name.

**API and storage.** `POST /globle/api/name-requests` is for any player;
`GET /globle/api/name-requests?status=open|all` and
`POST /globle/api/name-requests/{id}/resolve` are the admin's; and `/home` adds
`admin` and `nameRequests`. The rows, the JSON and the refusals are under
[API](#api). The requests are one table, `globle_name_request` (see
[Storage](#storage)). The first start after an upgrade from either earlier release
creates it empty, and a rollback to either needs nothing ([Upgrading](#upgrading)).
Switching the game off removes the routes but not the table, and the last, optional
step of [Removing Globle](#removing-globle) drops it with the others.

## Languages

Globle is in English and Spanish, the Spanish written for Latin America, with "tú".
Everything a player sees or hears is translated: the page's text, buttons, labels,
help and welcome, every message, toast, confirmation and error, the end cards, the
leaderboard and stats, screen-reader text and announcements, the share text, the
autocomplete's tags ("Guessed", "Never the answer"), and the globe's labels and
tooltip. Brand names (Globle, Gehan.Cloud), km and mi, puzzle numbers and the
`<noscript>` line stay as they are.

**Choosing.** Until a player picks, the page is in Spanish if the first English or
Spanish entry in the browser's language list is Spanish, and in English otherwise:
`es-MX, en-US` and `fr-FR, es-ES` get Spanish, `en-US, es` and `de-DE` English. The
header's language chip (`bi-translate`, labelled with the language it switches to,
ES or EN) switches in one tap, as does the English | Español toggle in the welcome
and ? sheets. The choice is kept on the device in `localStorage` (`globle.lang`,
`en` or `es`). The page switches in place, with no reload, keeping anything typed
(a share text shown for copying is rewritten in the new language), and
`<html lang>` follows.

**Place names** show in the page's language everywhere: the autocomplete, the guess
list, the end card's answer and capital, the globe's label and tooltip, and Name them
all's lists. The server keeps naming places in English, so a game reads the same
whichever language asks for it, and the page swaps names by code from
`/globle/api/countries`, which carries `nameEs` beside each name (falling back to
English if it is ever missing). Server error codes stay codes; the page maps each one
to a message in both languages.

**Both languages are always accepted**, whichever one the page shows: "Germany" on a
Spanish page and "Alemania" on an English one, in the autocomplete, in Hard and in
Name them all, whose spell check also reads misspellings of both ("kazajastan",
"filipnas"). In the autocomplete the page's own language ranks first; a name in the
other language still matches, and shows as a hint beside the place it found. On top
of case, accents (ñ included) and punctuation, matching ignores:

- a leading article when more words follow: "the", and Spanish "el", "la", "los" and
  "las" ("los Estados Unidos", "el Perú", "las Bahamas"). Where the article is part
  of the name it still matches as typed ("El Salvador", "La Paz");
- the filler words "of", "and" and "the", and, inside a name, Spanish "de", "del",
  "y", "el", "la", "los" and "las" ("Trinidad Tobago", "República Congo");
- abbreviations: "St" is Saint, and "Sta." and "Sto." are Santa and Santo before
  another word ("Sta. Lucía", "Sto. Tomé y Príncipe").

**Numbers and dates** follow the locale. Spanish uses the browser's own Spanish
locale when its list has one (es-MX, es-ES, plain es), else es-US. English uses the
browser's first locale unless that is Spanish, then its English entry, else en-US.
So an average reads "4.3" in es-US and es-MX, as in English, and "4,3" in es-ES,
es-AR and plain es. Plurals follow each language's own rules (`Intl.PluralRules`),
and Name them all's lists sort in the locale's alphabetical order.

**No flash of English.** The server renders the page in English, with `data-i18n`
attributes beside every piece of text, and i18n.js fills them in the reader's
language. `<html class="gl-i18n-pending">` keeps the app hidden until main.js has
done that, so a Spanish reader never sees English first. If the script fails or
never runs, a CSS animation shows the page anyway after 1.5 s (reduced motion
shortens the animation, not the delay). There is still no inline script; the CSP is
unchanged.

**Adding or changing a string.** The words are `STRINGS.en` and `STRINGS.es` in
`static/globle/js/i18n.js`, under dotted keys ("end.found"). A value that counts
something is `{ one, other }`, and placeholders are `{name}`. Add the key to both,
use `t()` / `tn()` in a script or `data-i18n*` in the template, and run
`npm run check:i18n` (see [Data](#data)). Anything new that draws words must also be
redrawn when the language changes (`relocalize()` in main.js).

## How it works

```
 browser                                           server (cloud.gehan.globle)
 -------                                           ---------------------------
 templates/globle/index.html  <- GET /globle ----- GloblePageController   (main security chain)
 world.json  <- GET /globle/world.json             static file, 726 KiB TopoJSON
 main.js     -- GET/POST /globle/api/** ---------> GlobleApiController    (Globle's own chain)
                                                   RollCallApiController  /globle/api/rollcall/**, same chain
                                                   NameRequestApiController /globle/api/name-requests/**, same chain
                                                     GameService          rules, one write at a time
                                                     RollCallService      Name them all's rules, same lock
                                                     RollCallStudyService "Study what you missed", read-only
                                                     RollCallPools        each region's countries to name
                                                     NameRequestService   name-change requests, same lock
                                                     PlaceNames           match.js's exact matching, in Java
                                                     DailyPuzzles         each day's two answers, drawn once
                                                     AnswerPools          who can be drawn, and how likely
                                                     HomeService          the home screen's summary
                                                     GameViews            distances; hides the answer
                                                     LeaderboardService   counted in memory
                                                     CountryCatalog       countries.json + distances.json
                                                   globle_day, globle_hard_day, globle_game,
                                                   globle_name_request (SQLite, beside users)
```

On the page, `main.js` routes and draws, `rollcall-view.js` runs Name them all (its
rules, the spell check's included, are the pure `rollcall.js`), `match.js` finds the
place a player typed and measures how close a misspelling is, `study.js` draws "Study
what you missed" and `requests.js` the name-change requests (the words and rules of
both are the pure `study-logic.js`), `i18n.js` holds every word in both languages,
and `typing.js` keeps the globe above a phone's keyboard.

**The server is the source of truth.** Every guess, give-up and new game returns
the whole game as the server now sees it, and the page redraws from that. A reload,
a second device or a midnight rollover cannot leave the screen disagreeing with the
database. Name them all works the same way: every start, name and finish returns the
whole run.

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
flag stay on the server. The one exception is coarse and comes after the fact: "Study
what you missed" labels each country a finished Name them all run missed
`well-known` (tiers 1 and 2), `familiar` (3) or `obscure` (4 and 5). That is
acceptable because the tiers themselves are public in this repository
(`tools/globle`), it covers only a run that is over, and the label is three bands, not
the tier (see [Study what you missed](#study-what-you-missed)).

Name them all has no answer to hide, and which countries a region holds is public
too, so its rule is about what is shown. The server sends a run in progress no
`missed` list (it is null until the run is finished) and no difficulty tier, only
which of its codes were filled in, and the page never draws or names a country not
yet named, except one the spell check offers after Enter because it is close to what
was typed. A test checks that a run's start mentions no difficulty and no country it
did not fill in.

**Name-change requests** keep their rules in `NameRequestService`, under the same
write lock as the games, since the admin's reads may close requests. Who the admin is
comes from the session, the `ROLE_ADMIN` authority, exactly as for the user console
at `/users`; anyone else gets `404 not-found` from the admin's two routes and
`"admin": false, "nameRequests": null` on the home screen. Everything else is under
[Name-change requests](#name-change-requests).

**Distances are precomputed.** Border-to-border distance is expensive to compute
and impossible to approximate well with centre points: Russia's middle is nowhere
near Finland. So `tools/globle` measures every pair of Natural Earth shapes once,
counting only the parts the globe draws, and the server loads the 248 × 248
matrix at startup. It refuses to start if the matrix and the country list
disagree. Capital distances are cheap and computed on the fly with the haversine
formula.

**One write at a time.** Every Globle write runs in one transaction under a single
app-wide lock, so a double-tapped guess or name lands once, there is at most one
practice game in progress per slot, and at most one Name them all run in progress per
region. The lock is app-wide, not per player, because SQLite has one writer at a
time: two players' transactions open at once deadlock, and SQLite reports that with
an immediate `SQLITE_BUSY` instead of waiting, which the player would see as an
error. At family scale a guess waits a few milliseconds at most.
`SqliteWritesTest` checks this against a real SQLite file, including a classic and a
Hard daily started at the same moment and Name them all starts and names, since the
rest of the suite runs on H2. One app instance is assumed. The unique `daily_key`
column and the day tables' primary keys are the backstop if that ever changes.

A Name them all start saves the new run before it deletes the one it drops. SQLite
gives a new row one more than the largest id in the table, so in the other order the
new run could take over the dropped run's id, and a tab still showing the dropped run
would name into the new one. As it is, that tab gets `404 not-found` and reloads.

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
gets Europe's hardest. Name them all's fill-ins read the tiers too: "Easy ones" is
tier 1 and "Easy + well known" tiers 1 and 2.

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
  feels. Name them all's board is the same: nothing stops a player typing from a
  list.

### API

All JSON, all requiring a signed-in session. POSTs need the CSRF header.
`variant` is `classic` or `hard` wherever it appears. It is optional and means
`classic` when left out, so every request shape from before Hard keeps its meaning.
`region` is `world`, `africa`, `americas`, `asia`, `europe`, `oceania` or `mainland`.
On the `/rollcall` routes it means `world` when left out, and `autofill` is `off`,
`easy`, `known` or `mine`, meaning `off` when left out. Both ignore case and
surrounding spaces.

| Method | Path | Returns |
| --- | --- | --- |
| `GET` | `/globle/api/countries` | Every guessable place: code, English and Spanish name (`name`, `nameEs`), flag code, aliases in both languages (`aliases`, `aliasesEs`), region, whether it can be the answer, capital (`name`, `nameEs`, `aliases`, `aliasesEs`, `lat`, `lng`, or null) |
| `GET` | `/globle/api/home` | Today's puzzle number, date and next midnight; for each daily (`classic`, `hard`) its `status` (`NEW`, `PLAYING`, `WON`, `GAVE_UP`), `guessCount` and `currentStreak`; `inProgress`, your practice games in progress (`id`, `kind`, `region`, `variant`, `guessCount`), newest first; and `rollcall`, your Name them all runs in progress (`id`, `region`, `namedCount`, `total`), newest first. `inProgress` never holds a Name them all run. Then `admin`, whether you have the admin role, and `nameRequests`, how many name-change requests are open: a number for the admin, `null` for everyone else. Stores nothing, except that the admin's count first closes the requests the data already answers (see [Name-change requests](#name-change-requests)). |
| `GET` | `/globle/api/daily?variant=hard` | Today's game of that daily for you (playing with no guesses if you have not started) |
| `POST` | `/globle/api/daily/guess` `{"code":"FRA","date":"2026-10-09","variant":"hard"}` | The game after the guess |
| `POST` | `/globle/api/daily/give-up` `{"date":"2026-10-09","variant":"hard"}` | The game, finished, with the answer |
| `GET` | `/globle/api/unlimited/current?kind=country&region=europe&variant=classic` | Your game in progress in that slot, or `204`. Without `region`, your newest game in progress of that kind and variant in any region, which is how this answered before slots existed. |
| `POST` | `/globle/api/unlimited/new` `{"kind":"country","region":"mainland","variant":"classic"}` | A new practice game. A game in progress in the same slot is given up (or dropped, with no guesses). |
| `POST` | `/globle/api/unlimited/{id}/guess` `{"code":"FRA"}` | The game after the guess (for capitals, the code is the capital's country) |
| `POST` | `/globle/api/unlimited/{id}/give-up` | The game, finished, with the answer |
| `GET` | `/globle/api/leaderboard?variant=hard` | That daily's Today, Week, All time and your own stats (`variant` says which), all three practice boards under `unlimited`: `country`, `capital`, `hard`, and `rollcall`, the Name them all board, the same whichever `variant` is asked for |
| `GET` | `/globle/api/rollcall/options?region=europe` | What the setup card needs: how many countries each autofill would fill in there, and your record there from the runs that count (`RollCallOptions`, below) |
| `GET` | `/globle/api/rollcall/current?region=europe` | Your Name them all run in progress in that region (`RollCallState`, below), or `204` |
| `POST` | `/globle/api/rollcall/new` `{"region":"europe","autofill":"easy"}` | A new run, filled in as asked. Both fields are optional (`world`, `off`), and so is the body. A run in progress in that region is finished if anything was typed in it, and deleted otherwise. |
| `POST` | `/globle/api/rollcall/{id}/name` `{"code":"FRA"}` | The run after naming one country. Naming the last one finishes it. |
| `POST` | `/globle/api/rollcall/{id}/finish` | "I'm done": the run, finished, with `missed` |
| `GET` | `/globle/api/rollcall/study?region=europe` | "Study what you missed": your newest finished run in that region and what it missed, most worth learning first (`RollCallStudy`, below), or `204` when you have finished none there. Stores nothing. |
| `POST` | `/globle/api/name-requests` `{"code":"GBR","suggestion":"Great Britain and NI","lang":"en","source":"missed"}` | A name-change request (`NameRequest`, below): `201` when stored, `200` with the one already open when you send the same place and suggestion again. `code` is any place in the country list, territories included; `lang` is `en` or `es`; `source` is `named`, `missed`, `study` or `other`. |
| `GET` | `/globle/api/name-requests?status=open` | The admin only: everybody's requests, newest first, `open` (the default) or `all`. Closes first the open ones the data already answers. |
| `POST` | `/globle/api/name-requests/{id}/resolve` `{"status":"done"}` | The admin only: marks one `done`, `dismissed` or `open` again, and returns it. The status it already has changes nothing. |

Every game carries `variant` too. Hard is countries only: Hard capitals is refused.

A Name them all run (`RollCallState`):

```json
{"id": 3, "mode": "rollcall", "region": "oceania", "status": "PLAYING", "autofill": "easy",
 "total": 14,
 "named": [{"code": "AUS", "auto": true}, {"code": "NZL", "auto": true}, {"code": "FJI", "auto": false}],
 "namedCount": 3, "autoCount": 2,
 "byRegion": [{"region": "oceania", "named": 3, "total": 14}],
 "elapsedSeconds": 754, "startedAt": "2026-10-09T16:28:07Z", "finishedAt": null, "missed": null}
```

- `status` is `PLAYING` or `FINISHED`. `total` is how many countries the region holds.
- `named` is in the order named, the filled-in ones first (in catalogue order), each
  with `auto`. `namedCount` counts them all, and `autoCount` the filled-in ones.
- `byRegion` is the progress in each continent the region covers, always in the
  order `africa`, `americas`, `asia`, `europe`, `oceania`: all five for `world` and
  `mainland`, one for a continent.
- `elapsedSeconds` is whole seconds by the server's clock, up to now while playing
  and up to the finish after. `startedAt` and `finishedAt` (null while playing) are
  ISO-8601 instants to the millisecond, the same in every response for a run.
- `missed` is null while playing. Once the run is finished it lists every code of
  the region not named, sorted by code, and is `[]` when nothing was missed.

The setup card's numbers (`RollCallOptions`):

```json
{"region": "world", "total": 197, "easy": 30, "known": 70, "mine": 31, "finishedRuns": 2,
 "studyable": true, "best": {"named": 32, "total": 197, "seconds": 843, "autoCount": 30}}
```

- `easy`, `known` and `mine` are how many each autofill would fill in. `mine` is null
  until you have two runs that count in the region. A count equal to `total` is a
  choice the start refuses.
- `finishedRuns` is how many runs count there (finished, with at least one country
  typed), and `best` the best of them, by most named then fastest, or null. The
  field keeps its name, but runs with nothing typed are not in it.
- `studyable` is true when any run there has finished, counting or not, empty "I'm
  done" runs included: what `GET /rollcall/study` would study. The setup card offers
  "Study your last game" on it.

The leaderboard's `rollcall` is one row per player:
`{"username", "played", "best", "total", "bestSeconds", "bestAutoCount", "you"}`. See
[Leaderboard and streak rules](#leaderboard-and-streak-rules).

"Study what you missed" (`RollCallStudy`):

```json
{"runId": 41, "region": "africa", "finishedAt": "2026-10-13T19:02:11.250Z",
 "namedCount": 31, "total": 54, "missedCount": 23,
 "missed": [{"code": "GMB", "region": "africa", "lat": 13.498, "lng": -14.636,
             "nearby": [{"code": "SEN", "km": 0}, {"code": "GNB", "km": 61}, {"code": "GIN", "km": 74},
                        {"code": "MLI", "km": 189}],
             "nearestNamed": {"code": "SEN", "km": 0},
             "namedRate": 0.8182, "sampleRuns": 11, "yourRuns": 4, "yourMisses": 3,
             "familiarity": "obscure"}],
 "order": "named-rate"}
```

Every field is explained under How the numbers are worked out, in [Study what you
missed](#study-what-you-missed): the run is your newest finished one in the region,
`nearby` every answer country it borders and then the nearest others, to at least
four, `nearestNamed` the nearest one the run named, `namedRate` the share of the
region's counting runs that named it (null under three runs), and `familiarity`
`well-known`, `familiar` or `obscure` from its tier.

A name-change request (`NameRequest`), as sent back to its player and listed for the
admin:

```json
{"id": 7, "code": "GBR", "suggestion": "Great Britain and NI", "lang": "en", "source": "missed",
 "username": "jen", "createdAt": "2026-10-14T01:12:09.120Z", "status": "OPEN",
 "resolvedAt": null, "resolvedBy": null}
```

`status` is `OPEN`, `DONE` or `DISMISSED`; `resolvedAt` and `resolvedBy` (the admin's
username, or `data` when it closed by itself) are null while it is open.

The daily POSTs' `date` (`yyyy-MM-dd`) is optional: it is the puzzle the page is
showing, so a tab left open across midnight cannot guess or give up on a puzzle the
player has not seen. Without it the move applies to today.

Refusals: `400` with `unknown-country`, `already-guessed` (also a country already
named in a run, filled-in ones included), `finished` (also a finished run),
`no-capital`, `not-an-answer` (Name them all was sent a territory), `outside-region`
(Name them all was sent a country outside the run's region) or `bad-request` (also
for an unknown `kind`, `region`, `variant` or `autofill`, for Hard capitals, for
`mine` before it opens, for an autofill that would fill in the whole region, and for a
name-change request with a suggestion that is blank, too long or without a letter or
digit, an unknown code, or a `lang` or `source` not listed), or `too-many` (a name
change request from a player who already has 30 open; nothing is stored);
`404 not-found` for a game id that is not yours, and for an id of the other kind: a
Name them all run on the `/unlimited` routes, or a practice or daily game on the
`/rollcall` routes; also for the admin's name-change request routes asked by anyone
else, whatever they send, and for a request id that does not exist; `409 new-day` for
a daily move whose `date` is no longer today (nothing is stored; load the new
puzzle); `401 signed-out`; `403 forbidden`. A
refused move stores nothing, and a refused start leaves the run in progress as it
was. An unexpected failure is logged and answered `500 server`.

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
| **Name them all** | Its own board, beside the practice boards. Whole-world runs that count only: finished, with at least one country typed (a continent's 54 or 14 are not comparable with 197). One row per player: runs played, the best (most named, filled-in countries included), that run's time (the fastest among equal bests) and how many it filled in. Sorted by best, then time, then username. Runs in progress do not count, and no Name them all run touches a daily or practice board, streak or stat. |
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
API's filter chain and all four controllers (the page, the game's API, Name them
all's, and the name-change requests'). `/globle` and `/globle/api/**`,
`/globle/api/rollcall/**` (the study included) and `/globle/api/name-requests/**`
included, then answer 404 from the packaged jar, and the rest of the app behaves
exactly as it did before the game existed. `GlobleDisabledTest` boots the app this
way and checks it. Three things it cannot do on its own:

- **The launcher tile stays.** Tiles come from `apps.yml`. Comment out the Globle
  entry there too.
- **The four tables are still created**, empty, while the code is present.
  Hibernate scans every entity in the app's package.
- **The static files stay servable** to signed-in users, as any file under
  `static/` is.

Deleting the code (below) removes all three.

## Storage

Four tables beside `users` in `users.db`, all prefixed `globle_` and created by
Hibernate on startup:

| Table | One row per | Notes |
| --- | --- | --- |
| `globle_day` | Puzzle date | The day's classic answer code, its puzzle number, when it was drawn. |
| `globle_hard_day` | Puzzle date | The same for the Hard daily. |
| `globle_game` | Game or Name them all run | Owner (`user_id` + `username`, deliberately not a foreign key, so deleting a user is never blocked by their games), `mode` (`DAILY`, `UNLIMITED` or `ROLLCALL`), kind, region, `variant` (`CLASSIC` or `HARD`), `status` (`PLAYING`, `WON` or `GAVE_UP`; `PLAYING` or `FINISHED` for Name them all), the guesses as a comma-separated list of codes, timestamps. Daily rows have a `puzzle_date` and a unique `daily_key`: `userId/username/date` for classic, the same plus `/HARD` for Hard. Practice rows carry their own `answer_code`. Name them all rows have none of these: kind `COUNTRY`, the run's region, variant `CLASSIC`, `guesses` the countries named in order with the filled-in ones first, `guess_count` how many, and two columns of their own, `autofill` (`OFF`, `EASY`, `KNOWN` or `MINE`) and `autofill_count` (the first N codes were filled in). Both are null on every other row. |
| `globle_name_request` | Name-change request | Owner (`user_id` + `username`, not a foreign key, as for games: a deleted player's requests stay, under their name), `code` (any place in `countries.json`), `suggestion` (as typed, cleaned, 1 to 80 characters), `lang` (`EN` or `ES`), `source` (`NAMED`, `MISSED`, `STUDY` or `OTHER`), `created_at`, `status` (`OPEN`, `DONE` or `DISMISSED`), and, null while open, `resolved_at` and `resolved_by` (the admin's username, or `data`). A new table, so the columns always written are NOT NULL in its CREATE TABLE; no index, no foreign key and no CHECK constraint. |

Distances are not stored with guesses. They are recomputed from the data files on
every response, so a data correction fixes old games too.

`mode`, `status`, `autofill` and the other enums are stored as plain text through a
converter, not as a checked enum, so a new value such as `ROLLCALL` or `FINISHED`
needs no schema change. `FINISHED` is only ever a Name them all status, and a daily
or practice game never ends that way. The two roll call columns are nullable
wrapper types: `ddl-auto=update` adds them to a table that already has rows, and a
primitive would make Hibernate add a NOT NULL column with no default, which SQLite
refuses. Every lookup names its mode, so a Name them all row never reaches a daily
or practice board, the home screen's `inProgress`, or a practice slot.

**Why Hard has its own day table.** `globle_day` is keyed by the date alone, and
`ddl-auto=update` can add tables and columns but never change a primary key, so that
table can never hold two answers for one date. A second table needs nothing done by
hand. It also keeps the date as the key, which is what makes the first draw of a day
race-safe: two inserts for one date cannot both succeed.

### Upgrading

Neither earlier release needs anything done by hand, and no upgrade rewrites a row.
From either one, the first start also creates `globle_name_request`, empty.

**From the Hard release** (commit `13a0310`), the first start of this version adds
two nullable columns to `globle_game`, `autofill` and `autofill_count`, and the new
`globle_name_request` table, and nothing else: no index or constraint. Every existing
row has nulls there, which read as no autofill, and every old game reads and counts
as it did.
`HardReleaseUpgradeTest` boots the app on a SQLite file with that release's exact
schema and rows, checks this, and plays Name them all beside the old games.

**From the first release** (commit `59ea8d8`), which created `globle_day` and
`globle_game` without Hard, the first start adds three nullable columns to
`globle_game`, `autofill`, `autofill_count` and `variant` (Hibernate appends them in
that order), and creates `globle_hard_day` and `globle_name_request`. Nothing else in
the schema changes:

- A row with no `variant` is a classic game. Every lookup reads a missing variant
  that way. Old daily rows keep their `daily_key`, which is still the classic format,
  so they still hold their date.
- Streaks, boards and distributions carry straight on.
- A practice game left in progress resumes in its preset's slot. An old Capitals
  game started in a region resumes under Capitals, labelled "Capitals · Europe" for
  example.

`LiveDatabaseUpgradeTest` boots the app on a SQLite file with the first release's
exact schema and rows and checks all of this, and that Name them all works there.

**Rolling back to `13a0310`** needs no SQL. Every query in that release names the
mode it wants (`DAILY` or `UNLIMITED`) in the SQL itself, so it never loads a Name
them all row and never meets the `ROLLCALL` mode or the `FINISHED` status, which it
has no value for. It ignores the two extra columns, and the rows it writes leave them
null. Name them all runs stay in the table unread, and come back with the next
upgrade. `globle_name_request` is a table it has no entity for, so it never reads it
and Hibernate leaves it alone. This was checked by reading that release's code, not
by running it. To remove the runs anyway, with the app stopped:

```bash
sqlite3 "$APP_DATA_DIR/users.db" "DELETE FROM globle_game WHERE mode = 'ROLLCALL';"
```

**Rolling back to `59ea8d8`** needs the database cleaned first, once anyone has
played Hard or Mainland. The old version expects one daily row per player per date
and one practice game in progress per kind, and it fails on a region it does not
know. Name them all rows need nothing: that release, too, filters every query by
mode. With the app stopped:

```bash
sqlite3 "$APP_DATA_DIR/users.db" "
  DELETE FROM globle_game WHERE variant = 'HARD';
  UPDATE globle_game SET region = 'WORLD' WHERE region = 'MAINLAND' AND mode <> 'ROLLCALL';
  UPDATE globle_game SET status = 'GAVE_UP', finished_at = CAST(strftime('%s','now') AS INTEGER) * 1000
   WHERE mode = 'UNLIMITED' AND status = 'PLAYING' AND id NOT IN
         (SELECT max(id) FROM globle_game WHERE mode = 'UNLIMITED' AND status = 'PLAYING'
          GROUP BY user_id, username, kind);"
```

This deletes Hard history and gives up all but each player's newest practice game
per kind. It leaves Name them all runs as they were, so a Mainland run (API only)
is not turned into a whole-world one if the app is upgraded again. The extra columns,
`globle_hard_day` and `globle_name_request` are harmless to the old version.

### In the browser

The browser keeps a few per-device preferences in `localStorage` under keys starting
`globle.`: km or mi (`units`), whether the welcome was seen (`seenHelp`), the
leaderboard's and stats sheet's last choices (`boardTab`, `boardKind`, which may be
`rollcall`, `boardVariant`, `statsVariant`), the language (`lang`: `en` or `es`), and
Name them all's last autofill choice (`rollcallAutofill`: `off`, `easy`, `known` or
`mine`). `lang` and `rollcallAutofill` are written only when the player picks one,
never by a default or a fallback, and so are `boardTab` and `boardKind`: Name them
all's end card opens the board on its own tab for that viewing only. Keys from the first release (`mode`, `region.*`,
`blind`) are no longer read.

## Data

Three generated files, committed so the build needs no Node:

| File | Size | Used by |
| --- | --- | --- |
| `src/main/resources/globle/countries.json` | 78 KiB | Server: 248 places (codes, English and Spanish names, aliases in both languages, answer flag, region, difficulty, small-island flag, label point, capital with its names in both languages) |
| `src/main/resources/globle/distances.json` | 315 KiB | Server: the 248 × 248 border-to-border km matrix |
| `src/main/resources/static/globle/world.json` | 726 KiB | Browser: the globe's shapes (Natural Earth 1:50m, TopoJSON) |

To regenerate (Node 20+):

```bash
cd tools/globle
npm ci
npm run build       # writes the three files, deterministically
npm run check       # validates them, then Name them all's typing rules; non-zero exit on any failure
npm run check:i18n  # the page's English and Spanish agree; needs no npm ci
```

Then `./mvnw test`: `CountryCatalogTest` loads the real files and checks them
against each other. The server refuses to start on a `countries.json` without a
difficulty and small-island flag for every place, or without a Spanish name
(`nameEs`) on every place and capital. Keep country codes stable across rebuilds.
Stored games refer to places by code, and a game whose answer code disappeared could
not be shown.

Each entry of `countries.json` is `code`, `name`, `nameEs`, `iso2`, `aliases`,
`aliasesEs`, `answer`, `region`, `difficulty`, `smallIsland`, `lat`, `lng` and
`capital` (`name`, `nameEs`, `aliases`, `aliasesEs`, `lat`, `lng`, or null). The
Spanish names are written by hand in `tools/globle/lib/catalog-es.mjs`, one entry
per code: the build fails if a code is missing or extra, if a place has a Spanish
capital without an English one or the other way round, or if a name or alias in
either language would also name another place. On top, it borrows
`i18n-iso-countries`' Spanish names as aliases under the same rule as the English
ones. An old or rare alias that would start with another answer country's whole name
is left out ("Honduras Británica", "China Taipéi"), since Name them all would then
wait after "Honduras" or "China".

`npm run check` also proves the names against the page's matcher (`match.js`): every
name and alias in both languages finds its own place, with the page in either
language; typed Spanish forms resolve ("los Estados Unidos", "EE. UU.", "Sta.
Lucía", "Rep. Checa", "Germany" on a Spanish page); and the answer countries whose
name starts another's are exactly the Name them all pairs listed above. It then runs
`check-rollcall.mjs` (also `npm run check:rollcall`), which holds Name them all's
names that do not count and its spell check to their cases (see How well it spells,
under [Name them all](#name-them-all)), checks that the server's copy of those names
holds every one, and pins the study page's compass for the countries a box retune
could misplace. `npm run check:i18n`
loads `static/globle/js/i18n.js` on its own and fails if the two languages' keys
differ, a value is empty, a plural's shape or a `{placeholder}` differs between
them, or the template or a script asks for a key that does not exist.

Sources, licences and every judgement call the data makes are listed in
[tools/globle/README.md](../tools/globle/README.md). That covers which territories
are split from their sovereign, Crimea and Western Sahara, why Somaliland and
Northern Cyprus are part of Somalia and Cyprus, which islets too small for the
globe are left out of the distances, which capital a country with several gets,
why Australia–New Zealand is 616 km, every difficulty tier, how small island
nations are found, how the build keeps land borders out of the drawn coastline,
and the Spanish names (Islas Malvinas and Puerto Argentino, Birmania, Chequia,
Kiev, no "América" for the United States). Geometry is
[Natural Earth](https://www.naturalearthdata.com/) (public domain).

## Testing

```bash
./mvnw test
```

Everything for the game is under `src/test/java/cloud/gehan/globle/`. The suite
uses a movable clock registered only under the bean name `globleTimeSource`, which
nothing outside Globle looks for. It covers:

- the data files, the difficulty tiers and the Mainland pool;
- the Spanish names: every place and capital has one, the usual forms are the ones
  used, no name in either language names two places, and a `countries.json` without
  them is refused (`CountryCatalogTest`); and the country list's payload in both
  languages (`CountryListApiTest`);
- both daily draws, including that Hard never repeats the classic answer and draws
  from its tiers in the configured proportions;
- every API route and refusal, in both variants and every practice slot;
- Name them all (`RollCallApiTest`): every route and refusal, each autofill's count
  in the world, every continent and Mainland, what "easy" and "known" fill in and
  that they stay in the region, "mine" opening after two runs that count and what it
  fills in, the refusal of an autofill that would fill in the whole region, both
  abandon rules, finishing by naming the last country, runs with nothing typed
  counting nowhere, the server's stopwatch and the times a run sends, the best run,
  ownership, ids not crossing between modes, and that a run in progress says nothing
  about what is left;
- the Name them all board and home rows, that its runs touch no daily or practice
  board, streak, stat, home section or practice slot, nor the other way round, and
  that an account given a deleted account's id inherits none of its runs, finished
  or in progress (`RollCallBoardsTest`);
- "Study what you missed" (`RollCallStudyApiTest`): which run is studied (the newest
  finished, counting or not; `204` when there is none), that its missed list is the
  end card's, `nearby` and `nearestNamed` against the km matrix (answer countries
  only), the named rate over the region's counting runs by existing accounts only
  (null below three), your own record, rounding, the order, and that it stores nothing;
- name-change requests (`NameRequestApiTest`, `NameRequestRulesTest`): every refusal,
  cleaning and the 80-character limit, sending the same one twice, the limit of 30
  open, the admin's list, resolve and home count, `404` for everyone else whatever
  they send, `401` and CSRF on both POSTs, a real admin sign-in, closing by itself
  only on an exact name of that place alone, and never on a name Name them all
  refuses ("England", "America", "Zaire"); and `PlaceNamesTest`, the Java copy of
  `match.js`'s matching, pinned to its outputs;
- the home summary, and the leaderboard rules across days, per variant;
- rows written by the first release (`LegacyRowsTest`), and booting on its schema
  (`LiveDatabaseUpgradeTest`) and on the Hard release's (`HardReleaseUpgradeTest`),
  each with name requests and a study on the upgraded file and the new table's
  schema checked (nullable only where it must be, no CHECK constraint);
- the security chain (including a real form login) and the rendered page, with Name
  them all's plain box (no suggestions or datalist; autocomplete, autocorrect and
  spellcheck off; 1Password's and LastPass's opt-outs; `enterkeyhint="enter"`, never
  `done`), its six region buttons, its board tab and its stats line;
- simultaneous writes on a real SQLite file (`SqliteWritesTest`), Name them all
  included: a family playing it at once, many starts by one player leaving one run,
  a double-tapped name landing once, and a dropped run's id never handed to the next
  run; many name-change requests at once, a double-tapped one landing once and a
  player's limit of 30 holding however the sends interleave; and the app with the
  game switched off, the roll call, study and name request routes included.

The page itself has no browser test suite. `npm run check` exercises `match.js` over
the real data in both languages, and `rollcall.js`'s names that do not count and
spell check over it too (`check-rollcall.mjs`), and `npm run check:i18n` the two
dictionaries (see [Data](#data)).

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

7. **Optional, on the server:** the four tables are inert once the code is gone.
   Nothing reads them, and Hibernate's `ddl-auto=update` neither uses nor removes
   them. To reclaim the space:

   ```bash
   sqlite3 "$APP_DATA_DIR/users.db" 'DROP TABLE IF EXISTS globle_game; DROP TABLE IF EXISTS globle_day; DROP TABLE IF EXISTS globle_hard_day; DROP TABLE IF EXISTS globle_name_request;'
   ```

Browsers that played keep a few `globle.*` keys in `localStorage`. They are
harmless and nothing reads them.
