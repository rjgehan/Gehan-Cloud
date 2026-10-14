// The words on the page, in English and Spanish.
//
// Every string a player can see or hear goes through here: t("key") for one
// string, tn("key", n) for one that depends on a count. The template keeps its
// English text (the page renders, and its tests read, without any script) and
// marks each piece with data-i18n attributes, which applyTranslations() fills
// from the dictionary in the reader's language.
//
// Pure apart from applyTranslations() and the <html lang> that setLang() keeps in
// step, and both only touch the DOM when called, with a document present. It
// imports nothing, so Node can load the dictionaries on their own: the parity
// check in tools/globle (npm run check:i18n) imports this file as a data: URL and
// fails if the two languages' keys, emptiness or {placeholders} ever disagree.
//
// Adding a string: add the key to BOTH dictionaries, then use t()/tn() in code, or
// data-i18n* in the template. Keys are dotted and grouped by where they appear
// ("end.found", "board.col.avg"); a key's value is either a string or, when it
// counts something, a plural object { one, other } (Spanish and English need no
// other categories; a missing category falls back to "other"). Placeholders are
// {name}; every form of a key, in both languages, uses the same set. The language
// preference itself (localStorage globle.lang) is read and written by main.js
// through prefs.js, which keeps this file free of imports.

export const LANGS = ["en", "es"];

/** The attributes applyTranslations() fills, each from data-i18n-<attribute>. */
export const I18N_ATTRIBUTES = ["title", "aria-label", "placeholder", "aria-roledescription"];

