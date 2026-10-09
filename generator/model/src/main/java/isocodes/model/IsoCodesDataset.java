package isocodes.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A complete, validated dataset with every standard in its newest schema version. This is all an emitter sees.
 *
 * @param sources every source the data came from; generated code inherits all their licences
 */
public record IsoCodesDataset(
        List<SourceInfo> sources,
        Table<Country.V2> countries,
        Table<Subdivision.V1> subdivisions) {

    /**
     * Upcasts every entry to its standard's newest schema version and validates the result.
     *
     * @throws InvalidDatasetException if any entry breaks its standard's rules
     */
    public static IsoCodesDataset fromSource(SourceData source) {
        IsoCodesDataset dataset = new IsoCodesDataset(
                source.sources(),
                upcast(Country.DEFINITION, source.countries(), Country::toLatest),
                upcast(Subdivision.DEFINITION, source.subdivisions(), Subdivision::toLatest));
        List<String> problems = new ArrayList<>();
        for (Table<?> table : dataset.tables()) {
            Validator.check(table, problems);
        }
        if (!problems.isEmpty()) {
            throw new InvalidDatasetException(source.sources().toString(), problems);
        }
        return dataset;
    }

    private static <A, L> Table<L> upcast(StandardDef<L> standard, List<? extends A> rows, Function<A, L> toLatest) {
        return new Table<>(standard, rows.stream().map(toLatest).toList());
    }

    public IsoCodesDataset {
        sources = List.copyOf(sources);
    }

    /** Distinct SPDX licence identifiers of all sources, in source order. */
    public List<String> sourceLicenses() {
        return sources.stream().map(SourceInfo::license).distinct().toList();
    }

    /** Every standard, in a fixed order. */
    public List<Table<?>> tables() {
        return List.of(countries, subdivisions);
    }
}
