package cloud.gehan.globle.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The classic daily's answer for one date. The columns are in {@link PuzzleDay}; this
 * table predates Hard and its shape has not changed.
 */
@Entity
@Table(name = "globle_day")
public class GlobleDay extends PuzzleDay {

    protected GlobleDay() {
    }

    public GlobleDay(LocalDate date, String countryCode, int puzzleNumber, Instant createdAt) {
        super(date, countryCode, puzzleNumber, createdAt);
    }
}