export const STRINGS = {
    en: {
        // ---------------------------------------------------------------- common
        "common.close": "Close",
        "common.cancel": "Cancel",
        "common.ok": "OK",
        "common.areYouSure": "Are you sure?",
        "common.tryAgain": "Try again",
        "common.reload": "Reload",
        "common.loading": "Loading…",
        "common.play": "Play",
        "common.practice": "Practice",
        "common.daily": "Daily",
        // How a capital is named alongside its country: "Paris, France".
        "place.capitalOf": "{capital}, {country}",
        // A country's name inside a sentence, for the ones that need an article there
        // ("bordering the United States"); read through names.js nameInSentence(), which
        // falls back to the plain name. Keys built at run time: keep the same codes in
        // both languages (Spanish repeats the plain name where it takes no article).
        "place.inSentence.USA": "the United States",
        "place.inSentence.GBR": "the United Kingdom",
        "place.inSentence.NLD": "the Netherlands",
        "place.inSentence.DOM": "the Dominican Republic",
        "place.inSentence.CAF": "the Central African Republic",
        "place.inSentence.COD": "the DR Congo",
        "place.inSentence.COG": "the Republic of the Congo",
        "place.inSentence.ARE": "the United Arab Emirates",
        "place.inSentence.GMB": "the Gambia",
        "place.inSentence.PHL": "the Philippines",
        "place.inSentence.BHS": "the Bahamas",
        "place.inSentence.MDV": "the Maldives",
        "place.inSentence.MHL": "the Marshall Islands",
        "place.inSentence.SLB": "the Solomon Islands",
        "place.inSentence.COM": "the Comoros",
        "place.inSentence.SYC": "the Seychelles",

        // ---------------------------------------------------------------- language
        // lang.* describe each language in itself: the switch to Spanish says
        // "Cambiar a español" whatever language the page is in.
        "lang.name": "English",
        "lang.code": "EN",
        "lang.switchTo": "Switch to English",
        "lang.group": "Language",
        "lang.changed": "Language: English",

        // ---------------------------------------------------------------- header, session
        "nav.back": "Back to Gehan.Cloud",
        "nav.board": "Leaderboard",
        "nav.stats": "Your stats",
        "nav.help": "How to play",
        "nav.game": "Game",
        "nav.home": "Home",
        "session.expired": "Your session has expired, so guesses can't be saved.",
        "session.stale": "This page is out of date, so guesses can't be saved.",
        "session.signIn": "Sign in again",
        "session.announce": "Your session has expired. Sign in again to keep playing.",

        // ---------------------------------------------------------------- globe
        "stage.label": "Globe",
        "stage.canvas": "Globe of your guesses. Drag to spin, scroll or pinch to zoom. With the globe focused, arrow keys rotate, plus and minus zoom, and 0 resets.",
        "stage.role": "globe",
        "stage.blind": "Blind",
        "stage.zoomIn": "Zoom in",
        "stage.zoomOut": "Zoom out",
        "stage.reset": "Reset view",
        "stage.hintTouch": "Drag to spin · pinch to zoom",
        "stage.hintMouse": "Drag to spin · scroll to zoom",
        "stage.loading": "Loading the world…",
        "stage.failed": "Couldn't load the globe.",
        "tip.answer": "The answer",
        "tip.answerFound": "The answer!",

        // ---------------------------------------------------------------- home
        "home.title": "Choose a game",
        "home.failed": "Couldn't load today's status.",
        "home.next": "New puzzles in",
        "home.more": "More ways to play",
        "home.otherRegions": "Other regions",
        "home.credit": "Inspired by",
        "home.statusUnknown": "Status unknown",
        "daily.number": "Daily #{n}",
        "daily.streakSr": { one: ", {n}-day streak", other: ", {n}-day streak" },

        // ---------------------------------------------------------------- modes
        "variant.classic": "Classic",
        "variant.hard": "Hard",
        "practice.country": "Unlimited",
        "practice.capital": "Capitals",
        "practice.hard": "Hard practice",
        "preset.world.label": "Unlimited",
        "preset.world.sub": "Any country, any time",
        "preset.europe.label": "Europe only",
        "preset.europe.sub": "Countries in Europe",
        "preset.mainland.label": "Mainland only",
        "preset.mainland.sub": "No tiny island nations",
        "preset.capitals.label": "Capitals",
        "preset.capitals.sub": "Find the capital city",
        "preset.hard.label": "Hard practice",
        "preset.hard.sub": "Blind globe, no suggestions",
        "preset.africa.label": "Africa only",
        "preset.africa.sub": "Countries in Africa",
        "preset.americas.label": "Americas only",
        "preset.americas.sub": "Countries in the Americas",
        "preset.asia.label": "Asia only",
        "preset.asia.sub": "Countries in Asia",
        "preset.oceania.label": "Oceania only",
        "preset.oceania.sub": "Countries in Oceania",
        "region.world": "World",
        "region.africa": "Africa",
        "region.americas": "Americas",
        "region.asia": "Asia",
        "region.europe": "Europe",
        "region.oceania": "Oceania",
        "region.mainland": "Mainland",
        "region.antarctica": "Antarctica",
        "mode.daily": "{name} · Daily #{n}",
        "mode.dailyPlain": "{name} · Daily",
        "mode.regional": "{label} · {region}",
        "status.play": "Play",
        "status.won": "Solved in {n} ✓",
        "status.gaveUp": "Gave up",
        "status.playing": { one: "In progress · {n} guess", other: "In progress · {n} guesses" },
        "status.resume": "Resume",
        "status.resumeCount": { one: "Resume · {n} guess", other: "Resume · {n} guesses" },
        "count.guesses": { one: "{n} guess", other: "{n} guesses" },

        // ---------------------------------------------------------------- game panel
        "panel.label": "Your game",
        "panel.practice": "Practice — doesn't count toward streaks.",
        "panel.start": "Start a game",
        "panel.loadingGame": "Loading your game…",
        "panel.loadingList": "Loading the country list…",
        "panel.listFailed": "Couldn't load the country list.",
        "panel.gameFailed": "Couldn't load this game.",
        "guess.label": "Guess a country",
        "guess.labelCapital": "Guess a capital city",
        "guess.placeholder": "Guess a country…",
        "guess.placeholderCapital": "Guess a capital…",
        "guess.placeholderHard": "Type a country, then Enter",
        "guess.go": "Guess",
        "guess.help": "Type to see matching places, use the arrow keys to choose, and Enter to guess.",
        "guess.helpHard": "No suggestions in Hard: type the country's name and press Enter.",
        "guess.choose": "Did you mean {name}? Pick it from the list, or press Enter again.",
        "guess.noCountry": "No country matches that. Try another spelling.",
        "guess.noCapital": "No capital matches that. Try another spelling.",
        "guess.listLoading": "Still loading the country list — try again in a moment.",
        "guess.notRecognised": "Not recognised — check the spelling",
        "ac.guessed": "Guessed",
        "ac.neverAnswer": "Never the answer",
        "closest.border": "Closest border",
        "closest.capital": "Closest capital",
        "closest.begin": "Guess any country to begin",
        "closest.beginCapital": "Guess any capital to begin",
        "list.heading": "Guesses",
        "list.units": "Distance units",
        "list.label": "Your guesses, closest first",
        "list.empty": "No guesses yet.",
        "list.found": "Found!",
        "list.order": "Guess {n}",
        "list.latest": "(latest guess)",
        "actions.newGame": "New game",
        "actions.giveUp": "Give up",

        // ---------------------------------------------------------------- end card
        "end.firstTry": "First try. Unreal!",
        "end.found": "You found it!",
        "end.answerWas": "The answer was",
        "end.capitalOf": "Capital of {name}",
        "end.capital": "Capital: {name}",
        "end.foundIn": { one: "Found in {n} guess", other: "Found in {n} guesses" },
        "end.revealedAfter": { one: "Revealed after {n} guess", other: "Revealed after {n} guesses" },
        "end.distribution": "Guess distribution",
        "end.next": "Next puzzle in",
        "end.newReady": "A new puzzle is ready",
        "end.share": "Share",
        "end.copied": "Copied!",
        "end.playHard": "Play today's Hard",
        "end.home": "Back home",
        "end.copyLabel": "Result to copy",
        "end.record": "Your {name} record: {summary}",
        "end.emptyHard": "Win a Hard daily to start your chart.",

        // ---------------------------------------------------------------- errors, by code
        // The server answers {"error":"<code>"}; api.js adds the transport codes.
        "error.offline": "You seem to be offline, so nothing was sent. Try again once you're connected.",
        "error.timeout": "The server took too long to answer. Your game has been reloaded to be sure.",
        "error.server": "The server hit a problem. Try again in a moment.",
        "error.bad-response": "The server sent something unexpected. Try reloading the page.",
        "error.generic": "Something went wrong. Try again.",
        "error.unknown-country": "That isn't a country we know.",
        "error.unknown-capital": "That isn't a capital we know. Pick one from the list.",
        "error.already-guessed": "You already guessed {name}.",
        "error.no-capital": "{name} has no capital to guess. Try another.",
        "error.finished": "That game already ended (maybe on another device). Loading your current one.",
        "error.not-found": "That game could not be found. Loading your current one.",
        "error.new-day": "A new puzzle started at midnight. Nothing was counted.",
        "error.bad-request": "That didn't go through. Try again.",
        // Name them all's refusals: the place named does not count in this game.
        "error.not-an-answer": "{name} is a territory, so it doesn't count.",
        "error.outside-region": "{name} isn't in {region}.",

        // ---------------------------------------------------------------- toasts, announcements
        "toast.otherDevice": "You have a game in progress from another device.",
        "toast.endedElsewhere": "That game ended on another device.",
        "toast.newDay": "A new day has started. This is {name} #{n}.",
        "toast.lastGivenUp": "Your last game counted as given up.",
        "toast.copied": "Copied! Paste it in the family chat.",
        "toast.copyFailed": "Couldn't copy automatically. Select the text and copy it.",
        "announce.distance": "{name}: {distance} away.",
        "announce.borders": "It borders the mystery country.",
        "announce.closest": "Closest so far: {name}, {distance}.",
        "announce.correct": { one: "Correct! It was {name}. Found in {n} guess.", other: "Correct! It was {name}. Found in {n} guesses." },
        "announce.answerWas": "The answer was {name}.",
        "announce.newGame": "New {mode} game. Make your first guess.",

        // ---------------------------------------------------------------- confirmations
        "confirm.giveUp.title": "Give up?",
        "confirm.giveUp.daily": "The answer will be revealed and today's puzzle counts as a loss.",
        "confirm.giveUp.dailyStreak": {
            one: "The answer will be revealed and today's puzzle counts as a loss, ending your {n}-day streak.",
            other: "The answer will be revealed and today's puzzle counts as a loss, ending your {n}-day streak.",
        },
        "confirm.giveUp.hard": "The answer will be revealed and today's Hard puzzle counts as a loss.",
        "confirm.giveUp.hardStreak": {
            one: "The answer will be revealed and today's Hard puzzle counts as a loss, ending your {n}-day streak.",
            other: "The answer will be revealed and today's Hard puzzle counts as a loss, ending your {n}-day streak.",
        },
        "confirm.giveUp.practice": "The answer will be revealed and this game counts as a loss.",
        "confirm.giveUp.ok": "Reveal the answer",
        "confirm.keepPlaying": "Keep playing",
        "confirm.newGame.title": "Start a new game?",
        "confirm.newGame.body": {
            one: "This one counts as given up after {n} guess. The answer won't be shown.",
            other: "This one counts as given up after {n} guesses. The answer won't be shown.",
        },
        "confirm.newGame.ok": "Start new game",

        // ---------------------------------------------------------------- welcome, help
        "welcome.body": "Find the mystery country. Each guess is coloured by how close it is.",
        "legend.far": "Far",
        "legend.close": "Close",
        "legend.found": "Found",
        "help.lede": "Find the mystery country. Each guess is coloured by how close it is; neighbours are 0 km.",
        "help.classic": "one country a day, the same for everyone.",
        "help.hard": "a second daily: blank globe, rarer countries, no suggestions.",
        "help.practice": "unlimited rounds by region or capital. No streaks.",
        "help.rollcall": "how many countries can you name?",
        "help.controls": "Drag to spin, pinch to zoom. Shares show colours, never names.",

        // ---------------------------------------------------------------- stats
        "tiles.played": "Played",
        "tiles.winRate": "Win %",
        "tiles.streak": "Streak",
        "tiles.maxStreak": "Best streak",
        "tiles.avg": "Avg guesses",
        "dist.label": "Daily wins by number of guesses",
        "dist.bucket": { one: "{bucket} guess", other: "{bucket} guesses" },
        "dist.wins": { one: "{n} win", other: "{n} wins" },
        "dist.today": "(today)",
        "dist.empty": "Win a daily puzzle to start your chart.",
        "summary.none": "No finished games yet.",
        "summary.wins": { one: "{n} win from {played}", other: "{n} wins from {played}" },
        "summary.avg": "avg {avg}",
        "summary.best": "best {n}",

        // ---------------------------------------------------------------- leaderboard
        "board.periods": "Leaderboard period",
        "board.today": "Today",
        "board.week": "Week",
        "board.all": "All time",
        "board.unlimited": "Unlimited",
        "board.gameType": "Game type",
        "board.countries": "Countries",
        "board.capitals": "Capitals",
        "board.failed": "Couldn't load the leaderboard.",
        "board.noPuzzle": "No puzzle today.",
        "board.todayMeta.classic": "Puzzle #{n} · {date}",
        "board.todayMeta.hard": "Hard #{n} · {date}",
        "board.lock": "Still playing? Only guess counts show here, never the answer.",
        "board.nobody": "Nobody has guessed yet today. Be the first!",
        "board.rank": "Rank {n}",
        "board.won": "Won",
        "board.playing": "Playing",
        "board.gaveUp": "Gave up",
        "board.you": "you",
        "board.week.meta.classic": "Daily puzzles, last 7 days",
        "board.week.meta.hard": "Hard dailies, last 7 days",
        "board.week.empty.classic": "No daily puzzles in the last seven days.",
        "board.week.empty.hard": "No hard dailies in the last seven days.",
        "board.week.caption": "Daily results for the last seven days",
        "board.all.meta.classic": "Daily puzzles, all time",
        "board.all.meta.hard": "Hard dailies, all time",
        "board.all.empty.classic": "No daily puzzles yet.",
        "board.all.empty.hard": "No hard dailies yet.",
        "board.all.caption": "Daily results, all time",
        "board.practice.meta": "{name}, all finished games",
        "board.practice.empty": "No finished {name} games yet.",
        "board.practice.caption": "{name} results",
        "board.col.player": "Player",
        "board.col.played": "Played",
        "board.col.playedTitle": "Games played",
        "board.col.wins": "Wins",
        "board.col.winsTitle": "Games won",
        "board.col.winRate": "Win %",
        "board.col.avg": "Avg",
        "board.col.avgTitle": "Average guesses per win",
        "board.col.streak": "Streak",
        "board.col.streakTitle": "Current streak (best)",
        "board.col.best": "Best",
        "board.col.bestTitle": "Fewest guesses",

        // ---------------------------------------------------------------- Name them all
        // A game of naming every country you can, in the world or one continent
        // (rollcall-view.js). "Game" here is one run of it.
        "rollcall.name": "Name them all",
        "rollcall.sub": "How many countries can you name?",
        "rollcall.resume": { one: "Resume · {n} named", other: "Resume · {n} named" },
        "rollcall.best": "Best {named}/{total}",
        "rollcall.inProgress": "(game in progress)",
        "rollcall.setup.lede": "Type countries one at a time; each one lights up on the globe. No time limit, but the clock runs.",
        "rollcall.setup.region": "Where",
        "rollcall.setup.autofill": "Fill in for me",
        "rollcall.setup.best": "Your best here: {named}/{total} in {time}",
        "rollcall.setup.bestAuto": { one: "({n} filled in)", other: "({n} filled in)" },
        "rollcall.setup.noBest": "No record here yet. A game counts once you type at least one country.",
        "rollcall.setup.start": "Start",
        "rollcall.setup.failed": "Couldn't load your choices.",
        "rollcall.setup.unavailable": "That choice isn't open any more. Pick another.",
        "rollcall.autofill.off": "Off",
        "rollcall.autofill.easy": "Easy ones",
        "rollcall.autofill.known": "Easy + well known",
        "rollcall.autofill.mine": "My sure things",
        "rollcall.autofill.offNote": "type every one yourself",
        "rollcall.autofill.mineNote": "named in each of your last {n} games",
        "rollcall.autofill.locked": "after 2 games",
        "rollcall.autofill.lockedMore": { one: "after {n} more game", other: "after {n} more games" },
        "rollcall.autofill.full": "that's all of them — nothing left to name",
        "rollcall.play.count": "{n} of {total} named",
        "rollcall.play.time": "Time",
        "rollcall.play.progress": "By continent",
        "rollcall.play.regionCount": "{region}: {n} of {total}",
        "rollcall.input.label": "Name a country",
        "rollcall.input.placeholder": "Type a country…",
        "rollcall.input.go": "Enter",
        "rollcall.named": "{name}: named. {n} of {total}.",
        "rollcall.already": "You already named {name}.",
        "rollcall.notSaved": "{name} wasn't saved.",
        "rollcall.notSavedMore": { one: "{name} and {n} more weren't saved.", other: "{name} and {n} more weren't saved." },
        // The box's help (read out with it) now that Enter also checks a spelling.
        "rollcall.input.helpLenient": "No list while you type: a country counts as soon as its name is complete. Press Enter if it waits, or to check a spelling.",
        // What doesn't count, typed exactly ({n} is the number of countries, 197).
        "rollcall.notCountry": "{name} doesn't count: it isn't one of the {n} countries.",
        "rollcall.part": "{part} is part of {name}, so it doesn't count — name the country.",
        "rollcall.former": "{old} is an old name for {name}, so it doesn't count — use today's name.",
        // The same for one country whose name needs an article in a sentence
        // (rollcall-view.js forPlace(): "<key>.<code>" is used when it exists).
        "rollcall.part.GBR": "{part} is part of the United Kingdom, so it doesn't count — name the country.",
        "rollcall.part.VCT": "The Grenadines are part of Saint Vincent and the Grenadines, so they don't count — name the country.",
        "rollcall.former.COD": "{old} is an old name for the DR Congo, so it doesn't count — use today's name.",
        // How a part is written inside those sentences when it needs an article, keyed by
        // its compact form (rollcall-view.js explanation(): "rollcall.term.<compact>").
        "rollcall.term.westbank": "the West Bank",
        "rollcall.term.cisjordania": "the West Bank",
        "rollcall.term.franjadegaza": "the Gaza Strip",
        // The spell check: a misspelling it is sure of ("✓ Kyrgyzstan (from “kyrigistan”)"),
        // one it offers ("Did you mean [Nauru]?", {options} being one or two buttons), and
        // what it found that doesn't count.
        "rollcall.from": "(from “{text}”)",
        "rollcall.namedFrom": "{name}: named, from “{text}”. {n} of {total}.",
        "rollcall.didYouMean": "Did you mean {options}?",
        "rollcall.or": "or",
        // ...before a name that starts with an "o" sound (Spanish "o" becomes "u": "Rumania u Omán").
        "rollcall.orBeforeO": "or",
        "rollcall.suggest.enter": "Press Enter to count it.",
        "rollcall.fuzzy.already": "Did you mean {name}? You already named it.",
        "rollcall.fuzzy.notCountry": "Did you mean {name}? It doesn't count: it isn't one of the {n} countries.",
        "rollcall.fuzzy.outside": "Did you mean {name}? It isn't in {region}.",
        "rollcall.fuzzy.part": "Did you mean {part}? It's part of {name}, so it doesn't count — name the country.",
        "rollcall.fuzzy.former": "Did you mean {old}? It's an old name for {name}, so it doesn't count — use today's name.",
        "rollcall.fuzzy.part.GBR": "Did you mean {part}? It's part of the United Kingdom, so it doesn't count — name the country.",
        "rollcall.fuzzy.former.COD": "Did you mean {old}? It's an old name for the DR Congo, so it doesn't count — use today's name.",
        // A region inside a sentence ("isn't in the Americas"), where its label alone reads wrong.
        "rollcall.in.americas": "the Americas",
        "rollcall.list.named": "Named",
        "rollcall.list.missed": "Missed",
        "rollcall.list.empty": "Nothing named yet. Start typing!",
        "rollcall.list.auto": "filled in",
        "rollcall.list.saving": "saving",
        "rollcall.list.other": "Other",
        "rollcall.done": "I'm done",
        "rollcall.startOver": "Start over",
        "rollcall.confirm.doneTitle": "Finish this game?",
        "rollcall.confirm.doneBody": "You've named {n} of {total} in {time}. The ones you missed will be shown.",
        "rollcall.confirm.doneEmpty": "You haven't typed any yet, so this game won't count.",
        "rollcall.confirm.doneOk": "I'm done",
        "rollcall.confirm.keepGoing": "Keep going",
        "rollcall.confirm.overTitle": "Start over?",
        "rollcall.confirm.overBody": "This game ends now and counts: {n} of {total} in {time}.",
        "rollcall.confirm.overEmpty": "You haven't typed any yet, so this game is just dropped.",
        "rollcall.confirm.overOk": "Start over",
        "rollcall.end.kicker": "Game over",
        "rollcall.end.all": "Every single one!",
        "rollcall.end.title": "You named {n} of {total}",
        "rollcall.end.time": "Time {time}",
        "rollcall.end.auto": { one: "{n} filled in automatically", other: "{n} filled in automatically" },
        "rollcall.end.noCount": "You didn't type any, so this game doesn't count.",
        "rollcall.end.newBest": "A new personal best!",
        "rollcall.end.again": "Play again",
        "rollcall.tip.named": "Named",
        "rollcall.tip.auto": "Filled in",
        "rollcall.tip.missed": "Missed",
        "rollcall.toast.otherDevice": "You have a game in progress here from another device.",
        "rollcall.toast.ended": "That game ended on another device.",
        "rollcall.toast.counted": "Your last game counted: {n} of {total}.",
        "rollcall.announce.start": { one: "{mode}: {n} country to name. Start typing.", other: "{mode}: {n} countries to name. Start typing." },
        "rollcall.announce.done": "Game over: you named {n} of {total} in {time}.",
        "rollcall.board.meta": "Whole world: each player's best finished game",
        "rollcall.board.empty": "No finished whole-world games yet.",
        "rollcall.board.caption": "Name them all results",
        "rollcall.board.col.best": "Best",
        "rollcall.board.col.bestTitle": "Most countries named in one game",
        "rollcall.board.col.time": "Time",
        "rollcall.board.col.timeTitle": "How long that game took",
        "rollcall.board.col.auto": "Auto",
        "rollcall.board.col.autoTitle": "Countries filled in automatically in that game",
        "rollcall.stats": "best {named}/{total} · {time}",
        "rollcall.stats.none": "No whole-world record yet.",

        // round 2: name-change requests and study page
        // Study what you missed (study.js, study-logic.js). The compass phrase is
        // "In the north-west" + an area phrase that brings its own "of" ("of Africa",
        // "of the Caribbean"), so Spanish can say "del Caribe".
        "study.badge": "Study",
        "study.open": "Study what you missed",
        "study.last": "Study your last game",
        "study.hint": "Tap a country to see it on the globe, with the countries around it.",
        "study.failed": "Couldn't load your last game.",
        "study.none": "No finished game here yet. Play one, then come back to study the ones you missed.",
        "study.allNamed": "You named all {total} in your last game here. Nothing to study!",
        // Plurals by {missed}, for Spanish's "te faltó 1" / "te faltaron 16".
        "study.summary.world": { one: "Your last whole-world game ({when}): you named {named} of {total} and missed {missed}.", other: "Your last whole-world game ({when}): you named {named} of {total} and missed {missed}." },
        "study.summary.region": { one: "Your last game in {region} ({when}): you named {named} of {total} and missed {missed}.", other: "Your last game in {region} ({when}): you named {named} of {total} and missed {missed}." },
        "study.showAll": { one: "Show all {n}", other: "Show all {n}" },
        "study.show": "Show {name} on the globe",
        "study.showSr": "(show on the globe)",
        "study.tip.selected": "Studying",
        "study.tip.borders": "Borders {name}",
        "study.compass.northWest": "In the north-west {area}",
        "study.compass.north": "In the north {area}",
        "study.compass.northEast": "In the north-east {area}",
        "study.compass.west": "In the west {area}",
        "study.compass.centre": "In the middle {area}",
        "study.compass.east": "In the east {area}",
        "study.compass.southWest": "In the south-west {area}",
        "study.compass.south": "In the south {area}",
        "study.compass.southEast": "In the south-east {area}",
        "study.area.africa": "of Africa",
        "study.area.europe": "of Europe",
        "study.area.asia": "of Asia",
        "study.area.oceania": "of Oceania",
        "study.area.northAmerica": "of North America",
        "study.area.centralAmerica": "of Central America",
        "study.area.caribbean": "of the Caribbean",
        "study.area.southAmerica": "of South America",
        "study.area.americas": "of the Americas",
        "study.where.borders": "{where}, bordering {list}.",
        "study.where.bordersOnly": "Bordering {list}.",
        "study.where.nearest": "{where}. Nearest country: {name}, {km} away.",
        "study.where.nearestOnly": "Nearest country: {name}, {km} away.",
        "study.where.only": "{where}.",
        "study.yourNearest.border": "Next to {name}, which is on your list.",
        "study.yourNearest.km": "Closest on your list: {name}, {km} away.",
        // How often it is named, by the games (runs) here that count, filled-in countries
        // counting as named: more than half of them, exactly half, fewer than half.
        "study.often.most": { one: "Usually named: in {named} of {n} game here.", other: "Usually named: in {named} of {n} games here." },
        "study.often.half": { one: "Named in half the games here: {named} of {n}.", other: "Named in half the games here: {named} of {n}." },
        "study.often.few": { one: "Usually missed: named in only {named} of {n} game here.", other: "Usually missed: named in only {named} of {n} games here." },
        "study.often.nobody": { one: "Nobody has named it yet in {n} game.", other: "Nobody has named it yet in {n} games." },
        "study.often.wellKnown": "Not enough games yet to say, but it's one of the well-known ones.",
        "study.often.familiar": "Not enough games yet to say; it's fairly well known.",
        "study.often.obscure": "Not enough games yet to say, but it's one of the less-known ones.",
        // ...One: the player has just one game here that counts.
        "study.yours.neverMissedOne": "You named it in your other game here.",
        "study.yours.neverMissed": { one: "You named it in each of your {n} other games here.", other: "You named it in each of your {n} other games here." },
        "study.yours.alwaysOne": "You missed it in your only game here.",
        "study.yours.always": { one: "You've missed it in every one of your {n} games here.", other: "You've missed it in every one of your {n} games here." },
        "study.yours.some": { one: "You've missed it in {missed} of your {n} game here.", other: "You've missed it in {missed} of your {n} games here." },
        // Request a name change (requests.js): the pencil, its sheet, and why a send failed.
        "request.button": "Request a name change for {name}",
        "request.titlePlain": "Suggest a name",
        "request.title": "Suggest a name for {name}",
        "request.lede": "Type the spelling you think should count. It goes to whoever looks after the game, for the next update.",
        "request.label": "Spelling that should count",
        "request.placeholder": "e.g. Great Britain",
        "request.recent": "Not recognised in this game:",
        "request.use": "Use “{text}”",
        "request.send": "Send",
        "request.sent": "Thanks — sent for review.",
        "request.error.empty": "Type the spelling you think should count.",
        "request.error.tooLong": "That's too long: 80 characters at most.",
        "request.error.tooMany": "You already have 30 suggestions waiting for review. Try again once some have been looked at.",
        "request.error.bad": "That can't be sent as it is. Use letters or numbers, 80 characters at most.",
        "request.error.signedOut": "Your session has expired, so this wasn't sent. Sign in again first.",
        "request.error.stale": "This page is out of date, so this wasn't sent. Reload the page and try again.",
        "request.error.offline": "You seem to be offline, so nothing was sent. Try again once you're connected.",
        "request.error.timeout": "The server took too long to answer. Try again; it won't be sent twice.",
        "request.error.generic": "Something went wrong, so nothing was sent. Try again.",
        "request.source.named": "typed on the named list",
        "request.source.missed": "typed on the missed list",
        "request.source.study": "typed on the study page",
        "request.source.other": "typed elsewhere",
        "request.lang.en": "English page",
        "request.lang.es": "Spanish page",
        // The admin's list (#/requests) and the quiet count on home.
        "requests.badge": "Name requests",
        "requests.title": "Name-change requests",
        "requests.lede": "Names players think should count. Copy them into the next change to the data; a request the data already accepts closes by itself.",
        "requests.showResolved": "Show resolved",
        "requests.copyAll": "Copy all",
        "requests.copyLabel": "Requests to copy",
        "requests.copied": { one: "Copied {n} request.", other: "Copied {n} requests." },
        "requests.homeCount": { one: "{n} name-change request to review", other: "{n} name-change requests to review" },
        "requests.failed": "Couldn't load the requests.",
        "requests.empty": "No requests waiting.",
        "requests.emptyAll": "No requests yet.",
        "requests.by": "by {name}",
        "requests.suggested": "Suggested name:",
        "requests.cleaned": "(hidden characters removed)",
        "requests.state.done": "Done by {name}",
        "requests.state.data": "Closed by itself: the data already accepts it",
        "requests.state.dismissed": "Dismissed by {name}",
        "requests.done": "Done",
        "requests.dismiss": "Dismiss",
        "requests.reopen": "Reopen",
        "requests.doneFor": "Done: {name}, “{suggestion}”",
        "requests.dismissFor": "Dismiss: {name}, “{suggestion}”",
        "requests.reopenFor": "Reopen: {name}, “{suggestion}”",
        "requests.toast.done": "Request for {name}: done.",
        "requests.toast.dismissed": "Request for {name}: dismissed.",
        "requests.toast.reopened": "Request for {name}: reopened.",
        "requests.gone": "That request is gone. The list has been reloaded.",
        // Copy all: plain text for a chat. {suggestion} arrives already in quotes.
        "requests.copy.heading": { one: "Globle name-change requests ({n}):", other: "Globle name-change requests ({n}):" },
        "requests.copy.line": "{code} {name} ← {suggestion} (by {user}, {date}, {source}, {lang})",
        "requests.copy.cleaned": "(hidden characters removed)",
        "requests.copy.done": "[done]",
        "requests.copy.dismissed": "[dismissed]",

        // ---------------------------------------------------------------- share text
        // After "Globle", which is a name and stays as it is.
        "share.hard": "Hard",
        "share.hardPractice": "Hard Practice",
        "share.capitals": "Capitals",
        "share.unlimited": "Unlimited",
        "share.gaveUpEarly": "Gave up before guessing",
        "share.streak": { one: "{n}-day streak", other: "{n}-day streak" },
    },

    es: {
        // ---------------------------------------------------------------- common
        "common.close": "Cerrar",
        "common.cancel": "Cancelar",
        "common.ok": "Aceptar",
        "common.areYouSure": "¿Seguro?",
        "common.tryAgain": "Reintentar",
        "common.reload": "Recargar",
        "common.loading": "Cargando…",
        "common.play": "Jugar",
        "common.practice": "Práctica",
        "common.daily": "Diario",
        "place.capitalOf": "{capital}, {country}",
        // A country's name inside a sentence (names.js nameInSentence()): the same codes
        // as in English. Never after "a" or "de" in a template ("al", "del").
        "place.inSentence.USA": "Estados Unidos",
        "place.inSentence.GBR": "el Reino Unido",
        "place.inSentence.NLD": "los Países Bajos",
        "place.inSentence.DOM": "la República Dominicana",
        "place.inSentence.CAF": "la República Centroafricana",
        "place.inSentence.COD": "la República Democrática del Congo",
        "place.inSentence.COG": "la República del Congo",
        "place.inSentence.ARE": "los Emiratos Árabes Unidos",
        "place.inSentence.GMB": "Gambia",
        "place.inSentence.PHL": "Filipinas",
        "place.inSentence.BHS": "Bahamas",
        "place.inSentence.MDV": "Maldivas",
        "place.inSentence.MHL": "las Islas Marshall",
        "place.inSentence.SLB": "las Islas Salomón",
        "place.inSentence.COM": "Comoras",
        "place.inSentence.SYC": "Seychelles",

        // ---------------------------------------------------------------- language
        "lang.name": "Español",
        "lang.code": "ES",
        "lang.switchTo": "Cambiar a español",
        "lang.group": "Idioma",
        "lang.changed": "Idioma: español",

        // ---------------------------------------------------------------- header, session
        "nav.back": "Volver a Gehan.Cloud",
        "nav.board": "Clasificación",
        "nav.stats": "Tus estadísticas",
        "nav.help": "Cómo jugar",
        "nav.game": "Juego",
        "nav.home": "Inicio",
        "session.expired": "Tu sesión expiró, así que tus intentos no se pueden guardar.",
        "session.stale": "Esta página está desactualizada, así que tus intentos no se pueden guardar.",
        "session.signIn": "Volver a iniciar sesión",
        "session.announce": "Tu sesión expiró. Vuelve a iniciar sesión para seguir jugando.",

        // ---------------------------------------------------------------- globe
        "stage.label": "Globo",
        "stage.canvas": "Globo con tus intentos. Arrástralo para girarlo; usa la rueda o pellizca para hacer zoom. Con el globo enfocado, las flechas lo giran, más y menos hacen zoom, y 0 lo restablece.",
        "stage.role": "globo",
        "stage.blind": "A ciegas",
        "stage.zoomIn": "Acercar",
        "stage.zoomOut": "Alejar",
        "stage.reset": "Restablecer vista",
        "stage.hintTouch": "Arrastra para girar · pellizca para zoom",
        "stage.hintMouse": "Arrastra para girar · usa la rueda para zoom",
        "stage.loading": "Cargando el mundo…",
        "stage.failed": "No se pudo cargar el globo.",
        "tip.answer": "La respuesta",
        "tip.answerFound": "¡La respuesta!",

        // ---------------------------------------------------------------- home
        "home.title": "Elige un juego",
        "home.failed": "No se pudo cargar el estado de hoy.",
        "home.next": "Nuevos retos en",
        "home.more": "Más formas de jugar",
        "home.otherRegions": "Otras regiones",
        "home.credit": "Inspirado en",
        "home.statusUnknown": "Estado desconocido",
        "daily.number": "Diario #{n}",
        "daily.streakSr": { one: ", racha de {n} día", other: ", racha de {n} días" },

        // ---------------------------------------------------------------- modes
        "variant.classic": "Clásico",
        "variant.hard": "Difícil",
        "practice.country": "Ilimitado",
        "practice.capital": "Capitales",
        "practice.hard": "Práctica difícil",
        "preset.world.label": "Ilimitado",
        "preset.world.sub": "Cualquier país, cuando quieras",
        "preset.europe.label": "Solo Europa",
        "preset.europe.sub": "Países de Europa",
        "preset.mainland.label": "Sin islas pequeñas",
        "preset.mainland.sub": "Continentes e islas grandes",
        "preset.capitals.label": "Capitales",
        "preset.capitals.sub": "Encuentra la capital",
        "preset.hard.label": "Práctica difícil",
        "preset.hard.sub": "Globo a ciegas, sin sugerencias",
        "preset.africa.label": "Solo África",
        "preset.africa.sub": "Países de África",
        "preset.americas.label": "Solo América",
        "preset.americas.sub": "Países de América",
        "preset.asia.label": "Solo Asia",
        "preset.asia.sub": "Países de Asia",
        "preset.oceania.label": "Solo Oceanía",
        "preset.oceania.sub": "Países de Oceanía",
        "region.world": "Mundo",
        "region.africa": "África",
        "region.americas": "América",
        "region.asia": "Asia",
        "region.europe": "Europa",
        "region.oceania": "Oceanía",
        "region.mainland": "Sin islas pequeñas",
        "region.antarctica": "Antártida",
        "mode.daily": "{name} · Diario #{n}",
        "mode.dailyPlain": "{name} · Diario",
        "mode.regional": "{label} · {region}",
        "status.play": "Jugar",
        "status.won": "Resuelto en {n} ✓",
        "status.gaveUp": "Te rendiste",
        "status.playing": { one: "En curso · {n} intento", other: "En curso · {n} intentos" },
        "status.resume": "Continuar",
        "status.resumeCount": { one: "Continuar · {n} intento", other: "Continuar · {n} intentos" },
        "count.guesses": { one: "{n} intento", other: "{n} intentos" },

        // ---------------------------------------------------------------- game panel
        "panel.label": "Tu partida",
        "panel.practice": "Práctica: no cuenta para las rachas.",
        "panel.start": "Empezar una partida",
        "panel.loadingGame": "Cargando tu partida…",
        "panel.loadingList": "Cargando la lista de países…",
        "panel.listFailed": "No se pudo cargar la lista de países.",
        "panel.gameFailed": "No se pudo cargar esta partida.",
        "guess.label": "Adivina un país",
        "guess.labelCapital": "Adivina una capital",
        "guess.placeholder": "Escribe un país…",
        "guess.placeholderCapital": "Escribe una capital…",
        "guess.placeholderHard": "Escribe un país + Enter",
        "guess.go": "Adivinar",
        "guess.help": "Escribe para ver los lugares que coinciden, elige con las flechas y presiona Enter para adivinar.",
        "guess.helpHard": "En Difícil no hay sugerencias: escribe el nombre del país y presiona Enter.",
        "guess.choose": "¿Quisiste decir {name}? Elígelo de la lista o vuelve a presionar Enter.",
        "guess.noCountry": "Ningún país coincide. Intenta escribirlo de otra forma.",
        "guess.noCapital": "Ninguna capital coincide. Intenta escribirla de otra forma.",
        "guess.listLoading": "Todavía se está cargando la lista de países; inténtalo en un momento.",
        "guess.notRecognised": "No lo reconozco: revisa cómo lo escribiste",
        "ac.guessed": "Ya probado",
        "ac.neverAnswer": "Nunca sale",
        "closest.border": "Frontera más cercana",
        "closest.capital": "Capital más cercana",
        "closest.begin": "Escribe cualquier país para empezar",
        "closest.beginCapital": "Escribe cualquier capital para empezar",
        "list.heading": "Intentos",
        "list.units": "Unidades de distancia",
        "list.label": "Tus intentos, del más cercano al más lejano",
        "list.empty": "Aún no hay intentos.",
        "list.found": "¡Acierto!",
        "list.order": "Intento {n}",
        "list.latest": "(último intento)",
        "actions.newGame": "Nueva partida",
        "actions.giveUp": "Rendirse",

        // ---------------------------------------------------------------- end card
        "end.firstTry": "¡A la primera! ¡Increíble!",
        "end.found": "¡Lo encontraste!",
        "end.answerWas": "La respuesta era",
        "end.capitalOf": "Capital de {name}",
        "end.capital": "Capital: {name}",
        "end.foundIn": { one: "Resuelto en {n} intento", other: "Resuelto en {n} intentos" },
        "end.revealedAfter": { one: "Te rendiste tras {n} intento", other: "Te rendiste tras {n} intentos" },
        "end.distribution": "Distribución de intentos",
        "end.next": "Próximo reto en",
        "end.newReady": "Ya hay un reto nuevo",
        "end.share": "Compartir",
        "end.copied": "¡Copiado!",
        "end.playHard": "Jugar el Difícil de hoy",
        "end.home": "Volver al inicio",
        "end.copyLabel": "Resultado para copiar",
        "end.record": "Tu historial en {name}: {summary}",
        "end.emptyHard": "Gana un Difícil diario para empezar tu gráfico.",

        // ---------------------------------------------------------------- errors, by code
        "error.offline": "Parece que no tienes conexión, así que no se envió nada. Vuelve a intentarlo cuando te conectes.",
        "error.timeout": "El servidor tardó demasiado en responder. Volvimos a cargar tu partida por si acaso.",
        "error.server": "El servidor tuvo un problema. Inténtalo de nuevo en un momento.",
        "error.bad-response": "El servidor envió algo inesperado. Intenta recargar la página.",
        "error.generic": "Algo salió mal. Inténtalo de nuevo.",
        "error.unknown-country": "Ese no es un país que conozcamos.",
        "error.unknown-capital": "Esa no es una capital que conozcamos. Elige una de la lista.",
        "error.already-guessed": "Ya intentaste con {name}.",
        "error.no-capital": "{name} no tiene capital para adivinar. Intenta con otro.",
        "error.finished": "Esa partida ya terminó (quizás en otro dispositivo). Cargando la actual.",
        "error.not-found": "No se encontró esa partida. Cargando la actual.",
        "error.new-day": "A medianoche empezó un reto nuevo. No se contó nada.",
        "error.bad-request": "No se pudo enviar. Inténtalo de nuevo.",
        "error.not-an-answer": "{name} es un territorio, así que no cuenta.",
        "error.outside-region": "{name} no está en {region}.",

        // ---------------------------------------------------------------- toasts, announcements
        "toast.otherDevice": "Tienes una partida en curso desde otro dispositivo.",
        "toast.endedElsewhere": "Esa partida terminó en otro dispositivo.",
        "toast.newDay": "Empezó un nuevo día. Este es el {name} #{n}.",
        "toast.lastGivenUp": "Tu partida anterior contó como abandonada.",
        "toast.copied": "¡Copiado! Pégalo en el chat de la familia.",
        "toast.copyFailed": "No se pudo copiar automáticamente. Selecciona el texto y cópialo.",
        "announce.distance": "{name}: a {distance}.",
        "announce.borders": "Hace frontera con el país misterioso.",
        "announce.closest": "Tu intento más cercano hasta ahora: {name}, {distance}.",
        "announce.correct": { one: "¡Correcto! Era {name}. Resuelto en {n} intento.", other: "¡Correcto! Era {name}. Resuelto en {n} intentos." },
        "announce.answerWas": "La respuesta era {name}.",
        "announce.newGame": "Nueva partida: {mode}. Haz tu primer intento.",

        // ---------------------------------------------------------------- confirmations
        "confirm.giveUp.title": "¿Rendirte?",
        "confirm.giveUp.daily": "Se revelará la respuesta y el reto de hoy contará como derrota.",
        "confirm.giveUp.dailyStreak": {
            one: "Se revelará la respuesta, el reto de hoy contará como derrota y perderás tu racha de {n} día.",
            other: "Se revelará la respuesta, el reto de hoy contará como derrota y perderás tu racha de {n} días.",
        },
        "confirm.giveUp.hard": "Se revelará la respuesta y el Difícil de hoy contará como derrota.",
        "confirm.giveUp.hardStreak": {
            one: "Se revelará la respuesta, el Difícil de hoy contará como derrota y perderás tu racha de {n} día.",
            other: "Se revelará la respuesta, el Difícil de hoy contará como derrota y perderás tu racha de {n} días.",
        },
        "confirm.giveUp.practice": "Se revelará la respuesta y esta partida contará como derrota.",
        "confirm.giveUp.ok": "Revelar la respuesta",
        "confirm.keepPlaying": "Seguir jugando",
        "confirm.newGame.title": "¿Empezar una partida nueva?",
        "confirm.newGame.body": {
            one: "Esta partida contará como abandonada tras {n} intento. No se mostrará la respuesta.",
            other: "Esta partida contará como abandonada tras {n} intentos. No se mostrará la respuesta.",
        },
        "confirm.newGame.ok": "Empezar partida nueva",

        // ---------------------------------------------------------------- welcome, help
        "welcome.body": "Encuentra el país misterioso. Cada intento se colorea según lo cerca que está.",
        "legend.far": "Lejos",
        "legend.close": "Cerca",
        "legend.found": "Acierto",
        "help.lede": "Encuentra el país misterioso. Cada intento se colorea según lo cerca que está; los países vecinos están a 0 km.",
        "help.classic": "un país al día, el mismo para todos.",
        "help.hard": "un segundo reto diario: globo en blanco, países menos conocidos, sin sugerencias.",
        "help.practice": "partidas ilimitadas por región o de capitales. Sin rachas.",
        "help.rollcall": "¿cuántos países puedes nombrar?",
        "help.controls": "Arrastra para girar y pellizca para hacer zoom. Lo que compartes muestra colores, nunca nombres.",

        // ---------------------------------------------------------------- stats
        "tiles.played": "Jugadas",
        "tiles.winRate": "% victorias",
        "tiles.streak": "Racha",
        "tiles.maxStreak": "Mejor racha",
        "tiles.avg": "Prom. intentos",
        "dist.label": "Victorias diarias por número de intentos",
        "dist.bucket": { one: "{bucket} intento", other: "{bucket} intentos" },
        "dist.wins": { one: "{n} victoria", other: "{n} victorias" },
        "dist.today": "(hoy)",
        "dist.empty": "Gana un reto diario para empezar tu gráfico.",
        "summary.none": "Aún no hay partidas terminadas.",
        "summary.wins": { one: "{n} victoria de {played}", other: "{n} victorias de {played}" },
        "summary.avg": "prom. {avg}",
        "summary.best": "mejor {n}",

        // ---------------------------------------------------------------- leaderboard
        "board.periods": "Periodo de la clasificación",
        "board.today": "Hoy",
        "board.week": "Semana",
        "board.all": "Histórico",
        "board.unlimited": "Ilimitado",
        "board.gameType": "Tipo de juego",
        "board.countries": "Países",
        "board.capitals": "Capitales",
        "board.failed": "No se pudo cargar la clasificación.",
        "board.noPuzzle": "Hoy no hay reto.",
        "board.todayMeta.classic": "Reto #{n} · {date}",
        "board.todayMeta.hard": "Difícil #{n} · {date}",
        "board.lock": "¿Sigues jugando? Aquí solo se ve cuántos intentos lleva cada uno, nunca la respuesta.",
        "board.nobody": "Nadie ha jugado hoy todavía. ¡Empieza tú!",
        "board.rank": "Puesto {n}",
        "board.won": "Ganó",
        "board.playing": "Jugando",
        "board.gaveUp": "Se rindió",
        "board.you": "tú",
        "board.week.meta.classic": "Retos diarios, últimos 7 días",
        "board.week.meta.hard": "Difícil diario, últimos 7 días",
        "board.week.empty.classic": "No se jugaron retos diarios en los últimos siete días.",
        "board.week.empty.hard": "No se jugó el Difícil diario en los últimos siete días.",
        "board.week.caption": "Resultados diarios de los últimos siete días",
        "board.all.meta.classic": "Retos diarios, histórico",
        "board.all.meta.hard": "Difícil diario, histórico",
        "board.all.empty.classic": "Aún no se jugó ningún reto diario.",
        "board.all.empty.hard": "Aún no se jugó ningún Difícil diario.",
        "board.all.caption": "Resultados diarios, histórico",
        "board.practice.meta": "{name}, todas las partidas terminadas",
        "board.practice.empty": "Aún no hay partidas terminadas de {name}.",
        "board.practice.caption": "Resultados de {name}",
        "board.col.player": "Jugador",
        // Abbreviated like "% vict." and "Prom.": the uppercase headings must fit a phone.
        "board.col.played": "Jug.",
        "board.col.playedTitle": "Partidas jugadas",
        "board.col.wins": "Vict.",
        "board.col.winsTitle": "Partidas ganadas",
        "board.col.winRate": "% vict.",
        "board.col.avg": "Prom.",
        "board.col.avgTitle": "Promedio de intentos por victoria",
        "board.col.streak": "Racha",
        "board.col.streakTitle": "Racha actual (mejor)",
        "board.col.best": "Mejor",
        "board.col.bestTitle": "Menos intentos",

        // ---------------------------------------------------------------- Name them all
        "rollcall.name": "Nómbralos todos",
        "rollcall.sub": "¿Cuántos países puedes nombrar?",
        "rollcall.resume": { one: "Continuar · {n} nombrado", other: "Continuar · {n} nombrados" },
        "rollcall.best": "Récord {named}/{total}",
        "rollcall.inProgress": "(partida en curso)",
        "rollcall.setup.lede": "Escribe los países de uno en uno; cada uno se ilumina en el globo. No hay límite de tiempo, pero el reloj corre.",
        "rollcall.setup.region": "Dónde",
        "rollcall.setup.autofill": "Completar automáticamente",
        "rollcall.setup.best": "Tu récord aquí: {named}/{total} en {time}",
        "rollcall.setup.bestAuto": { one: "({n} completado automáticamente)", other: "({n} completados automáticamente)" },
        "rollcall.setup.noBest": "Todavía no tienes récord aquí. Una partida cuenta en cuanto escribes al menos un país.",
        "rollcall.setup.start": "Empezar",
        "rollcall.setup.failed": "No se pudieron cargar tus opciones.",
        "rollcall.setup.unavailable": "Esa opción ya no está disponible. Elige otra.",
        "rollcall.autofill.off": "Ninguno",
        "rollcall.autofill.easy": "Los fáciles",
        "rollcall.autofill.known": "Fáciles y conocidos",
        "rollcall.autofill.mine": "Los que siempre nombro",
        "rollcall.autofill.offNote": "los escribes todos tú",
        "rollcall.autofill.mineNote": "los que nombraste en cada una de tus últimas {n} partidas",
        "rollcall.autofill.locked": "después de 2 partidas",
        "rollcall.autofill.lockedMore": { one: "después de {n} partida más", other: "después de {n} partidas más" },
        "rollcall.autofill.full": "son todos: no quedaría nada por nombrar",
        "rollcall.play.count": "{n} de {total} nombrados",
        "rollcall.play.time": "Tiempo",
        "rollcall.play.progress": "Por continente",
        "rollcall.play.regionCount": "{region}: {n} de {total}",
        "rollcall.input.label": "Nombra un país",
        "rollcall.input.placeholder": "Escribe un país…",
        "rollcall.input.go": "Enter",
        "rollcall.named": "{name}: nombrado. {n} de {total}.",
        "rollcall.already": "Ya nombraste {name}.",
        "rollcall.notSaved": "{name} no se guardó.",
        "rollcall.notSavedMore": { one: "{name} y {n} país más no se guardaron.", other: "{name} y {n} países más no se guardaron." },
        "rollcall.input.helpLenient": "Mientras escribes no hay lista: un país cuenta en cuanto terminas su nombre. Presiona Enter si se queda esperando o para revisar cómo lo escribiste.",
        "rollcall.notCountry": "{name} no cuenta: no es uno de los {n} países.",
        "rollcall.part": "{part} es parte de {name}, así que no cuenta: nombra el país.",
        "rollcall.former": "{old} es un nombre antiguo de {name}, así que no cuenta: usa el nombre actual.",
        "rollcall.part.GBR": "{part} es parte del Reino Unido, así que no cuenta: nombra el país.",
        "rollcall.part.VCT": "Las Granadinas son parte de San Vicente y las Granadinas, así que no cuentan: nombra el país.",
        "rollcall.term.westbank": "Cisjordania",
        "rollcall.term.cisjordania": "Cisjordania",
        "rollcall.term.franjadegaza": "la Franja de Gaza",
        "rollcall.former.COD": "{old} es un nombre antiguo de la República Democrática del Congo, así que no cuenta: usa el nombre actual.",
        "rollcall.from": "(escribiste “{text}”)",
        "rollcall.namedFrom": "{name}: nombrado, escribiste “{text}”. {n} de {total}.",
        "rollcall.didYouMean": "¿Quisiste decir {options}?",
        "rollcall.or": "o",
        "rollcall.orBeforeO": "u",
        "rollcall.suggest.enter": "Presiona Enter para contarlo.",
        "rollcall.fuzzy.already": "¿Quisiste decir {name}? Ya lo nombraste.",
        "rollcall.fuzzy.notCountry": "¿Quisiste decir {name}? No cuenta: no es uno de los {n} países.",
        "rollcall.fuzzy.outside": "¿Quisiste decir {name}? No está en {region}.",
        "rollcall.fuzzy.part": "¿Quisiste decir {part}? Es parte de {name}, así que no cuenta: nombra el país.",
        "rollcall.fuzzy.former": "¿Quisiste decir {old}? Es un nombre antiguo de {name}, así que no cuenta: usa el nombre actual.",
        "rollcall.fuzzy.part.GBR": "¿Quisiste decir {part}? Es parte del Reino Unido, así que no cuenta: nombra el país.",
        "rollcall.fuzzy.former.COD": "¿Quisiste decir {old}? Es un nombre antiguo de la República Democrática del Congo, así que no cuenta: usa el nombre actual.",
        "rollcall.in.americas": "América",
        "rollcall.list.named": "Nombrados",
        "rollcall.list.missed": "Te faltaron",
        "rollcall.list.empty": "Todavía no nombraste ninguno. ¡Empieza a escribir!",
        "rollcall.list.auto": "completado automáticamente",
        "rollcall.list.saving": "guardando",
        "rollcall.list.other": "Otros",
        "rollcall.done": "Terminé",
        "rollcall.startOver": "Empezar de nuevo",
        "rollcall.confirm.doneTitle": "¿Terminar esta partida?",
        "rollcall.confirm.doneBody": "Nombraste {n} de {total} en {time}. Se mostrarán los que te faltaron.",
        "rollcall.confirm.doneEmpty": "Todavía no escribiste ninguno, así que esta partida no contará.",
        "rollcall.confirm.doneOk": "Terminé",
        "rollcall.confirm.keepGoing": "Seguir",
        "rollcall.confirm.overTitle": "¿Empezar de nuevo?",
        "rollcall.confirm.overBody": "Esta partida termina ahora y cuenta: {n} de {total} en {time}.",
        "rollcall.confirm.overEmpty": "Todavía no escribiste ninguno, así que esta partida simplemente se descarta.",
        "rollcall.confirm.overOk": "Empezar de nuevo",
        "rollcall.end.kicker": "Fin de la partida",
        "rollcall.end.all": "¡Todos y cada uno!",
        "rollcall.end.title": "Nombraste {n} de {total}",
        "rollcall.end.time": "Tiempo {time}",
        "rollcall.end.auto": { one: "{n} completado automáticamente", other: "{n} completados automáticamente" },
        "rollcall.end.noCount": "No escribiste ninguno, así que esta partida no cuenta.",
        "rollcall.end.newBest": "¡Nuevo récord personal!",
        "rollcall.end.again": "Jugar otra vez",
        "rollcall.tip.named": "Nombrado",
        "rollcall.tip.auto": "Completado automáticamente",
        "rollcall.tip.missed": "Te faltó",
        "rollcall.toast.otherDevice": "Ya tienes una partida en curso aquí, empezada en otro dispositivo.",
        "rollcall.toast.ended": "Esa partida terminó en otro dispositivo.",
        "rollcall.toast.counted": "Tu partida anterior quedó registrada: {n} de {total}.",
        "rollcall.announce.start": { one: "{mode}: {n} país por nombrar. Empieza a escribir.", other: "{mode}: {n} países por nombrar. Empieza a escribir." },
        "rollcall.announce.done": "Fin de la partida: nombraste {n} de {total} en {time}.",
        "rollcall.board.meta": "Mundo entero: la mejor partida terminada de cada uno",
        "rollcall.board.empty": "Aún nadie terminó una partida del mundo entero.",
        "rollcall.board.caption": "Resultados de Nómbralos todos",
        "rollcall.board.col.best": "Récord",
        "rollcall.board.col.bestTitle": "Más países nombrados en una partida",
        "rollcall.board.col.time": "Tiempo",
        "rollcall.board.col.timeTitle": "Lo que duró esa partida",
        "rollcall.board.col.auto": "Auto.",
        "rollcall.board.col.autoTitle": "Países completados automáticamente en esa partida",
        "rollcall.stats": "récord {named}/{total} · {time}",
        "rollcall.stats.none": "Todavía no tienes récord en el mundo entero.",

        // round 2: name-change requests and study page
        "study.badge": "Repaso",
        "study.open": "Repasa los que te faltaron",
        "study.last": "Repasa tu última partida",
        "study.hint": "Toca un país para verlo en el globo, con los países que lo rodean.",
        "study.failed": "No se pudo cargar tu última partida.",
        "study.none": "Todavía no terminaste ninguna partida aquí. Juega una y vuelve para repasar los que te falten.",
        "study.allNamed": "En tu última partida aquí nombraste los {total}. ¡No hay nada que repasar!",
        "study.summary.world": { one: "Tu última partida del mundo entero ({when}): nombraste {named} de {total} y te faltó {missed}.", other: "Tu última partida del mundo entero ({when}): nombraste {named} de {total} y te faltaron {missed}." },
        "study.summary.region": { one: "Tu última partida en {region} ({when}): nombraste {named} de {total} y te faltó {missed}.", other: "Tu última partida en {region} ({when}): nombraste {named} de {total} y te faltaron {missed}." },
        "study.showAll": { one: "Mostrar {n}", other: "Mostrar los {n}" },
        "study.show": "Mostrar {name} en el globo",
        "study.showSr": "(mostrar en el globo)",
        "study.tip.selected": "Repasando",
        "study.tip.borders": "Limita con {name}",
        "study.compass.northWest": "En el noroeste {area}",
        "study.compass.north": "En el norte {area}",
        "study.compass.northEast": "En el noreste {area}",
        "study.compass.west": "En el oeste {area}",
        "study.compass.centre": "En el centro {area}",
        "study.compass.east": "En el este {area}",
        "study.compass.southWest": "En el suroeste {area}",
        "study.compass.south": "En el sur {area}",
        "study.compass.southEast": "En el sureste {area}",
        "study.area.africa": "de África",
        "study.area.europe": "de Europa",
        "study.area.asia": "de Asia",
        "study.area.oceania": "de Oceanía",
        "study.area.northAmerica": "de América del Norte",
        "study.area.centralAmerica": "de Centroamérica",
        "study.area.caribbean": "del Caribe",
        "study.area.southAmerica": "de Sudamérica",
        "study.area.americas": "de América",
        "study.where.borders": "{where}. Limita con {list}.",
        "study.where.bordersOnly": "Limita con {list}.",
        "study.where.nearest": "{where}. El país más cercano: {name}, a {km}.",
        "study.where.nearestOnly": "El país más cercano: {name}, a {km}.",
        "study.where.only": "{where}.",
        "study.yourNearest.border": "Limita con {name}, que está en tu lista.",
        "study.yourNearest.km": "Lo más cercano de tu lista: {name}, a {km}.",
        "study.often.most": { one: "Suelen nombrarlo: en {named} de {n} partida aquí.", other: "Suelen nombrarlo: en {named} de {n} partidas aquí." },
        "study.often.half": { one: "Lo nombraron en la mitad de las partidas aquí: {named} de {n}.", other: "Lo nombraron en la mitad de las partidas aquí: {named} de {n}." },
        "study.often.few": { one: "Se suele olvidar: lo nombraron solo en {named} de {n} partida aquí.", other: "Se suele olvidar: lo nombraron solo en {named} de {n} partidas aquí." },
        "study.often.nobody": { one: "Todavía nadie lo nombró en {n} partida.", other: "Todavía nadie lo nombró en ninguna de las {n} partidas." },
        "study.often.wellKnown": "Todavía no hay suficientes partidas para saberlo, pero es de los conocidos.",
        "study.often.familiar": "Todavía no hay suficientes partidas para saberlo; es bastante conocido.",
        "study.often.obscure": "Todavía no hay suficientes partidas para saberlo, pero es de los menos conocidos.",
        "study.yours.neverMissedOne": "Lo nombraste en tu otra partida aquí.",
        "study.yours.neverMissed": { one: "Lo nombraste en tus otras {n} partidas aquí.", other: "Lo nombraste en tus otras {n} partidas aquí." },
        "study.yours.alwaysOne": "Te faltó en tu única partida aquí.",
        "study.yours.always": { one: "Te faltó en tus {n} partidas aquí.", other: "Te faltó en tus {n} partidas aquí." },
        "study.yours.some": { one: "Te faltó en {missed} de tu {n} partida aquí.", other: "Te faltó en {missed} de tus {n} partidas aquí." },
        "request.button": "Pedir un cambio de nombre para {name}",
        "request.titlePlain": "Sugiere un nombre",
        "request.title": "Sugiere un nombre para {name}",
        "request.lede": "Escribe cómo crees que debería aceptarse. Le llega a quien mantiene el juego, para la próxima actualización.",
        "request.label": "Cómo debería escribirse",
        "request.placeholder": "p. ej., Gran Bretaña",
        "request.recent": "No reconocidos en esta partida:",
        "request.use": "Usar “{text}”",
        "request.send": "Enviar",
        "request.sent": "¡Gracias! Se envió para revisión.",
        "request.error.empty": "Escribe cómo crees que debería aceptarse.",
        "request.error.tooLong": "Es demasiado largo: 80 caracteres como máximo.",
        "request.error.tooMany": "Ya tienes 30 sugerencias esperando revisión. Vuelve a intentarlo cuando se hayan revisado algunas.",
        "request.error.bad": "Así no se puede enviar. Usa letras o números, 80 caracteres como máximo.",
        "request.error.signedOut": "Tu sesión expiró, así que no se envió. Primero vuelve a iniciar sesión.",
        "request.error.stale": "Esta página está desactualizada, así que no se envió. Recarga la página y vuelve a intentarlo.",
        "request.error.offline": "Parece que no tienes conexión, así que no se envió nada. Vuelve a intentarlo cuando tengas conexión.",
        "request.error.timeout": "El servidor tardó demasiado en responder. Vuelve a intentarlo; no se enviará dos veces.",
        "request.error.generic": "Algo salió mal, así que no se envió nada. Vuelve a intentarlo.",
        "request.source.named": "escrito en la lista de nombrados",
        "request.source.missed": "escrito en la lista de los que faltaron",
        "request.source.study": "escrito en la página de repaso",
        "request.source.other": "escrito en otra parte",
        "request.lang.en": "página en inglés",
        "request.lang.es": "página en español",
        "requests.badge": "Solicitudes de nombres",
        "requests.title": "Solicitudes de cambio de nombre",
        "requests.lede": "Nombres que los jugadores creen que deberían aceptarse. Cópialos para el próximo cambio en los datos; una solicitud que los datos ya aceptan se cierra sola.",
        "requests.showResolved": "Mostrar resueltas",
        "requests.copyAll": "Copiar todo",
        "requests.copyLabel": "Solicitudes para copiar",
        "requests.copied": { one: "Se copió {n} solicitud.", other: "Se copiaron {n} solicitudes." },
        "requests.homeCount": { one: "{n} solicitud de cambio de nombre por revisar", other: "{n} solicitudes de cambio de nombre por revisar" },
        "requests.failed": "No se pudieron cargar las solicitudes.",
        "requests.empty": "No hay solicitudes pendientes.",
        "requests.emptyAll": "Todavía no hay solicitudes.",
        "requests.by": "de {name}",
        "requests.suggested": "Nombre sugerido:",
        "requests.cleaned": "(se quitaron caracteres invisibles)",
        "requests.state.done": "Resuelta por {name}",
        "requests.state.data": "Se cerró sola: los datos ya lo aceptan",
        "requests.state.dismissed": "Descartada por {name}",
        "requests.done": "Hecho",
        "requests.dismiss": "Descartar",
        "requests.reopen": "Reabrir",
        "requests.doneFor": "Hecho: {name}, “{suggestion}”",
        "requests.dismissFor": "Descartar: {name}, “{suggestion}”",
        "requests.reopenFor": "Reabrir: {name}, “{suggestion}”",
        "requests.toast.done": "Solicitud para {name}: hecha.",
        "requests.toast.dismissed": "Solicitud para {name}: descartada.",
        "requests.toast.reopened": "Solicitud para {name}: reabierta.",
        "requests.gone": "Esa solicitud ya no existe. Se volvió a cargar la lista.",
        "requests.copy.heading": { one: "Solicitudes de cambio de nombre de Globle ({n}):", other: "Solicitudes de cambio de nombre de Globle ({n}):" },
        "requests.copy.line": "{code} {name} ← {suggestion} (de {user}, {date}, {source}, {lang})",
        "requests.copy.cleaned": "(se quitaron caracteres invisibles)",
        "requests.copy.done": "[hecha]",
        "requests.copy.dismissed": "[descartada]",

        // ---------------------------------------------------------------- share text
        "share.hard": "Difícil",
        "share.hardPractice": "Práctica difícil",
        "share.capitals": "Capitales",
        "share.unlimited": "Ilimitado",
        "share.gaveUpEarly": "Me rendí antes de intentarlo",
        "share.streak": { one: "Racha de {n} día", other: "Racha de {n} días" },
    },
};

