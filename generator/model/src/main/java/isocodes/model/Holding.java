package isocodes.model;

import java.util.Objects;
import java.util.Optional;

/**
 * One period during which a code was assigned to one holder.
 *
 * @param holder    identifies the holder: two holdings with the same holder are the same place, e.g. a code
 *                  withdrawn and later assigned again to the same region
 * @param assigned  when ISO assigned the code to this holder
 * @param withdrawn when ISO withdrew it, or empty if the holder still has it
 */
public record Holding(String holder, DateRange assigned, Optional<DateRange> withdrawn) {

    public Holding {
        Objects.requireNonNull(holder);
        Objects.requireNonNull(assigned);
        Objects.requireNonNull(withdrawn);
    }
}
