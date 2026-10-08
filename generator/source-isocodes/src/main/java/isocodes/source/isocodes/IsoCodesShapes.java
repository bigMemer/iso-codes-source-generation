package isocodes.source.isocodes;

import static isocodes.source.isocodes.Shape.optionalText;
import static isocodes.source.isocodes.Shape.text;

import isocodes.model.Country;
import isocodes.model.Subdivision;
import java.util.List;
import java.util.Set;

/** Every JSON shape iso-codes has published since 3.67, for the standards we generate. */
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
}
