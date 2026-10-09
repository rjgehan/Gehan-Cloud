package cloud.gehan.globle.model;

/**
 * A signed-in user as Globle sees them: their row id in the users table and their
 * username.
 *
 * <p>Games are owned by the pair, not by the id alone. On SQLite an id is a rowid
 * declared without AUTOINCREMENT, so deleting the newest account and creating another
 * hands the new one the same id. Matching the username too means the newcomer starts
 * with a clean record instead of inheriting a deleted person's streak, and their
 * daily game does not collide with the old account's row for the same date.
 */
public record Player(long id, String username) {
}
