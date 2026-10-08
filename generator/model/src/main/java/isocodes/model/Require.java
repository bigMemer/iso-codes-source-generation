package isocodes.model;

import java.util.Objects;

final class Require {

    private Require() {}

    /** Records in this package never hold null; absent optional fields are {@code Optional.empty()}. */
    static void nonNull(Object... values) {
        for (Object value : values) {
            Objects.requireNonNull(value);
        }
    }
}
