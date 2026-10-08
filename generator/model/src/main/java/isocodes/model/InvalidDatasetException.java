package isocodes.model;

import java.util.List;

/** Thrown when source data breaks the model's rules. Lists the first problems found. */
public final class InvalidDatasetException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final int SHOWN = 20;

    private final transient List<String> problems;

    InvalidDatasetException(String source, List<String> problems) {
        super(source + " is invalid (" + problems.size() + " problems):\n  "
                + String.join("\n  ", problems.subList(0, Math.min(SHOWN, problems.size())))
                + (problems.size() > SHOWN ? "\n  ..." : ""));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
