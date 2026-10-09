/**
 * Combines contributions from several sources, plus reviewed overrides, into one {@link isocodes.model.SourceData}.
 *
 * <p>Knows nothing about any particular source, only the roles sources play: a <em>backbone</em> that proposes which
 * codes exist and is usually first to change, and an <em>authority</em> trusted to be right but sometimes stale.
 * The rules are in {@code docs/sources.md} §3 and §4.
 */
package isocodes.source.aggregate;
