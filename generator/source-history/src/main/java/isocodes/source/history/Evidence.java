package isocodes.source.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The contents of {@code history/iso3166-evidence.json}: what each source said about each code, and when. Written by
 * {@code scripts/build_history.py}; no conclusions drawn.
 *
 * @param isoCodesObservedFrom date of the first iso-codes snapshot
 * @param cldrObservedFrom     date of the first CLDR release
 * @param changeLogCommit      the iso3166-updates commit the change log was read from
 * @param codes                per standard id, per code
 * @param formerCountries      ISO 3166-3 records from iso-codes
 * @param countryChangeLog     ISO's change log, per country
 */
public record Evidence(
        LocalDate isoCodesObservedFrom,
        LocalDate cldrObservedFrom,
        String changeLogCommit,
        Map<String, Map<String, CodeEvidence>> codes,
        List<Map<String, String>> formerCountries,
        Map<String, List<ChangeLogEntry>> countryChangeLog) {

    /**
     * A continuous run of snapshots in which a source listed a code.
     *
     * @param firstSeen first snapshot listing it
     * @param lastSeen  last snapshot listing it
     * @param goneBy    first later snapshot not listing it; empty if still listed
     * @param values    the code's values in the last snapshot of the run
     * @param names     every name the code had during the run, in order
     */
    public record Interval(LocalDate firstSeen, LocalDate lastSeen, Optional<LocalDate> goneBy, Map<String, String> values,
            List<Name> names) {}

    /**
     * A name a code had for part of an interval.
     *
     * @param firstSeen first snapshot with this name
     * @param lastSeen  last snapshot with this name
     * @param name      the name
     */
    public record Name(LocalDate firstSeen, LocalDate lastSeen, String name) {}

    /**
     * An entry in ISO's change log.
     *
     * @param date the issue date (the first date, when the entry gives a correction date too), if any
     * @param text the change and its description
     */
    public record ChangeLogEntry(Optional<LocalDate> date, String text) {}

    /**
     * Everything the sources said about one code.
     *
     * @param isoCodes  intervals in iso-codes
     * @param cldr      intervals in CLDR
     * @param changeLog change-log entries naming the code
     */
    public record CodeEvidence(List<Interval> isoCodes, List<Interval> cldr, List<ChangeLogEntry> changeLog) {}

    public static Evidence parse(String json) {
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException("History evidence is not valid JSON", e);
        }
        Map<String, Map<String, CodeEvidence>> codes = new LinkedHashMap<>();
        for (String standard : List.of("3166-1", "3166-2")) {
            Map<String, CodeEvidence> byCode = new LinkedHashMap<>();
            root.path(standard).properties().forEach(e -> byCode.put(e.getKey(), new CodeEvidence(
                    intervals(e.getValue().path("iso-codes")),
                    intervals(e.getValue().path("cldr")),
                    entries(e.getValue().path("change-log")))));
            codes.put(standard, byCode);
        }
        List<Map<String, String>> former = new ArrayList<>();
        root.path("former-countries").forEach(node -> former.add(strings(node)));
        Map<String, List<ChangeLogEntry>> changeLog = new LinkedHashMap<>();
        root.path("change-log").properties().forEach(e -> changeLog.put(e.getKey(), entries(e.getValue())));
        return new Evidence(
                LocalDate.parse(root.path("sources").path("iso-codes").path("observed_from").asText()),
                LocalDate.parse(root.path("sources").path("cldr").path("observed_from").asText()),
                root.path("sources").path("iso3166-updates").path("commit").asText(),
                codes, former, changeLog);
    }

    private static List<Interval> intervals(JsonNode array) {
        List<Interval> intervals = new ArrayList<>();
        array.forEach(node -> intervals.add(new Interval(
                LocalDate.parse(node.path("first_seen").asText()),
                LocalDate.parse(node.path("last_seen").asText()),
                node.path("gone_by").isTextual() ? Optional.of(LocalDate.parse(node.path("gone_by").asText())) : Optional.empty(),
                strings(node.path("values")),
                names(node.path("names")))));
        return intervals;
    }

    private static List<Name> names(JsonNode array) {
        List<Name> names = new ArrayList<>();
        array.forEach(n -> names.add(new Name(LocalDate.parse(n.path("first_seen").asText()),
                LocalDate.parse(n.path("last_seen").asText()), n.path("name").asText(null))));
        return names;
    }

    private static List<ChangeLogEntry> entries(JsonNode array) {
        List<ChangeLogEntry> entries = new ArrayList<>();
        array.forEach(node -> entries.add(new ChangeLogEntry(
                node.path("date").isTextual() ? Optional.of(LocalDate.parse(node.path("date").asText())) : Optional.empty(),
                node.path("text").asText())));
        return entries;
    }

    private static Map<String, String> strings(JsonNode object) {
        Map<String, String> values = new LinkedHashMap<>();
        object.properties().forEach(e -> values.put(e.getKey(), e.getValue().asText()));
        return values;
    }
}
