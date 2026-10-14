package cloud.gehan.globle.service;

import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.catalog.PlaceNames;
import cloud.gehan.globle.model.GlobleNameRequest;
import cloud.gehan.globle.model.Lang;
import cloud.gehan.globle.model.NameRequestSource;
import cloud.gehan.globle.model.NameRequestStatus;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.repository.GlobleNameRequestRepository;
import cloud.gehan.globle.view.NameRequestView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * "Request a name change": a player suggests the name they think should count for a
 * place, and the admin works through the suggestions by hand, changing the data
 * (tools/globle) and marking each one DONE, or DISMISSED.
 *
 * <p><b>Sending.</b> Any signed-in player may send one for any place in the catalogue,
 * territories included. The suggestion is cleaned before anything else (Unicode NFC,
 * every run of spaces, line breaks or control characters one space, the ends trimmed)
 * and must then be 1 to {@value GlobleNameRequest#MAX_SUGGESTION} characters with at
 * least one letter or digit. Sending again what the player already has open, the same
 * place and the same suggestion as the page's matcher reads it ({@link #sameKey}),
 * returns the open request rather than storing a second one, so a double tap or a
 * retry after a timeout is harmless. A player may have at most
 * {@value #MAX_OPEN_PER_PLAYER} open at once; the next is refused with
 * {@code too-many} until the admin has closed some.
 *
 * <p><b>Closing by itself.</b> When the data comes to name a place the way an open
 * request suggests (any name or alias, in either language, compared as the page
 * compares what is typed: {@link PlaceNames}), the request is closed as DONE by
 * {@value GlobleNameRequest#BY_DATA}. That happens whenever the admin's list or count
 * is read, so after a data change adds the name, the request closes with nothing done
 * by hand. Only an exact name closes one, never a near miss, and only one that names
 * that place alone, as the page's findExact() requires. A name in
 * {@link #NOT_COUNTED_IN_NAME_THEM_ALL} never closes one either: the data has it, but
 * Name them all still refuses it, so the request is a question for the admin. A request
 * for a code no longer in the data stays open for the admin to see.
 *
 * <p><b>Writes</b> run like every other Globle write: one transaction under the
 * app-wide lock ({@link GlobleWrites}). That includes the admin's reads, since they
 * may close requests.
 */
@Service
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NameRequestService {

    /** How many open requests one player may have at once. */
    static final int MAX_OPEN_PER_PLAYER = 30;

    /**
     * Names countries.json gives a country that Name them all does not count for it, by
     * code: a part of the country ("England" for the United Kingdom), "America" alone
     * (in Spanish the continent), and former names ("Zaire", "Persia"). The game's own
     * list is the roll call client's (rollcall.js); this is the server's copy, used only
     * to keep a request for one of these names open, since the data already having it
     * answers nothing. Compared as the page compares names, so "Inglaterra", "América"
     * and "St Kitts" are covered too. A name here that the game does count costs no
     * more than a request the admin closes by hand, so it errs on the side of more.
     */
    static final Map<String, List<String>> NOT_COUNTED_IN_NAME_THEM_ALL = Map.ofEntries(
            // Part of the country.
            Map.entry("GBR", List.of("England", "Scotland", "Wales", "Northern Ireland",
                    "Inglaterra", "Escocia", "Gales", "Irlanda del Norte")),
            Map.entry("PSE", List.of("West Bank", "Gaza", "Cisjordania", "Franja de Gaza")),
            Map.entry("NLD", List.of("Holland", "Holanda")),
            Map.entry("BIH", List.of("Bosnia")),
            Map.entry("TTO", List.of("Trinidad")),
            Map.entry("ATG", List.of("Antigua")),
            Map.entry("KNA", List.of("Saint Kitts", "San Cristobal")),
            Map.entry("VCT", List.of("Saint Vincent", "San Vicente")),
            Map.entry("STP", List.of("Sao Tome", "Santo Tome")),
            // Ambiguous alone.
            Map.entry("USA", List.of("America")),
            // Former names.
            Map.entry("COD", List.of("Zaire")),
            Map.entry("ZWE", List.of("Rhodesia", "Rodesia")),
            Map.entry("IRN", List.of("Persia")),
            Map.entry("BEN", List.of("Dahomey")),
            Map.entry("BFA", List.of("Upper Volta", "Alto Volta")),
            Map.entry("BGD", List.of("East Pakistan")),
            Map.entry("BLR", List.of("Byelorussia", "White Russia")),
            Map.entry("BLZ", List.of("British Honduras")),
            Map.entry("KGZ", List.of("Kirghizia")),
            Map.entry("KHM", List.of("Kampuchea")),
            Map.entry("ETH", List.of("Abyssinia", "Abisinia")),
            Map.entry("GUY", List.of("British Guiana", "Guayana Britanica")),
            Map.entry("SUR", List.of("Dutch Guiana", "Guayana Holandesa")),
            Map.entry("LKA", List.of("Ceylon", "Ceilan")),
            Map.entry("THA", List.of("Siam")),
            Map.entry("TUV", List.of("Ellice Islands", "Islas Ellice")),
            Map.entry("TWN", List.of("Formosa")),
            Map.entry("VUT", List.of("New Hebrides", "Nuevas Hebridas")),
            Map.entry("WSM", List.of("Western Samoa", "Samoa Occidental")),
            Map.entry("SWZ", List.of("Swaziland", "Suazilandia", "Swazilandia")),
            Map.entry("MKD", List.of("Macedonia")));

    /** Spaces of every kind, line breaks and other control characters: read as one space. */
    private static final Pattern GAPS = Pattern.compile("[\\p{Z}\\p{Cc}\\s]+");
    private static final Pattern LETTER_OR_DIGIT = Pattern.compile("[\\p{L}\\p{N}]");

    private final GlobleNameRequestRepository requests;
    private final CountryCatalog catalog;
    /** Every place's names as the page matches them, built once: the catalogue never changes while running. */
    private final PlaceNames.Index names;
    private final GlobleClock clock;
    private final TransactionTemplate transaction;

    public NameRequestService(GlobleNameRequestRepository requests, CountryCatalog catalog, GlobleClock clock,
                              PlatformTransactionManager transactions) {
        this.requests = requests;
        this.catalog = catalog;
        this.names = PlaceNames.index(catalog.all());
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    /**
     * What {@link #send} did: stored a new request ({@code created}), or found the same
     * one already open and returned it.
     */
    public record Sent(NameRequestView request, boolean created) {
    }

    // ------------------------------------------------------------------ players

    /**
     * Sends a request. Refused with {@code bad-request}, storing nothing, for a code not
     * in the catalogue, a suggestion that is blank or too long once cleaned, or a
     * language or source the API does not know; with {@code too-many} when the player
     * already has {@value #MAX_OPEN_PER_PLAYER} open.
     */
    public Sent send(Player player, String rawCode, String rawSuggestion, String rawLang, String rawSource) {
        Country country = place(rawCode);
        String suggestion = clean(rawSuggestion).orElseThrow(GlobleException::badRequest);
        Lang lang = Lang.parse(rawLang).orElseThrow(GlobleException::badRequest);
        NameRequestSource source = NameRequestSource.parse(rawSource).orElseThrow(GlobleException::badRequest);
        return write(() -> {
            List<GlobleNameRequest> open = requests.findOpen(player);
            Optional<GlobleNameRequest> same = open.stream()
                    .filter(request -> request.getCode().equals(country.code())
                            && sameKey(request.getSuggestion(), suggestion))
                    .min(Comparator.comparing(GlobleNameRequest::getId));
            if (same.isPresent()) {
                return new Sent(view(same.get()), false);
            }
            if (open.size() >= MAX_OPEN_PER_PLAYER) {
                throw GlobleException.tooMany();
            }
            GlobleNameRequest saved = requests.save(
                    new GlobleNameRequest(player, country.code(), suggestion, lang, source, now()));
            return new Sent(view(saved), true);
        });
    }

    // ------------------------------------------------------------------ the admin

    /**
     * Everybody's requests, newest first: the open ones, or all of them. Closes first
     * the open ones the data already names (see the class comment).
     */
    public List<NameRequestView> list(boolean all) {
        return write(() -> {
            closeSettled();
            List<GlobleNameRequest> found = all
                    ? requests.findAllByOrderByCreatedAtDescIdDesc()
                    : requests.findByStatusOrderByCreatedAtDescIdDesc(NameRequestStatus.OPEN);
            return found.stream().map(NameRequestService::view).toList();
        });
    }

    /** How many requests are open, once those the data already names are closed. */
    public int openCount() {
        return write(() -> {
            closeSettled();
            return (int) requests.countByStatus(NameRequestStatus.OPEN);
        });
    }

    /**
     * Marks a request DONE or DISMISSED in the admin's name, or OPEN again. Marking it
     * with the status it has changes nothing. 404 for an id that does not exist.
     */
    public NameRequestView resolve(Player admin, long id, NameRequestStatus outcome) {
        return write(() -> {
            GlobleNameRequest request = requests.findById(id).orElseThrow(GlobleException::notFound);
            request.resolve(outcome, admin.username(), now());
            return view(requests.save(request));
        });
    }

    // ------------------------------------------------------------------ rules

    /** Closes, as DONE by the data, every open request whose suggestion the data now names. */
    private void closeSettled() {
        Instant now = now();
        for (GlobleNameRequest request : requests.findByStatusOrderByCreatedAtDescIdDesc(NameRequestStatus.OPEN)) {
            if (settled(request)) {
                request.resolve(NameRequestStatus.DONE, GlobleNameRequest.BY_DATA, now);
                requests.save(request);
            }
        }
    }

    /**
     * Whether the data already names the request's place the way it suggests, so that
     * the page counts it: it names that place and no other, and is not a name Name them
     * all refuses for it.
     */
    boolean settled(GlobleNameRequest request) {
        String code = request.getCode();
        String suggestion = request.getSuggestion();
        return names.namedPlace(suggestion).filter(code::equals).isPresent()
                && !PlaceNames.names(suggestion, NOT_COUNTED_IN_NAME_THEM_ALL.getOrDefault(code, List.of()));
    }

    /**
     * Whether two suggestions are the same as the page's matcher reads names: case,
     * accents, punctuation, spaces and a leading article aside ("Great Britain",
     * "great-britain", "GreatBritain"). Text with nothing the matcher reads (another
     * script, say) is compared as cleaned, ignoring case.
     */
    static boolean sameKey(String a, String b) {
        return key(a).equals(key(b));
    }

    private static String key(String suggestion) {
        String compact = PlaceNames.compact(suggestion);
        return compact.isEmpty() ? "=" + suggestion.toLowerCase(Locale.ROOT) : compact;
    }

    /**
     * The suggestion as stored: NFC, every gap one space, trimmed. Empty when nothing
     * is left, or no letter or digit, or more than the limit.
     */
    static Optional<String> clean(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = GAPS.matcher(Normalizer.normalize(raw, Normalizer.Form.NFC)).replaceAll(" ").strip();
        if (text.isEmpty() || !LETTER_OR_DIGIT.matcher(text).find()
                || text.codePointCount(0, text.length()) > GlobleNameRequest.MAX_SUGGESTION) {
            return Optional.empty();
        }
        return Optional.of(text);
    }

    /** Any code in the catalogue, territories included; case and surrounding spaces forgiven. */
    private Country place(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw GlobleException.badRequest();
        }
        return catalog.find(rawCode.trim().toUpperCase(Locale.ROOT)).orElseThrow(GlobleException::badRequest);
    }

    static NameRequestView view(GlobleNameRequest request) {
        Instant resolvedAt = request.getResolvedAt();
        return new NameRequestView(
                request.getId(),
                request.getCode(),
                request.getSuggestion(),
                request.getLang().api(),
                request.getSource().api(),
                request.getUsername(),
                request.getCreatedAt().toString(),
                request.getStatus().name(),
                resolvedAt == null ? null : resolvedAt.toString(),
                request.getResolvedBy());
    }

    /** To the millisecond, the precision the table keeps; see RollCallService#now. */
    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MILLIS);
    }

    /** One transaction, under the app-wide write lock; see GameService. */
    private <T> T write(Supplier<T> work) {
        return GlobleWrites.run(() -> transaction.execute(status -> work.get()));
    }
}