// ------------------------------------------------------------------ the language

let current = "en";
const listeners = new Set();

/** The browser's preferred languages, most wanted first; [] where there is no browser. */
export function browserLanguages() {
    try {
        if (typeof navigator !== "undefined" && navigator) {
            if (Array.isArray(navigator.languages) && navigator.languages.length) return [...navigator.languages];
            if (navigator.language) return [navigator.language];
        }
    } catch {
        // No navigator worth asking: no preference.
    }
    return [];
}

/** Which language a tag belongs to: "es-MX" -> "es", "en" -> "en", "fr-FR" -> null. */
function langOf(tag) {
    const m = /^(en|es)(?:[-_]|$)/i.exec(String(tag || ""));
    return m ? m[1].toLowerCase() : null;
}

/**
 * The default for a reader who has not chosen: Spanish if the first English or
 * Spanish entry in their browser's list is Spanish, else English. So
 * ["es-MX", "en-US"] and ["fr-FR", "es-ES", "en"] get Spanish, ["en-US", "es"]
 * and ["de-DE"] English.
 */
export function detectLang(languages = browserLanguages()) {
    for (const tag of languages || []) {
        const lang = langOf(tag);
        if (lang) return lang;
    }
    return "en";
}

export function getLang() {
    return current;
}

/**
 * Switches the language: <html lang> follows, and every onLangChange listener
 * runs, so the page can redraw. Anything that is not a supported language is
 * ignored. Returns the language now in force.
 */
