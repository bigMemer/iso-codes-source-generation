package isocodes.emitter.java;

import java.util.Objects;
import java.util.Optional;

/**
 * How to emit.
 *
 * @param basePackage    root Java package for generated sources
 * @param datasetVersion the dataset version recorded in {@code IsoCodes.VERSION}
 * @param ownLicense     SPDX expression for our own contribution (generated structure, overrides), combined with the
 *                       sources' licences in every file header; empty to use only the sources' licences
 */
public record EmitOptions(String basePackage, String datasetVersion, Optional<String> ownLicense) {

    public EmitOptions {
        Objects.requireNonNull(basePackage);
        Objects.requireNonNull(datasetVersion);
        Objects.requireNonNull(ownLicense);
    }
}
