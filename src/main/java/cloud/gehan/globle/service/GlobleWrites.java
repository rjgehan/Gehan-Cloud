package cloud.gehan.globle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;

import java.util.function.Supplier;

/**
 * The one lock every Globle write runs under, app-wide rather than per player.
 *
 * <p>SQLite has one writer at a time. A transaction starts deferred, so its first read
 * takes a shared lock, and if it then tries to write while another connection is
 * already writing, SQLite reports the deadlock at once with SQLITE_BUSY instead of
 * waiting out the busy timeout. Two players guessing a few milliseconds apart would
 * each read their game and then both try to save it, and one of them would get an
 * error. Queuing Globle's write transactions behind each other here means only one is
 * ever open; at family scale the wait is a few milliseconds.
 */
final class GlobleWrites {

    static final Object LOCK = new Object();

    private static final Logger log = LoggerFactory.getLogger(GlobleWrites.class);

    private GlobleWrites() {
    }

    /**
     * Runs {@code work} under the lock. The lock cannot hold off the rest of the app's
     * own writes (a first login, the user console), so if SQLite still answers busy the
     * work runs once more. {@code work} must be a whole transaction, or several that each
     * stand alone: the one that failed has rolled back, so running it again is safe.
     */
    static <T> T run(Supplier<T> work) {
        synchronized (LOCK) {
            try {
                return work.get();
            } catch (PessimisticLockingFailureException busy) {
                log.debug("Globle write met another writer; trying it once more", busy);
                return work.get();
            }
        }
    }
}