export function setLang(lang) {
    if (!LANGS.includes(lang)) return current;
    const changed = lang !== current;
    current = lang;
    if (typeof document !== "undefined" && document.documentElement) document.documentElement.lang = lang;
    if (changed) {
        for (const fn of [...listeners]) fn(lang);
    }
    return current;
}

/** Calls fn(lang) after every change of language; returns a function that stops it. */
export function onLangChange(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
}

function validTag(tag) {
    try {
        return Intl.getCanonicalLocales(tag)[0] || null;
    } catch {
        return null;
    }
}

/**
 * The locale numbers and dates are formatted in. Spanish: the browser's own
 * Spanish (es-MX, es-ES...) when it lists one, else es-US. English: the browser's
 * own locale as it always was, unless that is Spanish, in which case its English
 * entry, else en-US (an English page should not read "vie, 9 oct").
 */
export function locale(lang = current, languages = browserLanguages()) {
    const list = (languages || []).map(validTag).filter(Boolean);
    const firstOf = (want) => list.find((tag) => langOf(tag) === want) || null;
    if (lang === "es") return firstOf("es") || "es-US";
    if (list.length && langOf(list[0]) !== "es") return list[0];
    return firstOf("en") || "en-US";
}

// ------------------------------------------------------------------ lookups

const warned = new Set();

