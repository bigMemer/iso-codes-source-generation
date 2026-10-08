package isocodes.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A complete, validated dataset with every standard in its newest schema version. This is all an emitter sees.
 *
 * @param sourceName    human-readable name of the source, e.g. {@code Debian iso-codes}
 * @param sourceVersion version of the source data, e.g. the iso-codes release {@code 4.20.1}
 * @param sourceLicense SPDX licence identifier of the source data, which generated code inherits
 */
public record IsoCodesDataset(
        String sourceName,
        String sourceVersion,
        String sourceLicense,
        Table<Country.V2> countries,
        Table<Subdivision.V1> subdivisions,
        Table<FormerCountry.V2> formerCountries,
        Table<Currency.V1> currencies,
        Table<Script.V1> scripts,
        Table<LanguagePart2.V1> languagesPart2,
        Table<Language.V1> languages,
        Table<LanguageFamily.V1> languageFamilies) {

    /**
     * Upcasts every entry to its standard's newest schema version and validates the result.
     *
     * @throws InvalidDatasetException if any entry breaks its standard's rules
     */
    public static IsoCodesDataset fromSource(SourceData source) {
        IsoCodesDataset dataset = new IsoCodesDataset(
                source.sourceName(),
                source.sourceVersion(),
                source.sourceLicense(),
                upcast(Country.DEFINITION, source.countries(), Country::toLatest),
                upcast(Subdivision.DEFINITION, source.subdivisions(), Subdivision::toLatest),
                upcast(FormerCountry.DEFINITION, source.formerCountries(), FormerCountry::toLatest),
                upcast(Currency.DEFINITION, source.currencies(), Currency::toLatest),
                upcast(Script.DEFINITION, source.scripts(), Script::toLatest),
                upcast(LanguagePart2.DEFINITION, source.languagesPart2(), LanguagePart2::toLatest),
                upcast(Language.DEFINITION, source.languages(), Language::toLatest),
                upcast(LanguageFamily.DEFINITION, source.languageFamilies(), LanguageFamily::toLatest));
        List<String> problems = new ArrayList<>();
        for (Table<?> table : dataset.tables()) {
            Validator.check(table, problems);
        }
        if (!problems.isEmpty()) {
            throw new InvalidDatasetException(source.sourceName() + " " + source.sourceVersion(), problems);
        }
        return dataset;
    }

    private static <A, L> Table<L> upcast(StandardDef<L> standard, List<? extends A> rows, Function<A, L> toLatest) {
        return new Table<>(standard, rows.stream().map(toLatest).toList());
    }

    /** Every standard, in a fixed order. */
    public List<Table<?>> tables() {
        return List.of(countries, subdivisions, formerCountries, currencies, scripts, languagesPart2, languages,
                languageFamilies);
    }
}
