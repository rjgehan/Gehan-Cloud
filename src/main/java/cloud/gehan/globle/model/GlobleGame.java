package cloud.gehan.globle.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One game by one player: a daily puzzle, an unlimited (country or capital) round, or
 * a roll call run ("Name them all"; see {@link #rollCall}).
 *
 * <p><b>The owner is a plain id, not a foreign key.</b> A foreign key into
 * {@code users} would make deleting a user fail while they have games, which would let
 * Globle change how the user console behaves. Instead games of a deleted user are
 * simply left behind and ignored (the leaderboard only lists users that still exist),
 * and the table can be dropped whenever the game is. The username is stored beside
 * the id; see {@link Player} for why the pair, not the id alone, identifies the owner.
 *
 * <p><b>Daily rows exist only once a player has done something.</b> They are created
 * by the first guess or a give-up, never by looking, so "played" means played. A
 * unique {@code daily_key} stops a second row for the same player and day; see that
 * field for why it is one column rather than a key over three.
 *
 * <p><b>Guesses are one comma-separated column</b> of codes in the order they were
 * made. A game is always read and written whole, there is nothing to query inside it,
 * and a child table would be a join on every request for no gain. Distances are not
 * stored: they are recomputed from the catalogue, so a correction to the data fixes
 * old games too.
 *
 * <p>Daily rows do not store their answer; it lives once per date in
 * {@link GlobleDay} (classic) or {@link GlobleHardDay} (Hard). Unlimited rows carry
 * their own in {@code answerCode}.
 *
 * <p><b>Rows from before Hard.</b> The table predates the {@code variant} column, and
 * {@code ddl-auto=update} adds a column without filling it, so every row written
 * before Hard existed has a null variant. They were all classic games, and
 * {@link #getVariant()} reads null as exactly that. Nothing rewrites those rows.
 *
 * <p><b>Roll call rows</b> reuse the table rather than adding one: mode ROLLCALL, kind
 * COUNTRY, the run's region, variant CLASSIC, no answer, and the named countries in
 * {@code guesses}, in the order named, the ones filled in automatically first. Two
 * columns serve them alone, {@code autofill} and {@code autofill_count}. Both are
 * nullable wrapper types: they were added to a table that already had rows, and a
 * primitive would make Hibernate add a NOT NULL column, which SQLite refuses to add to
 * a table with rows. Every other row, and every row from before, has nulls there, and
 * nothing rewrites them.
 */
@Entity
@Table(name = "globle_game",
        indexes = {
                @Index(name = "ix_globle_game_player", columnList = "user_id, mode, kind, status"),
                @Index(name = "ix_globle_game_date", columnList = "puzzle_date")
        })
public class GlobleGame {

    private static final String SEPARATOR = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Convert(converter = GlobleColumns.ModeName.class)
    @Column(name = "mode", length = 16, nullable = false)
    private Mode mode;

    @Convert(converter = GlobleColumns.KindName.class)
    @Column(name = "kind", length = 16, nullable = false)
    private Kind kind;

    @Convert(converter = GlobleColumns.RegionName.class)
    @Column(name = "region", length = 16, nullable = false)
    private Region region;

    /**
     * CLASSIC or HARD; null on rows from before Hard, which are classic (see the class
     * comment). Nullable because it was added to a table that already had rows.
     */
    @Convert(converter = GlobleColumns.VariantName.class)
    @Column(name = "variant", length = 16)
    private Variant variant;

    /** Daily only. */
    @Convert(converter = GlobleColumns.DateText.class)
    @Column(name = "puzzle_date", length = 10)
    private LocalDate puzzleDate;

    /**
     * "userId/username/date" on a classic daily row, "userId/username/date/HARD" on a
     * Hard one, null on any other: the rule "one daily per player per day per variant"
     * as a single unique column. The classic form is the one rows have always had, so
     * a classic game written now still collides with one written before Hard existed.
     *
     * <p>It is one column because that is the only shape of unique key Hibernate
     * creates on SQLite. Its SQLite dialect adds multi-column keys with ALTER TABLE,
     * which SQLite does not support, so a {@code @UniqueConstraint} or a unique
     * {@code @Index} over (user_id, username, puzzle_date) is silently left out of the
     * schema; a single column's UNIQUE goes into CREATE TABLE itself. NULLs never
     * collide, so unlimited rows are unaffected.
     */
    @Column(name = "daily_key", length = 128, unique = true)
    private String dailyKey;

    /** Unlimited only; a daily's answer is in globle_day. */
    @Column(name = "answer_code", length = 8)
    private String answerCode;

    @Convert(converter = GlobleColumns.StatusName.class)
    @Column(name = "status", length = 16, nullable = false)
    private Status status;

    /** Codes in guess order, comma-separated. Room for well over the guess cap. */
    @Column(name = "guesses", length = 4000, nullable = false)
    private String guesses = "";

    @Column(name = "guess_count", nullable = false)
    private int guessCount;

    @Convert(converter = GlobleColumns.InstantMillis.class)
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Convert(converter = GlobleColumns.InstantMillis.class)
    @Column(name = "finished_at")
    private Instant finishedAt;

    /**
     * Roll call only: what was filled in when the run started (OFF, EASY, KNOWN or
     * MINE). Null on every other row; see the class comment for why it is nullable.
     */
    @Convert(converter = GlobleColumns.AutofillName.class)
    @Column(name = "autofill", length = 16)
    private Autofill autofill;

    /**
     * Roll call only: the first this-many codes of {@code guesses} were filled in
     * automatically; the rest were typed. Null on every other row. An Integer, not an
     * int, so the column it adds is nullable (see the class comment).
     */
    @Column(name = "autofill_count")
    private Integer autofillCount;

    protected GlobleGame() {
    }

    private GlobleGame(Player player, Mode mode, Kind kind, Region region, Variant variant, Instant now) {
        this.userId = player.id();
        this.username = player.username();
        this.mode = mode;
        this.kind = kind;
        this.region = region;
        this.variant = variant;
        this.status = Status.PLAYING;
        this.startedAt = now;
    }

    /** Today's classic puzzle for this player. */
    public static GlobleGame daily(Player player, LocalDate date, Instant now) {
        return daily(player, date, Variant.CLASSIC, now);
    }

    /** Today's puzzle of one variant for this player. Always a whole-world country game. */
    public static GlobleGame daily(Player player, LocalDate date, Variant variant, Instant now) {
        GlobleGame game = new GlobleGame(player, Mode.DAILY, Kind.COUNTRY, Region.WORLD, variant, now);
        game.puzzleDate = date;
        game.dailyKey = dailyKey(player, date, variant);
        return game;
    }

    /** The classic form is unchanged from before Hard; see the column of the same name. */
    static String dailyKey(Player player, LocalDate date, Variant variant) {
        String key = player.id() + "/" + player.username() + "/" + date;
        return variant == Variant.CLASSIC ? key : key + "/" + variant.name();
    }

    public static GlobleGame unlimited(Player player, Kind kind, Region region, Variant variant, String answerCode,
                                       Instant now) {
        GlobleGame game = new GlobleGame(player, Mode.UNLIMITED, kind, region, variant, now);
        game.answerCode = answerCode;
        return game;
    }

    /**
     * A new roll call run in a region, with {@code autoCodes} already named on the
     * player's behalf (none for {@link Autofill#OFF}). They go first in the list and are
     * counted by {@code autofill_count}; everything named after them was typed.
     */
    public static GlobleGame rollCall(Player player, Region region, Autofill autofill, List<String> autoCodes,
                                      Instant now) {
        GlobleGame game = new GlobleGame(player, Mode.ROLLCALL, Kind.COUNTRY, region, Variant.CLASSIC, now);
        game.autofill = autofill;
        game.guesses = String.join(SEPARATOR, autoCodes);
        game.guessCount = autoCodes.size();
        game.autofillCount = autoCodes.size();
        return game;
    }

    public List<String> guessCodes() {
        if (guesses == null || guesses.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(guesses.split(SEPARATOR)).filter(code -> !code.isBlank()).toList();
    }

    public boolean hasGuessed(String code) {
        return guessCodes().contains(code);
    }

    public void addGuess(String code) {
        List<String> codes = new ArrayList<>(guessCodes());
        codes.add(code);
        this.guesses = String.join(SEPARATOR, codes);
        this.guessCount = codes.size();
    }

    /**
     * Roll call: how many of the leading codes were filled in automatically. 0 on any
     * other row. Never more than the codes there are.
     */
    public int autoCount() {
        return autofillCount == null ? 0 : Math.max(0, Math.min(autofillCount, guessCount));
    }

    /** Roll call: the codes the player typed, in the order typed (the auto ones excluded). */
    public List<String> typedCodes() {
        List<String> codes = guessCodes();
        return codes.subList(Math.min(autoCount(), codes.size()), codes.size());
    }

    /** Roll call: whether the player has typed at least one name, beyond what was filled in. */
    public boolean hasTyped() {
        return guessCount > autoCount();
    }

    /**
     * Roll call: whether this run counts toward the player's record - the best run and
     * the finished-run count on the setup card, the runs "mine" reads, and the board. It
     * does once it is FINISHED with at least one name typed. A run with nothing typed,
     * ended empty or holding only what autofill gave it, stays stored as FINISHED but
     * counts nowhere: none of it was the player's doing. It is the line the abandon rule
     * draws too (a run with nothing typed is dropped), and the board applies the same
     * rule to its summaries (RollCallSummary#counts).
     */
    public boolean counts() {
        return mode == Mode.ROLLCALL && status == Status.FINISHED && hasTyped();
    }

    /**
     * Ends the game. A daily or practice game ends WON or GAVE_UP, a roll call run
     * FINISHED, and never the other way round: the boards for each count only their
     * own statuses, so a mismatch would be a game counted nowhere or in the wrong place.
     */
    public void finish(Status outcome, Instant now) {
        if (outcome == Status.PLAYING || (outcome == Status.FINISHED) != (mode == Mode.ROLLCALL)) {
            throw new IllegalArgumentException(
                    "A daily or practice game finishes as WON or GAVE_UP, a roll call run as FINISHED");
        }
        this.status = outcome;
        this.finishedAt = now;
    }

    public boolean isPlaying() {
        return status == Status.PLAYING;
    }

    public boolean belongsTo(Player player) {
        return userId == player.id() && player.username().equals(username);
    }

    /**
     * Whether this is an unlimited game in the slot (kind, region, variant). Each slot
     * has at most one game in progress, and each resumes on its own.
     */
    public boolean inSlot(Kind kind, Region region, Variant variant) {
        return this.kind == kind && this.region == region && getVariant() == variant;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Mode getMode() {
        return mode;
    }

    public Kind getKind() {
        return kind;
    }

    public Region getRegion() {
        return region;
    }

    /** Never null: a row from before Hard is classic. */
    public Variant getVariant() {
        return Variant.orClassic(variant);
    }

    public LocalDate getPuzzleDate() {
        return puzzleDate;
    }

    public String getAnswerCode() {
        return answerCode;
    }

    public Status getStatus() {
        return status;
    }

    public int getGuessCount() {
        return guessCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    /** Never null: a row that is not a roll call run, or predates it, reads as OFF. */
    public Autofill getAutofill() {
        return Autofill.orOff(autofill);
    }

    /** As stored: null on every row that is not a roll call run. See {@link #autoCount()}. */
    public Integer getAutofillCount() {
        return autofillCount;
    }
}
