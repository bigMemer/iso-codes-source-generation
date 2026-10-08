package isocodes.gradle;

import org.gradle.api.provider.Property;

/** The {@code isoCodes { }} block. */
public abstract class IsoCodesExtension {

    /** The iso-codes release to generate from, e.g. {@code 4.20.1}. */
    public abstract Property<String> getVersion();

    /** Root Java package for generated sources. */
    public abstract Property<String> getBasePackage();
}
