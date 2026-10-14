package cloud.gehan.globle.view;

/**
 * A "request a name change" as the API returns it: to the player who sent it, and in
 * the admin's list. Times are ISO-8601 instants to the millisecond, as in
 * {@link RollCallState}.
 *
 * @param id         for the admin's resolve route
 * @param code       the place the name is for (a country or a territory)
 * @param suggestion the name the player thinks should count, trimmed, as typed
 * @param lang       the page's language when it was sent: "en" or "es"
 * @param source     where on the page it was sent from: "named", "missed", "study" or "other"
 * @param username   who sent it
 * @param createdAt  when it was sent
 * @param status     OPEN, DONE or DISMISSED
 * @param resolvedAt when it was marked DONE or DISMISSED; null while OPEN
 * @param resolvedBy the admin who marked it, or "data" when it closed by itself because
 *                   the data already names the place that way; null while OPEN
 */
public record NameRequestView(long id, String code, String suggestion, String lang, String source,
                              String username, String createdAt, String status, String resolvedAt,
                              String resolvedBy) {
}
