package isocodes.model;

import java.util.Objects;

/**
 * Identifies one source of data.
 *
 * @param name    human-readable name, e.g. {@code Debian iso-codes}
 * @param version the source's own version, e.g. {@code 4.20.1}
 * @param license SPDX licence identifier of the source's data
 */
public record SourceInfo(String name, String version, String license) {

    public SourceInfo {
        Objects.requireNonNull(name);
        Objects.requireNonNull(version);
        Objects.requireNonNull(license);
    }

    @Override
    public String toString() {
        return name + " " + version;
    }
}
