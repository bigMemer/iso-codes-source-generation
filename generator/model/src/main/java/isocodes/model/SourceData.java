package isocodes.model;

import java.util.List;
import java.util.Map;

/**
 * Everything a source read, before upcasting. Each standard's entries may be in any schema version.
 *
 * @param sources    every source the data came from; generated code inherits all their licences
 * @param lifecycles history per standard id, then per primary code; empty without history
 */
public record SourceData(
        List<SourceInfo> sources,
        List<? extends Country> countries,
        List<? extends Subdivision> subdivisions,
        Map<String, Map<String, Lifecycle>> lifecycles) {

    /** Source data without history. */
    public SourceData(List<SourceInfo> sources, List<? extends Country> countries,
            List<? extends Subdivision> subdivisions) {
        this(sources, countries, subdivisions, Map.of());
    }

    public SourceData {
        Require.nonNull(countries, subdivisions);
        sources = List.copyOf(sources);
        lifecycles = Map.copyOf(lifecycles);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Source data needs at least one source");
        }
    }
}
