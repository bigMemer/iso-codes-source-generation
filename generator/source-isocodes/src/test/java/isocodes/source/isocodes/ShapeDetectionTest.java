package isocodes.source.isocodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import isocodes.model.Country;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShapeDetectionTest {

    private static List<Country> parse(String entries) {
        return IsoCodesShapes.COUNTRIES.parse("{\"3166-1\": [" + entries + "]}");
    }

    @Test
    void entriesWithoutFlagAreSchema1() {
        List<Country> countries = parse("""
                {"alpha_2": "DE", "alpha_3": "DEU", "name": "Germany", "numeric": "276",
                 "official_name": "Federal Republic of Germany"}""");
        Country.V1 germany = assertInstanceOf(Country.V1.class, countries.get(0));
        assertEquals("Federal Republic of Germany", germany.officialName().orElseThrow());
    }

    @Test
    void entriesWithFlagAreSchema2() {
        List<Country> countries = parse("""
                {"alpha_2": "DE", "alpha_3": "DEU", "flag": "🇩🇪", "name": "Germany", "numeric": "276"}""");
        assertInstanceOf(Country.V2.class, countries.get(0));
    }

    @Test
    void unknownFieldFailsInsteadOfBeingDropped() {
        UnknownShapeException e = assertThrows(UnknownShapeException.class, () -> parse("""
                {"alpha_2": "DE", "alpha_3": "DEU", "flag": "🇩🇪", "name": "Germany", "numeric": "276",
                 "capital": "Berlin"}"""));
        assertTrue(e.getMessage().contains("unknown fields [capital]"), e.getMessage());
    }

    @Test
    void missingRequiredFieldFails() {
        UnknownShapeException e = assertThrows(UnknownShapeException.class, () -> parse("""
                {"alpha_2": "DE", "alpha_3": "DEU", "flag": "🇩🇪", "name": "Germany"}"""));
        assertTrue(e.getMessage().contains("lack required fields [numeric]"), e.getMessage());
    }

    @Test
    void nonStringValueFails() {
        UnknownShapeException e = assertThrows(UnknownShapeException.class, () -> parse("""
                {"alpha_2": "DE", "alpha_3": "DEU", "flag": "🇩🇪", "name": "Germany", "numeric": 276}"""));
        assertTrue(e.getMessage().contains("non-string values in [numeric]"), e.getMessage());
    }

    @Test
    void wrongTopLevelKeyFails() {
        assertThrows(UnknownShapeException.class, () -> IsoCodesShapes.COUNTRIES.parse("{\"countries\": []}"));
    }
}
