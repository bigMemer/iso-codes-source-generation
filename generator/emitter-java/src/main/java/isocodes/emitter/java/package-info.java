/**
 * Emits Java sources from an {@link isocodes.model.IsoCodesDataset}.
 *
 * <p>Knows nothing about where the data came from. Everything Java-specific (type names, packages, enum versus class,
 * accessor naming) lives here; everything about the data itself comes from each standard's
 * {@link isocodes.model.StandardDef}.
 */
package isocodes.emitter.java;