function lookup(key, lang) {
    const table = STRINGS[lang] || STRINGS.en;
    if (Object.prototype.hasOwnProperty.call(table, key)) return table[key];
    if (Object.prototype.hasOwnProperty.call(STRINGS.en, key)) return STRINGS.en[key];
    if (!warned.has(key) && typeof console !== "undefined") {
        warned.add(key);
        console.warn(`i18n: no string for "${key}"`);
    }
    return key;
}

function fill(template, params) {
    return String(template).replace(/\{(\w+)\}/g, (whole, name) =>
        params && params[name] != null ? String(params[name]) : whole);
}

/** Whether a key exists (in English, which every key must). */
export function has(key) {
    return Object.prototype.hasOwnProperty.call(STRINGS.en, key);
}

/**
 * One string: t("end.capital", { name: "París" }). A plural key read through t()
 * gives its "other" form. lang defaults to the page's language; pass one to read
 * the other dictionary (the language switch speaks the language it offers).
 */
export function t(key, params, lang = current) {
    const value = lookup(key, lang);
    return fill(typeof value === "object" ? value.other : value, params);
}

const pluralRules = new Map();
const numberFormats = new Map();

function pluralRule(tag) {
    if (!pluralRules.has(tag)) pluralRules.set(tag, new Intl.PluralRules(tag));
    return pluralRules.get(tag);
}

