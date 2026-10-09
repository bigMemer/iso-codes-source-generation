package isocodes.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/** The {@code isoCodes { }} block. */
public abstract class IsoCodesExtension {

    /** The iso-codes release to use as the authority, e.g. {@code 4.20.1}. */
    public abstract Property<String> getVersion();

    /** The CLDR release to use as the backbone, e.g. {@code 48.2}. */
    public abstract Property<String> getCldrVersion();

    /** The dataset version recorded in the generated code, e.g. {@code 2026.10.0}. */
    public abstract Property<String> getDatasetVersion();

    /** Root Java package for generated sources. */
    public abstract Property<String> getBasePackage();

    /** SPDX expression for our own contribution, e.g. {@code Apache-2.0 OR MIT}. */
    public abstract Property<String> getOwnLicense();

    /** Reviewed overrides (JSON). Optional. */
    public abstract RegularFileProperty getOverridesFile();
}
