/**
 * Reads Unicode CLDR's XML data into a {@link isocodes.model.Contribution}.
 *
 * <p>This is the only place that knows CLDR's file layout, its lower-case subdivision ids ({@code usca} for
 * {@code US-CA}), its {@code ~} range shorthand and its deprecation reasons.
 */
package isocodes.source.cldr;
