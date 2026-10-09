package isocodes.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Describes a standard's newest schema version, so emitters can work generically over every standard.
 *
 * @param id           the standard's number, e.g. {@code 3166-1}
 * @param summary      one-sentence description
 * @param fields       the fields, in canonical order
 * @param primaryKey   id of the required, unique field that identifies an entry
 * @param uniqueFields ids of fields whose present values are unique across entries, primary key first
 * @param fromFields   builds a record from field values keyed by field id; absent keys are absent values
 * @param introduced   when ISO first published each code field, by field id (output spec §6.7)
 * @param <T>          the newest record type
 */
public record StandardDef<T>(
        String id,
        String summary,
        List<FieldDef<T>> fields,
        String primaryKey,
        List<String> uniqueFields,
        Function<FieldValues, T> fromFields,
        Map<String, DateRange> introduced) {

    public StandardDef {
        fields = List.copyOf(fields);
        uniqueFields = List.copyOf(uniqueFields);
        introduced = Map.copyOf(introduced);
        if (uniqueFields.isEmpty() || !uniqueFields.get(0).equals(primaryKey)) {
            throw new IllegalArgumentException(id + ": uniqueFields must start with the primary key");
        }
        for (String unique : uniqueFields) {
            if (fields.stream().noneMatch(f -> f.id().equals(unique))) {
                throw new IllegalArgumentException(id + ": unknown unique field " + unique);
            }
        }
        if (field(fields, primaryKey).optional()) {
            throw new IllegalArgumentException(id + ": primary key " + primaryKey + " must be required");
        }
    }

    /** The record's present field values, keyed by field id, in field order. */
    public Map<String, String> toFields(T row) {
        Map<String, String> values = new LinkedHashMap<>();
        for (FieldDef<T> field : fields) {
            field.valueOf(row).ifPresent(value -> values.put(field.id(), value));
        }
        return values;
    }

    /**
     * Builds a record from field values keyed by field id.
     *
     * @throws IllegalArgumentException if a required field is missing
     */
    public T build(Map<String, String> values) {
        return fromFields.apply(new FieldValues(id, values));
    }

    public FieldDef<T> field(String fieldId) {
        return field(fields, fieldId);
    }

    private static <T> FieldDef<T> field(List<FieldDef<T>> fields, String fieldId) {
        return fields.stream()
                .filter(f -> f.id().equals(fieldId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No field " + fieldId));
    }
}
