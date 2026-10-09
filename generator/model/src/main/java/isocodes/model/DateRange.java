package isocodes.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * The days on which something could have happened. Both ends inclusive; an absent end is unbounded.
 *
 * @param earliest the earliest possible day, if known
 * @param latest   the latest possible day, if known
 */
public record DateRange(Optional<LocalDate> earliest, Optional<LocalDate> latest) {

    public DateRange {
        Objects.requireNonNull(earliest);
        Objects.requireNonNull(latest);
        if (earliest.isPresent() && latest.isPresent() && earliest.get().isAfter(latest.get())) {
            throw new IllegalArgumentException("Empty range: " + earliest.get() + " to " + latest.get());
        }
    }

    public static final DateRange UNKNOWN = new DateRange(Optional.empty(), Optional.empty());

    public static DateRange exact(LocalDate day) {
        return new DateRange(Optional.of(day), Optional.of(day));
    }

    public static DateRange between(LocalDate earliest, LocalDate latest) {
        return new DateRange(Optional.of(earliest), Optional.of(latest));
    }

    public static DateRange noLaterThan(LocalDate latest) {
        return new DateRange(Optional.empty(), Optional.of(latest));
    }

    /** Parses ISO 8601 at day, month or year precision: {@code 2006-09-26}, {@code 1993-06}, {@code 1977}. */
    public static DateRange parse(String text) {
        String t = text.trim();
        if (t.matches("\\d{4}")) {
            int year = Integer.parseInt(t);
            return between(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        }
        if (t.matches("\\d{4}-\\d{2}")) {
            LocalDate first = LocalDate.parse(t + "-01");
            return between(first, first.withDayOfMonth(first.lengthOfMonth()));
        }
        return exact(LocalDate.parse(t.substring(0, 10)));
    }

    public boolean isExact() {
        return earliest.isPresent() && earliest.equals(latest);
    }

    @Override
    public String toString() {
        return isExact() ? earliest.get().toString()
                : earliest.map(Object::toString).orElse("?") + ".." + latest.map(Object::toString).orElse("?");
    }
}
