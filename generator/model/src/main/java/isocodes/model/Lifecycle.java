package isocodes.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything known about one code's history.
 *
 * @param holdings      the code's holders over time, oldest first; the last is the entry's own holder
 * @param recordedSince the earliest date from which the sources account for the code's status
 */
public record Lifecycle(List<Holding> holdings, LocalDate recordedSince) {

    public Lifecycle {
        holdings = List.copyOf(holdings);
        Objects.requireNonNull(recordedSince);
        if (holdings.isEmpty()) {
            throw new IllegalArgumentException("A lifecycle needs at least one holding");
        }
        for (int i = 0; i < holdings.size() - 1; i++) {
            if (holdings.get(i).withdrawn().isEmpty()) {
                throw new IllegalArgumentException("Only the last holding can be current: " + holdings);
            }
        }
    }

    /** The entry's own holding. */
    public Holding current() {
        return holdings.get(holdings.size() - 1);
    }

    public boolean isWithdrawn() {
        return current().withdrawn().isPresent();
    }

    public Optional<DateRange> withdrawn() {
        return current().withdrawn();
    }
}
