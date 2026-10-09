package isocodes.model;

import java.util.Map;
import java.util.Optional;

/** Field values keyed by field id, as handed to a standard's {@code fromFields}. */
public record FieldValues(String standardId, Map<String, String> values) {

    public FieldValues {
        values = Map.copyOf(values);
    }

    /** @throws IllegalArgumentException if the field is missing */
    public String required(String fieldId) {
        String value = values.get(fieldId);
        if (value == null) {
            throw new IllegalArgumentException("ISO " + standardId + ": missing required field " + fieldId + " in " + values);
        }
        return value;
    }

    public Optional<String> optional(String fieldId) {
        return Optional.ofNullable(values.get(fieldId));
    }
}
