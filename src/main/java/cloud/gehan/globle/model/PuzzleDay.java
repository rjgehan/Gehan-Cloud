package cloud.gehan.globle.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The answer to one day's puzzle, chosen the first time that day is needed and then
 * fixed, so every member of the family gets the same country. One table per daily
 * variant: {@link GlobleDay} for the classic puzzle, {@link GlobleHardDay} for Hard.
 *
 * <p>The answer is stored rather than derived (from a seeded shuffle, say) because the
 * source is public: anything computable from the date could be computed by a player.
 *
 * <p>The key is the date as ISO text rather than a DATE column; see
 * {@link GlobleColumns} for why. JPA does not apply converters to an id, so the text
 * is the field itself and {@link #getDate()} parses it.
 */
@MappedSuperclass
public abstract class PuzzleDay implements Persistable<String> {

    @Id
    @Column(name = "puzzle_date", length = 10, nullable = false)
    private String puzzleDate;

    @Column(name = "country_code", length = 8, nullable = false)
    private String countryCode;

    @Column(name = "puzzle_number", nullable = false)
    private int puzzleNumber;

    @Convert(converter = GlobleColumns.InstantMillis.class)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Makes save() an INSERT. With an assigned id Spring Data would otherwise merge,
     * and a merge that lost a race would quietly overwrite the day's answer with
     * another one; an insert fails on the primary key instead, which is what lets
     * DailyPuzzles notice and keep the winner.
     */
    @Transient
    private boolean fresh;

    protected PuzzleDay() {
    }

    protected PuzzleDay(LocalDate date, String countryCode, int puzzleNumber, Instant createdAt) {
        this.puzzleDate = date.toString();
        this.countryCode = countryCode;
        this.puzzleNumber = puzzleNumber;
        this.createdAt = createdAt;
        this.fresh = true;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        this.fresh = false;
    }

    @Override
    public String getId() {
        return puzzleDate;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    public LocalDate getDate() {
        return LocalDate.parse(puzzleDate);
    }

    public String getCountryCode() {
        return countryCode;
    }

    public int getPuzzleNumber() {
        return puzzleNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
