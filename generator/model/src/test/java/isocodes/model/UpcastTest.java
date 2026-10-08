package isocodes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UpcastTest {

    @Test
    void countryV1DerivesFlagFromAlpha2() {
        Country.V1 v1 = new Country.V1("DE", "DEU", "Germany", "276", Optional.empty(), Optional.empty());
        assertEquals("🇩🇪", v1.toLatest().flag());
    }

    @Test
    void latestVersionUpcastsToItself() {
        Country.V2 v2 = new Country.V2("DE", "DEU", "🇩🇪", "Germany", "276", Optional.empty(), Optional.empty());
        assertSame(v2, v2.toLatest());
    }

    @Test
    void datasetMixesSchemaVersions() {
        IsoCodesDataset dataset = IsoCodesDataset.fromSource(TestData.countries(List.of(
                new Country.V1("DE", "DEU", "Germany", "276", Optional.empty(), Optional.empty()),
                new Country.V2("FR", "FRA", "🇫🇷", "France", "250", Optional.empty(), Optional.empty()))));
        assertEquals(List.of("🇩🇪", "🇫🇷"), dataset.countries().rows().stream().map(Country.V2::flag).toList());
    }
}
