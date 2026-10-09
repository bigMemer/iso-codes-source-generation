package isocodes.source.history;

/**
 * Something history reasoning decided that a reviewer should know about.
 *
 * @param kind     what happened
 * @param standard e.g. {@code 3166-2}
 * @param code     the code
 * @param detail   specifics
 */
public record HistoryNote(Kind kind, String standard, String code, String detail) {

    public enum Kind {
        HOLDER_CHANGED("A code returned under a different name; treated as a different holder"),
        GLITCH_CLOSED("A short gap in iso-codes with the same holder; treated as continuous"),
        UNCONFIRMED_PAST_CODE("A past code only CLDR listed; not an entry"),
        UNBUILDABLE("A withdrawn code lacks values its type requires; not an entry"),
        INCONSISTENT("History and current aggregation disagree");

        private final String description;

        Kind(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }
}
