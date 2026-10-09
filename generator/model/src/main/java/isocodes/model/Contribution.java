package isocodes.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What one source says about every standard, before aggregation: current entries as field values keyed by field id,
 * and codes the source lists as withdrawn. A source may leave out fields it doesn't have.
 *
 * @param source    the source
 * @param standards per standard id ({@code 3166-1}, {@code 3166-2})
 */
public record Contribution(SourceInfo source, Map<String, Standard> standards) {

    public Contribution {
        Objects.requireNonNull(source);
        standards = Map.copyOf(standards);
    }

    /**
     * One standard's part of a contribution.
     *
     * @param entries   current entries keyed by primary code, in the source's order
     * @param withdrawn codes the source lists as no longer in use, with why
     */
    public record Standard(Map<String, Map<String, String>> entries, Map<String, Withdrawal> withdrawn) {

        public Standard {
            Map<String, Map<String, String>> copy = new LinkedHashMap<>();
            entries.forEach((code, fields) -> copy.put(code, Map.copyOf(fields)));
            entries = Collections.unmodifiableMap(copy);
            withdrawn = Map.copyOf(withdrawn);
        }
    }

    public Standard standard(String standardId) {
        return standards.getOrDefault(standardId, new Standard(Map.of(), Map.of()));
    }

    /** Converts complete source data, upcast to the newest schema versions, into a contribution. */
    public static Contribution of(SourceData data, Map<String, Map<String, Withdrawal>> withdrawnByStandard) {
        IsoCodesDataset dataset = IsoCodesDataset.fromSource(data);
        Map<String, Standard> standards = new LinkedHashMap<>();
        for (Table<?> table : dataset.tables()) {
            standards.put(table.standard().id(), standardOf(table,
                    withdrawnByStandard.getOrDefault(table.standard().id(), Map.of())));
        }
        if (data.sources().size() != 1) {
            throw new IllegalArgumentException("A contribution comes from exactly one source, not " + data.sources());
        }
        return new Contribution(data.sources().get(0), standards);
    }

    private static <T> Standard standardOf(Table<T> table, Map<String, Withdrawal> withdrawn) {
        Map<String, Map<String, String>> entries = new LinkedHashMap<>();
        for (T row : table.rows()) {
            Map<String, String> fields = table.standard().toFields(row);
            entries.put(fields.get(table.standard().primaryKey()), fields);
        }
        return new Standard(entries, withdrawn);
    }
}
