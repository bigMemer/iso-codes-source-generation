package isocodes.model;

import java.util.List;

/**
 * Everything a source read, before upcasting. Each standard's entries may be in any schema version.
 *
 * @param sources every source the data came from; generated code inherits all their licences
 */
public record SourceData(
        List<SourceInfo> sources,
        List<? extends Country> countries,
        List<? extends Subdivision> subdivisions) {

    public SourceData {
        Require.nonNull(countries, subdivisions);
        sources = List.copyOf(sources);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Source data needs at least one source");
        }
    }
}
