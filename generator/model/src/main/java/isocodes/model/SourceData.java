package isocodes.model;

import java.util.List;
import java.util.Objects;

/**
 * Everything a source read, before upcasting. Each standard's entries may be in any schema version.
 *
 * @param sourceName    human-readable name of the source, e.g. {@code Debian iso-codes}
 * @param sourceVersion version of the source data, e.g. the iso-codes release {@code 4.20.1}
 * @param sourceLicense SPDX licence identifier of the source data, which generated code inherits
 */
public record SourceData(
        String sourceName,
        String sourceVersion,
        String sourceLicense,
        List<? extends Country> countries,
        List<? extends Subdivision> subdivisions) {

    public SourceData {
        Require.nonNull(countries, subdivisions);
        Objects.requireNonNull(sourceName);
        Objects.requireNonNull(sourceVersion);
        Objects.requireNonNull(sourceLicense);
    }
}
