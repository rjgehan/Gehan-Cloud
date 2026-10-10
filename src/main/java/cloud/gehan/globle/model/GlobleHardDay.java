package cloud.gehan.globle.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The Hard daily's answer for one date: the same shape as {@link GlobleDay}, in a
 * table of its own.
 *
 * <p><b>Why a second table rather than a column on globle_day.</b> globle_day is
 * keyed by the date alone, and production databases already have it.
 * {@code ddl-auto=update} only adds tables and columns; it cannot change a primary
 * key, so globle_day can never hold two rows for one date. A new table can be created
 * by the same update that adds the code, needs nothing done by hand, and keeps the
 * date as the key, which is what makes the first-draw race safe: two inserts for one
 * date cannot both succeed. Every row already in globle_day stays the classic answer
 * it always was.
 */
@Entity
@Table(name = "globle_hard_day")
public class GlobleHardDay extends PuzzleDay {

    protected GlobleHardDay() {
    }

    public GlobleHardDay(LocalDate date, String countryCode, int puzzleNumber, Instant createdAt) {
        super(date, countryCode, puzzleNumber, createdAt);
    }
}
