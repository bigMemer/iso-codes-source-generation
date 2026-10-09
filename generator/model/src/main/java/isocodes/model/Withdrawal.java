package isocodes.model;

/** Why a source lists a code as no longer in use. */
public enum Withdrawal {
    /** ISO withdrew the code. */
    WITHDRAWN,
    /**
     * The source chose to represent the code differently, not that ISO withdrew it. CLDR does this for subdivision
     * codes of territories it treats as countries ({@code US-PR} becomes {@code PR}).
     */
    REPRESENTED_ELSEWHERE
}
