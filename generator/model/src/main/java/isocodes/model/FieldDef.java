package isocodes.model;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * One field of a standard's newest schema version.
 *
 * @param id          stable identifier, in ISO's snake_case naming (e.g. {@code alpha_2})
 * @param description one-line description, without trailing period
 * @param optional    whether entries may lack this field
 * @param pattern     the format every present value must match, if constrained
 * @param getter      reads the field from an entry
 * @param <T>         the record type
 */
public record FieldDef<T>(
        String id, String description, boolean optional, Optional<Pattern> pattern, Function<T, Optional<String>> getter) {

    public FieldDef {
        Objects.requireNonNull(id);
        Objects.requireNonNull(description);
        Objects.requireNonNull(pattern);
        Objects.requireNonNull(getter);
    }

    static <T> FieldDef<T> required(String id, String description, String regex, Function<T, String> getter) {
        return new FieldDef<>(id, description, false, compile(regex), row -> Optional.of(getter.apply(row)));
    }

    static <T> FieldDef<T> optional(
            String id, String description, String regex, Function<T, Optional<String>> getter) {
        return new FieldDef<>(id, description, true, compile(regex), getter);
    }

    private static Optional<Pattern> compile(String regex) {
        return Optional.ofNullable(regex).map(Pattern::compile);
    }

    public Optional<String> valueOf(T row) {
        return getter.apply(row);
    }
}
