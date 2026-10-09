package isocodes.source.aggregate;

/**
 * Something aggregation decided that a reviewer should know about.
 *
 * @param kind     what happened
 * @param standard e.g. {@code 3166-2}
 * @param code     the entry's primary code
 * @param detail   human-readable specifics
 */
public record Flag(Kind kind, String standard, String code, String detail) {

    public enum Severity {
        /** Blocks generation: a person must add an override. */
        ERROR,
        /** Generated, but a reviewer should check it. */
        REVIEW,
        /** Recorded for completeness. */
        INFO
    }

    public enum Kind {
        UNCONFIRMED(Severity.REVIEW, "In the backbone only; included, unconfirmed by the authority"),
        WITHDRAWN_BY_AUTHORITY(Severity.REVIEW, "The backbone still lists it, but the authority removed it; withdrawn"),
        WITHDRAWN_BY_BACKBONE(Severity.REVIEW, "The backbone withdrew it, but the authority still lists it; withdrawn"),
        ABSENT_FROM_BACKBONE(Severity.REVIEW, "The authority lists it but the backbone doesn't; included"),
        REPRESENTED_ELSEWHERE(Severity.INFO, "The backbone represents it differently; included from the authority"),
        EXCLUDED_BY_OVERRIDE(Severity.INFO, "Excluded by an override"),
        INCLUDED_BY_OVERRIDE(Severity.INFO, "Included by an override"),
        VALUE_FROM_OVERRIDE(Severity.INFO, "Value set by an override"),
        VALUE_FROM_FALLBACK(Severity.REVIEW, "Value taken from a lower-precedence source"),
        VALUE_DISAGREEMENT(Severity.REVIEW, "Sources disagree on a value; the higher precedence one was used"),
        MISSING_REQUIRED(Severity.ERROR, "A required field has no value from any source; add an override");

        private final Severity severity;
        private final String description;

        Kind(Severity severity, String description) {
            this.severity = severity;
            this.description = description;
        }

        public Severity severity() {
            return severity;
        }

        public String description() {
            return description;
        }
    }
}