/** A whole number in the page's locale: 1234 -> "1,234" (en-US) or "1.234" (es-ES). */
export function formatNumber(n, options = {}, lang = current) {
    const tag = locale(lang);
    const id = `${tag}|${JSON.stringify(options)}`;
    if (!numberFormats.has(id)) numberFormats.set(id, new Intl.NumberFormat(tag, options));
    return numberFormats.get(id).format(n);
}

/**
 * A string that depends on a count: tn("count.guesses", 3) -> "3 guesses" /
 * "3 intentos". The form follows Intl.PluralRules for the language itself, not
 * the locale numbers are written in: an English page on a French browser still
 * says "0 guesses" (a category the key lacks, such as Spanish "many" for a
 * million, falls back to "other"). {n} is the count formatted in the page's
 * locale unless params.n says otherwise.
 */
export function tn(key, count, params = {}, lang = current) {
    const value = lookup(key, lang);
    const n = Number(count) || 0;
    let template = value;
    if (value && typeof value === "object") {
        const category = pluralRule(LANGS.includes(lang) ? lang : "en").select(n);
        template = value[category] != null ? value[category] : value.other;
    }
    return fill(template, { n: formatNumber(n, { maximumFractionDigits: 0 }, lang), ...params });
}

const collators = new Map();

/** Compares two names the way the page's locale sorts them: list.sort(compareText). */
export function compareText(a, b) {
    const tag = locale();
    if (!collators.has(tag)) collators.set(tag, new Intl.Collator(tag, { sensitivity: "base", numeric: true }));
    return collators.get(tag).compare(String(a == null ? "" : a), String(b == null ? "" : b));
}

