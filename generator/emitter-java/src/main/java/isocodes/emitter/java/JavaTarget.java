package isocodes.emitter.java;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Where and how one standard is emitted as Java.
 *
 * @param standardId  the model standard's id, e.g. {@code 3166-1}
 * @param subPackage  package below the base package
 * @param className   simple name of the generated type
 * @param kind        enum, or class with chunked data holders
 */
record JavaTarget(String standardId, String subPackage, String className, Kind kind) {

    enum Kind {
        /** A Java enum; only possible when the data fits within JVM class-file limits. */
        ENUM,
        /** A final class whose instances are created by package-private data holder classes. */
        TABLE
    }

    /** ISO 3166-2 has thousands of entries, more than a single class can hold as enum constants. */
    static final Map<String, JavaTarget> BY_STANDARD = List.of(
                    new JavaTarget("3166-1", "iso3166", "Country", Kind.ENUM),
                    new JavaTarget("3166-2", "iso3166", "Subdivision", Kind.TABLE))
            .stream()
            .collect(Collectors.toUnmodifiableMap(JavaTarget::standardId, Function.identity()));

    static JavaTarget of(String standardId) {
        JavaTarget target = BY_STANDARD.get(standardId);
        if (target == null) {
            throw new IllegalStateException("No Java target configured for ISO " + standardId);
        }
        return target;
    }
}
