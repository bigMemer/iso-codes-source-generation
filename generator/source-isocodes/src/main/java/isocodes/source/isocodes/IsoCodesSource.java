package isocodes.source.isocodes;

import isocodes.model.Contribution;
import isocodes.model.SourceData;
import isocodes.model.SourceInfo;
import isocodes.model.Withdrawal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads one iso-codes release into {@link SourceData}. */
public final class IsoCodesSource {

    private IsoCodesSource() {}

    /**
     * @param version the iso-codes release, e.g. {@code 4.20.1}
     * @param files   the release's data files
     * @throws UnknownShapeException if a file doesn't fit any known shape
     */
    public static SourceData read(String version, UpstreamFiles files) {
        return new SourceData(
                List.of(new SourceInfo("Debian iso-codes", version, "LGPL-2.1-or-later")),
                parse(IsoCodesShapes.COUNTRIES, files),
                parse(IsoCodesShapes.SUBDIVISIONS, files));
    }

    /** iso-codes releases from which the JSON data, and so this reader, applies. */
    public static final String OLDEST_SUPPORTED = "3.67";

    /**
     * Reads one release as a contribution to aggregation. Codes that earlier releases had and this one doesn't are
     * listed as withdrawn: iso-codes is trusted to be right, so its removals stand even when other sources still
     * list the code. Withdrawn ISO 3166-1 codes also come from this release's ISO 3166-3 data.
     *
     * @param version       the release, e.g. {@code 4.20.1}
     * @param filesFor      data files of any release, by version
     * @param earlier       earlier releases to compare against, e.g. every release since {@link #OLDEST_SUPPORTED}
     */
    public static Contribution contribution(String version, Function<String, UpstreamFiles> filesFor, List<String> earlier) {
        SourceData current = read(version, filesFor.apply(version));
        Set<String> currentCountries = codes(current.countries().stream().map(c -> c.toLatest().alpha2()));
        Set<String> currentSubdivisions = codes(current.subdivisions().stream().map(s -> s.toLatest().code()));

        Map<String, Withdrawal> countries = new TreeMap<>();
        Map<String, Withdrawal> subdivisions = new TreeMap<>();
        for (String release : earlier) {
            UpstreamFiles files = filesFor.apply(release);
            parse(IsoCodesShapes.COUNTRIES, files).stream().map(c -> c.toLatest().alpha2())
                    .filter(code -> !currentCountries.contains(code))
                    .forEach(code -> countries.put(code, Withdrawal.WITHDRAWN));
            parse(IsoCodesShapes.SUBDIVISIONS, files).stream().map(s -> s.toLatest().code())
                    .filter(code -> !currentSubdivisions.contains(code))
                    .forEach(code -> subdivisions.put(code, Withdrawal.WITHDRAWN));
        }
        filesFor.apply(version).fetch("iso_3166-3.json").ifPresent(json -> {
            Matcher m = Pattern.compile("\"alpha_2\"\\s*:\\s*\"([A-Z]{2})\"").matcher(json);
            while (m.find()) {
                if (!currentCountries.contains(m.group(1))) {
                    countries.put(m.group(1), Withdrawal.WITHDRAWN);
                }
            }
        });
        return Contribution.of(current, Map.of("3166-1", countries, "3166-2", subdivisions));
    }

    private static Set<String> codes(Stream<String> codes) {
        return codes.collect(Collectors.toSet());
    }

    private static <R> List<R> parse(StandardFile<R> file, UpstreamFiles files) {
        String json = files.fetch(file.fileName())
                .orElseThrow(() -> new IllegalStateException("Release has no data/" + file.fileName()));
        return file.parse(json);
    }
}
