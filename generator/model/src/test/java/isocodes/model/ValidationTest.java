package isocodes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ValidationTest {

    private static Country.V2 country(String alpha2, String alpha3, String numeric) {
        return new Country.V2(alpha2, alpha3, "🇩🇪", "Somewhere", numeric, Optional.empty(), Optional.empty());
    }

    private static List<String> problems(Country.V2... countries) {
        return assertThrows(InvalidDatasetException.class,
                () -> IsoCodesDataset.fromSource(TestData.countries(List.of(countries)))).problems();
    }

    @Test
    void rejectsDuplicateUniqueField() {
        assertEquals(List.of("ISO 3166-1 entry 1, alpha_3: \"DEU\" duplicates entry 0"),
                problems(country("DE", "DEU", "276"), country("DD", "DEU", "278")));
    }

    @Test
    void rejectsValueNotMatchingPattern() {
        assertEquals(List.of("ISO 3166-1 entry 0, numeric: \"27\" does not match [0-9]{3}"),
                problems(country("DE", "DEU", "27")));
    }

    @Test
    void rejectsBlankRequiredValue() {
        Country.V2 blankName = new Country.V2("DE", "DEU", "🇩🇪", " ", "276", Optional.empty(), Optional.empty());
        assertEquals(List.of("ISO 3166-1 entry 0, name: blank"), problems(blankName));
    }

    @Test
    void allowsRepeatedValuesInNonUniqueFields() {
        Country.V2 a = new Country.V2("DE", "DEU", "🇩🇪", "Same", "276", Optional.empty(), Optional.empty());
        Country.V2 b = new Country.V2("FR", "FRA", "🇫🇷", "Same", "250", Optional.empty(), Optional.empty());
        assertEquals(2, IsoCodesDataset.fromSource(TestData.countries(List.of(a, b))).countries().rows().size());
    }
}
