package cloud.gehan.globle.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * How Globle's columns are stored, chosen for SQLite in production and H2 in tests.
 *
 * <ul>
 * <li><b>Dates as ISO text</b> ("2026-10-09"). The SQLite driver stores a
 *     {@code java.sql.Date} as epoch milliseconds at local midnight, so the stored day
 *     depends on the JVM's time zone and can shift by one if the container's zone
 *     changes. Text cannot shift, and ISO dates still sort and compare in order.</li>
 * <li><b>Instants as epoch milliseconds.</b> Unambiguous, and orders correctly.</li>
 * <li><b>Enums as their name, through a converter.</b> Hibernate gives an
 *     {@code @Enumerated} column a CHECK constraint listing today's values, and
 *     {@code ddl-auto=update} cannot alter one on SQLite, so a value added later
 *     would be rejected forever. A converter column is plain text.</li>
 * </ul>
 *
 * <p>Every converter is {@code autoApply = false} and referenced explicitly by
 * {@code @Convert}: an auto-applied converter would change how every entity in the
 * application maps that type, and nothing in Globle may reach that far.
 */
public final class GlobleColumns {

    private GlobleColumns() {
    }

    @Converter(autoApply = false)
    public static class DateText implements AttributeConverter<LocalDate, String> {
        @Override
        public String convertToDatabaseColumn(LocalDate date) {
            return date == null ? null : date.toString();
        }

        @Override
        public LocalDate convertToEntityAttribute(String text) {
            return text == null || text.isBlank() ? null : LocalDate.parse(text.trim());
        }
    }

    @Converter(autoApply = false)
    public static class InstantMillis implements AttributeConverter<Instant, Long> {
        @Override
        public Long convertToDatabaseColumn(Instant instant) {
            return instant == null ? null : instant.toEpochMilli();
        }

        @Override
        public Instant convertToEntityAttribute(Long millis) {
            return millis == null ? null : Instant.ofEpochMilli(millis);
        }
    }

    /** Stores an enum by name. Subclassed per enum because JPA converters cannot be generic. */
    abstract static class EnumName<E extends Enum<E>> implements AttributeConverter<E, String> {
        private final Class<E> type;

        EnumName(Class<E> type) {
            this.type = type;
        }

        @Override
        public String convertToDatabaseColumn(E value) {
            return value == null ? null : value.name();
        }

        @Override
        public E convertToEntityAttribute(String name) {
            return name == null ? null : Enum.valueOf(type, name.trim());
        }
    }

    @Converter(autoApply = false)
    public static class ModeName extends EnumName<Mode> {
        public ModeName() {
            super(Mode.class);
        }
    }

    @Converter(autoApply = false)
    public static class KindName extends EnumName<Kind> {
        public KindName() {
            super(Kind.class);
        }
    }

    @Converter(autoApply = false)
    public static class RegionName extends EnumName<Region> {
        public RegionName() {
            super(Region.class);
        }
    }

    @Converter(autoApply = false)
    public static class StatusName extends EnumName<Status> {
        public StatusName() {
            super(Status.class);
        }
    }

    @Converter(autoApply = false)
    public static class VariantName extends EnumName<Variant> {
        public VariantName() {
            super(Variant.class);
        }
    }
}
