package isocodes.source.history;

import isocodes.model.Country;
import isocodes.model.Lifecycle;
import isocodes.model.SourceData;
import isocodes.model.SourceInfo;
import isocodes.model.Subdivision;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What history reasoning concluded.
 *
 * @param lifecycles per standard, per code
 * @param withdrawn  per standard, per withdrawn code: its last known field values
 * @param notes      everything a reviewer should see
 */
public record History(
        Map<String, Map<String, Lifecycle>> lifecycles,
        Map<String, Map<String, Map<String, String>>> withdrawn,
        List<HistoryNote> notes) {

    /** Reads evidence and reasons about it against the current aggregated data. */
    public static History of(Evidence evidence, SourceData current) {
        return HistoryReasoner.reason(evidence, Map.of(
                "3166-1", current.countries().stream().map(c -> c.toLatest().alpha2()).collect(Collectors.toSet()),
                "3166-2", current.subdivisions().stream().map(s -> s.toLatest().code()).collect(Collectors.toSet())));
    }

    /**
     * Adds withdrawn entries (after the current ones, sorted by code) and lifecycles to current data. Withdrawn codes
     * whose last known values don't satisfy their type are left out and noted.
     */
    public Result apply(SourceData current, SourceInfo historySource) {
        List<HistoryNote> allNotes = new ArrayList<>(notes);
        List<Country.V2> countries = new ArrayList<>(current.countries().stream().map(Country::toLatest).toList());
        countries.addAll(build("3166-1", values -> {
            Map<String, String> v = new LinkedHashMap<>(values);
            v.put("flag", Country.flagOf(v.get("alpha_2")));
            return Country.DEFINITION.build(v);
        }, allNotes));
        List<Subdivision.V1> subdivisions = new ArrayList<>(current.subdivisions().stream().map(Subdivision::toLatest).toList());
        subdivisions.addAll(build("3166-2", Subdivision.DEFINITION::build, allNotes));

        List<SourceInfo> sources = new ArrayList<>(current.sources());
        sources.add(historySource);
        return new Result(new SourceData(sources, countries, subdivisions, lifecycles), allNotes);
    }

    private <T> List<T> build(String standard, Function<Map<String, String>, T> builder, List<HistoryNote> notes) {
        List<T> rows = new ArrayList<>();
        new TreeMap<>(withdrawn.get(standard)).forEach((code, values) -> {
            try {
                rows.add(builder.apply(values));
            } catch (IllegalArgumentException e) {
                notes.add(new HistoryNote(HistoryNote.Kind.UNBUILDABLE, standard, code, e.getMessage()));
            }
        });
        return rows;
    }

    /**
     * @param data  current entries plus buildable withdrawn ones, with lifecycles
     * @param notes everything a reviewer should see
     */
    public record Result(SourceData data, List<HistoryNote> notes) {

        /** Markdown section for the aggregation report. */
        public String markdown() {
            StringBuilder md = new StringBuilder("\n# History\n\n");
            md.append("| Standard | Withdrawn entries |\n|---|---|\n");
            for (String standard : List.of("3166-1", "3166-2")) {
                long count = data.lifecycles().getOrDefault(standard, Map.of()).values().stream()
                        .filter(Lifecycle::isWithdrawn).count();
                md.append("| ISO ").append(standard).append(" | ").append(count).append(" |\n");
            }
            Map<HistoryNote.Kind, List<HistoryNote>> byKind = new TreeMap<>(notes.stream()
                    .collect(Collectors.groupingBy(HistoryNote::kind)));
            byKind.forEach((kind, list) -> {
                md.append("\n## ").append(kind.description()).append(" (").append(list.size()).append(")\n\n");
                list.forEach(n -> md.append("- ISO ").append(n.standard()).append(" `").append(n.code()).append("`: ")
                        .append(n.detail()).append('\n'));
            });
            return md.toString();
        }
    }
}
