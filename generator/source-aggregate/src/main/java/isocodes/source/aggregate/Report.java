package isocodes.source.aggregate;

import isocodes.model.SourceInfo;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Renders an aggregation result as Markdown, for attaching to a pull request. */
public final class Report {

    private Report() {}

    public static String markdown(Aggregator.Result result, Overrides overrides) {
        StringBuilder md = new StringBuilder("# Aggregation report\n\n");
        md.append("Sources: ").append(result.data().sources().stream().map(SourceInfo::toString)
                .collect(Collectors.joining(", "))).append(", plus ").append(overrides.all().size())
                .append(" overrides.\n\n");
        md.append("| Standard | Entries |\n|---|---|\n");
        md.append("| ISO 3166-1 | ").append(result.data().countries().size()).append(" |\n");
        md.append("| ISO 3166-2 | ").append(result.data().subdivisions().size()).append(" |\n\n");

        Map<Flag.Kind, List<Flag>> byKind = new TreeMap<>(result.flags().stream()
                .collect(Collectors.groupingBy(Flag::kind)));
        if (byKind.isEmpty()) {
            return md.append("Nothing flagged.\n").toString();
        }
        md.append("| Severity | Kind | Count |\n|---|---|---|\n");
        byKind.forEach((kind, flags) -> md.append("| ").append(kind.severity()).append(" | ").append(kind.description())
                .append(" | ").append(flags.size()).append(" |\n"));
        byKind.forEach((kind, flags) -> {
            md.append("\n## ").append(kind.severity()).append(": ").append(kind.description()).append("\n\n");
            for (Flag flag : flags) {
                md.append("- ISO ").append(flag.standard()).append(" `").append(flag.code()).append("`");
                if (!flag.detail().equals(kind.description())) {
                    md.append(": ").append(flag.detail());
                }
                md.append('\n');
            }
        });
        return md.toString();
    }
}
