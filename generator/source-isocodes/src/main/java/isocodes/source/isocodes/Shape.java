package isocodes.source.isocodes;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * One known layout of a standard's JSON entries. The schema number matches the model record version it parses into.
 *
 * @param schema   schema number, e.g. {@code 2} for entries parsed into {@code Country.V2}
 * @param required keys every entry has
 * @param optional keys some entries have
 * @param parser   converts one entry; only called once the entry is known to fit this shape
 * @param <R>      the model record type
 */
record Shape<R>(int schema, Set<String> required, Set<String> optional, Function<JsonNode, R> parser) {

    Shape {
        required = Set.copyOf(required);
        optional = Set.copyOf(optional);
    }

    /** Describes why the entries don't fit this shape, or returns empty if they do. */
    Optional<String> mismatch(List<JsonNode> rows) {
        Set<String> unexpected = new TreeSet<>();
        Set<String> missing = new TreeSet<>();
        Set<String> notText = new TreeSet<>();
        for (JsonNode row : rows) {
            if (!row.isObject()) {
                return Optional.of("entry is not a JSON object: " + row);
            }
            row.properties().forEach(e -> {
                if (!required.contains(e.getKey()) && !optional.contains(e.getKey())) {
                    unexpected.add(e.getKey());
                }
                if (!e.getValue().isTextual()) {
                    notText.add(e.getKey());
                }
            });
            required.stream().filter(key -> !row.has(key)).forEach(missing::add);
        }
        List<String> reasons = new ArrayList<>();
        if (!unexpected.isEmpty()) {
            reasons.add("unknown fields " + unexpected);
        }
        if (!missing.isEmpty()) {
            reasons.add("some entries lack required fields " + missing);
        }
        if (!notText.isEmpty()) {
            reasons.add("non-string values in " + notText);
        }
        return reasons.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", reasons));
    }

    static String text(JsonNode row, String key) {
        return row.get(key).asText();
    }

    static Optional<String> optionalText(JsonNode row, String key) {
        return Optional.ofNullable(row.get(key)).map(JsonNode::asText);
    }
}
