package cloud.gehan.globle.view;

import java.util.List;

/**
 * A game as the client sees it. Dates and times are already strings so the JSON does
 * not depend on how the app's shared ObjectMapper happens to be configured.
 *
 * @param id           the game id for unlimited games; null for the daily, which is
 *                     addressed by date
 * @param mode         "daily" or "unlimited"
 * @param kind         "country" or "capital"; the daily is always country
 * @param region       "world", "africa", ...; the daily is always world
 * @param puzzle       the daily's puzzle number, else null
 * @param date         the daily's date (yyyy-MM-dd), else null
 * @param status       PLAYING, WON or GAVE_UP
 * @param guesses      in the order they were made; the winning guess is in here at 0 km
 * @param closestKm    the nearest guess so far, or null before the first
 * @param answer       null while PLAYING, always - this is the field that must never leak
 * @param nextPuzzleAt the daily only: the next local midnight, with its offset
 */
public record GameState(Long id, String mode, String kind, String region, Integer puzzle, String date,
                        String status, List<Guess> guesses, int guessCount, Integer closestKm,
                        Answer answer, String nextPuzzleAt) {

    /**
     * @param order   1 for the first guess
     * @param capital the guessed capital, in a capitals game only
     */
    public record Guess(String code, String name, String iso2, int km, int order, Place capital) {
    }

    /** The mystery, revealed. lat/lng is the country's label point. */
    public record Answer(String code, String name, String iso2, double lat, double lng, Place capital) {
    }

    public record Place(String name, double lat, double lng) {
    }
}
