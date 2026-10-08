/**
 * Reads Debian iso-codes JSON into the {@link isocodes.model} intermediate representation.
 *
 * <p>This is the only place that knows iso-codes' file names, JSON keys and release tags. Each JSON file is matched
 * against the known {@link isocodes.source.isocodes.Shape}s for its standard, newest first. A file that matches none,
 * for example because upstream added a field, fails the build rather than being silently misread.
 */
package isocodes.source.isocodes;
