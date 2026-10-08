package isocodes.model;

import java.util.List;

final class TestData {

    private TestData() {}

    static SourceData countries(List<? extends Country> countries) {
        return withCountriesAndFormer(countries, List.of());
    }

    static SourceData withCountriesAndFormer(List<? extends Country> countries, List<? extends FormerCountry> former) {
        return new SourceData("Test", "1.0", "CC0-1.0", countries, List.of(), former, List.of(), List.of(), List.of(),
                List.of(), List.of());
    }
}