// ------------------------------------------------------------------ the DOM

/**
 * Puts the dictionary's words into the page under root (the whole document by
 * default, or one element and what is inside it):
 *
 *   data-i18n="key"                 the element's text (it must hold only text)
 *   data-i18n-title="key"           its title attribute, and likewise
 *   data-i18n-aria-label="key"      for every attribute in I18N_ATTRIBUTES
 *
 * Safe to run again at any time, which is how the page changes language in place.
 * An element whose text a script sets can change its key (el.dataset.i18n = ...)
 * so a later run keeps it right.
 */
export function applyTranslations(root = typeof document !== "undefined" ? document : null) {
    if (!root || typeof root.querySelectorAll !== "function") return;
    const each = (selector, fn) => {
        if (typeof root.matches === "function" && root.matches(selector)) fn(root);
        for (const node of root.querySelectorAll(selector)) fn(node);
    };
    each("[data-i18n]", (node) => { node.textContent = t(node.getAttribute("data-i18n")); });
    for (const attribute of I18N_ATTRIBUTES) {
        const data = `data-i18n-${attribute}`;
        each(`[${data}]`, (node) => { node.setAttribute(attribute, t(node.getAttribute(data))); });
    }
}

/** Gives an element a key and its text in one go, so applyTranslations keeps it. */
export function translateText(node, key) {
    if (!node) return;
    node.setAttribute("data-i18n", key);
    node.textContent = t(key);
}
