/**
 * Globle, a geography guessing game at {@code /globle}: two daily mystery countries
 * the whole family shares (classic, and Hard: blind, unprompted, and weighted toward
 * obscure countries), plus practice rounds by region, Mainland, Capitals and Hard.
 *
 * <p>The game is built to be removable. Everything it needs lives under this
 * package, {@code resources/globle}, {@code templates/globle} and
 * {@code static/globle}; nothing outside depends on it, and no existing class was
 * edited to add it. Every bean here is gated on {@code globle.enabled} (on unless set
 * to false), so switching it off, or deleting these directories, leaves the launcher
 * exactly as it was. The one-line touches to shared files (the apps.yml tile, a
 * commented block in application.properties, README rows, a .gitignore line) are
 * listed in docs/globle.md under "Removing Globle".
 *
 * <p>The dependency only runs one way: Globle reads the existing users table to know
 * who is playing, and the rest of the app never learns Globle exists.
 */
package cloud.gehan.globle;
