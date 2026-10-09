package isocodes.source.aggregate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reviewed corrections, maintained by hand in this repository. They win over every source.
 *
 * @param byStandard overrides per standard id, keyed by primary code
 */
public record Overrides(Map<String, Map<String, Override>> byStandard) {

    public enum Action {
        /** Leave the code out, whatever sources say. */
        EXCLUDE,
        /** Include the code, whatever sources say. */
        INCLUDE,
        /** Only set {@link Override#values()}; inclusion follows the normal rules. */
        SET
    }

    /**
     * @param code     primary code
     * @param action   what to do
     * @param values   field values to use, by field id; win over every source
     * @param reason   why, in a sentence
     * @param evidence where the reason can be checked, e.g. a link to ISO's change notice
     */
    public record Override(String code, Action action, Map<String, String> values, String reason, String evidence) {

        public Override {
            Objects.requireNonNull(code);
            Objects.requireNonNull(action);
            values = Map.copyOf(values);
            if (reason == null || reason.isBlank() || evidence == null || evidence.isBlank()) {
                throw new IllegalArgumentException("Override for " + code + " needs a reason and evidence");
            }
        }
    }

    public static final Overrides NONE = new Overrides(Map.of());

    public Overrides {
        Map<String, Map<String, Override>> copy = new LinkedHashMap<>();
        byStandard.forEach((standard, overrides) -> copy.put(standard, Map.copyOf(overrides)));
        byStandard = Map.copyOf(copy);
    }

    public Map<String, Override> forStandard(String standardId) {
        return byStandard.getOrDefault(standardId, Map.of());
    }

    /**
     * Parses the overrides file:
     *
     * <pre>{@code
     * {
     *   "3166-1": [
     *     {"code": "XK", "action": "exclude", "reason": "...", "evidence": "..."}
     *   ],
     *   "3166-2": [
     *     {"code": "XX-01", "action": "set", "values": {"type": "Province"}, "reason": "...", "evidence": "..."}
     *   ]
     * }
     * }</pre>
     */
    public static Overrides parse(String json) {
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException("Overrides file is not valid JSON", e);
        }
        Map<String, Map<String, Override>> byStandard = new LinkedHashMap<>();
        root.properties().forEach(standard -> {
            Map<String, Override> overrides = new LinkedHashMap<>();
            for (JsonNode node : standard.getValue()) {
                Map<String, String> values = new LinkedHashMap<>();
                node.path("values").properties().forEach(v -> values.put(v.getKey(), v.getValue().asText()));
                Override override = new Override(
                        text(node, "code"),
                        Action.valueOf(text(node, "action").toUpperCase(java.util.Locale.ROOT)),
                        values,
                        text(node, "reason"),
                        text(node, "evidence"));
                if (overrides.put(override.code(), override) != null) {
                    throw new IllegalArgumentException("Duplicate override for " + override.code());
                }
            }
            byStandard.put(standard.getKey(), overrides);
        });
        return new Overrides(byStandard);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("Override is missing \"" + field + "\": " + node);
        }
        return value.asText();
    }

    /** All overrides, for reporting. */
    public List<Override> all() {
        List<Override> all = new ArrayList<>();
        byStandard.values().forEach(m -> all.addAll(m.values()));
        return all;
    }
}
