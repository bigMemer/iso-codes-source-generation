/**
 * The intermediate representation between data sources and code emitters.
 *
 * <p>Each standard is a sealed interface whose nested records are numbered schema versions ({@code V1}, {@code V2},
 * ...). A source parses its input into whichever version matches, and {@link isocodes.model.IsoCodesDataset#fromSource}
 * upcasts everything to the newest version and validates it. Emitters only ever see the newest versions, through
 * each standard's {@code DEFINITION}.
 *
 * <p>Adding a field means adding a schema version and an upcaster from the previous one. Changing where the data comes
 * from means writing a new source; nothing in this package or in any emitter changes.
 */
package isocodes.model;
