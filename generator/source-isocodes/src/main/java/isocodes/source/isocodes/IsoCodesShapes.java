package isocodes.source.isocodes;

import static isocodes.source.isocodes.Shape.optionalText;
import static isocodes.source.isocodes.Shape.text;

import isocodes.model.Country;
import isocodes.model.Currency;
import isocodes.model.FormerCountry;
import isocodes.model.Language;
import isocodes.model.LanguageFamily;
import isocodes.model.LanguagePart2;
import isocodes.model.Script;
import isocodes.model.Subdivision;
import java.util.List;
import java.util.Set;

/** Every JSON shape iso-codes has published since 3.67, per standard. */
final class IsoCodesShapes {

    private IsoCodesShapes() {}

    static final StandardFile<Country> COUNTRIES = new StandardFile<>("3166-1", List.of(
            new Shape<>(1,
                    Set.of("alpha_2", "alpha_3", "name", "numeric"),
                    Set.of("official_name", "common_name"),
                    row -> new Country.V1(text(row, "alpha_2"), text(row, "alpha_3"), text(row, "name"),
                            text(row, "numeric"), optionalText(row, "official_name"), optionalText(row, "common_name"))),
            // iso-codes 4.8.0 added flags.
            new Shape<>(2,
                    Set.of("alpha_2", "alpha_3", "flag", "name", "numeric"),
                    Set.of("official_name", "common_name"),
                    row -> new Country.V2(text(row, "alpha_2"), text(row, "alpha_3"), text(row, "flag"),
                            text(row, "name"), text(row, "numeric"), optionalText(row, "official_name"),
                            optionalText(row, "common_name")))));

    static final StandardFile<Subdivision> SUBDIVISIONS = new StandardFile<>("3166-2", List.of(
            new Shape<>(1,
                    Set.of("code", "name", "type"),
                    Set.of("parent"),
                    row -> new Subdivision.V1(text(row, "code"), text(row, "name"), optionalText(row, "parent"),
                            text(row, "type")))));

    static final StandardFile<FormerCountry> FORMER_COUNTRIES = new StandardFile<>("3166-3", List.of(
            new Shape<>(1,
                    Set.of("alpha_3", "alpha_4", "name", "withdrawal_date"),
                    Set.of("numeric", "comment"),
                    row -> new FormerCountry.V1(text(row, "alpha_3"), text(row, "alpha_4"), text(row, "name"),
                            optionalText(row, "numeric"), optionalText(row, "comment"),
                            text(row, "withdrawal_date"))),
            // iso-codes 3.76 added the former alpha-2 code.
            new Shape<>(2,
                    Set.of("alpha_2", "alpha_3", "alpha_4", "name", "withdrawal_date"),
                    Set.of("numeric", "comment"),
                    row -> new FormerCountry.V2(text(row, "alpha_2"), text(row, "alpha_3"), text(row, "alpha_4"),
                            text(row, "name"), optionalText(row, "numeric"), optionalText(row, "comment"),
                            text(row, "withdrawal_date")))));

    static final StandardFile<Currency> CURRENCIES = new StandardFile<>("4217", List.of(
            new Shape<>(1,
                    Set.of("alpha_3", "name", "numeric"),
                    Set.of(),
                    row -> new Currency.V1(text(row, "alpha_3"), text(row, "name"), text(row, "numeric")))));

    static final StandardFile<Script> SCRIPTS = new StandardFile<>("15924", List.of(
            new Shape<>(1,
                    Set.of("alpha_4", "name", "numeric"),
                    Set.of(),
                    row -> new Script.V1(text(row, "alpha_4"), text(row, "name"), text(row, "numeric")))));

    static final StandardFile<LanguagePart2> LANGUAGES_PART_2 = new StandardFile<>("639-2", List.of(
            new Shape<>(1,
                    Set.of("alpha_3", "name"),
                    Set.of("alpha_2", "bibliographic", "common_name"),
                    row -> new LanguagePart2.V1(text(row, "alpha_3"), text(row, "name"), optionalText(row, "alpha_2"),
                            optionalText(row, "bibliographic"), optionalText(row, "common_name")))));

    static final StandardFile<Language> LANGUAGES = new StandardFile<>("639-3", List.of(
            new Shape<>(1,
                    Set.of("alpha_3", "name", "scope", "type"),
                    Set.of("alpha_2", "common_name", "inverted_name", "bibliographic"),
                    row -> new Language.V1(text(row, "alpha_3"), text(row, "name"), text(row, "scope"),
                            text(row, "type"), optionalText(row, "alpha_2"), optionalText(row, "common_name"),
                            optionalText(row, "inverted_name"), optionalText(row, "bibliographic")))));

    static final StandardFile<LanguageFamily> LANGUAGE_FAMILIES = new StandardFile<>("639-5", List.of(
            new Shape<>(1,
                    Set.of("alpha_3", "name"),
                    Set.of(),
                    row -> new LanguageFamily.V1(text(row, "alpha_3"), text(row, "name")))));
}
