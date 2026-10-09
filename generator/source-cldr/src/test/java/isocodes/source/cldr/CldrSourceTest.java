package isocodes.source.cldr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import isocodes.model.Contribution;
import isocodes.model.Withdrawal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CldrSourceTest {

    private static final Map<String, String> FILES = Map.of(
            "common/validity/region.xml", """
                    <supplementalData><idValidity>
                      <id type='region' idStatus='regular'>  <!-- 4 items -->
                        DE US XK 001
                      </id>
                      <id type='region' idStatus='deprecated'>DD YU</id>
                    </idValidity></supplementalData>""",
            "common/validity/subdivision.xml", """
                    <!DOCTYPE supplementalData SYSTEM "../../common/dtd/ldmlSupplemental.dtd">
                    <supplementalData><idValidity>
                      <id type='subdivision' idStatus='regular'>  <!-- 5 items -->
                        ad02~4 gbeng gbbnh usca
                      </id>
                      <id type='subdivision' idStatus='deprecated'>uspr inor</id>
                    </idValidity></supplementalData>""",
            "common/supplemental/supplementalMetadata.xml", """
                    <supplementalData><metadata><alias>
                      <territoryAlias type="DD" replacement="DE" reason="deprecated"/>
                      <subdivisionAlias type="uspr" replacement="PR" reason="overlong"/>
                      <subdivisionAlias type="inor" replacement="in?" reason="deprecated"/>
                    </alias></metadata></supplementalData>""",
            "common/supplemental/supplementalData.xml", """
                    <supplementalData><codeMappings>
                      <territoryCodes type="DE" numeric="276" alpha3="DEU"/>
                      <territoryCodes type="XK" numeric="983" alpha3="XKK"/>
                    </codeMappings></supplementalData>""",
            "common/main/en.xml", """
                    <ldml><localeDisplayNames><territories>
                      <territory type="DE">Germany</territory>
                      <territory type="US">United States</territory>
                      <territory type="US" alt="short">US</territory>
                    </territories></localeDisplayNames></ldml>""",
            "common/subdivisions/en.xml", """
                    <ldml><localeDisplayNames><subdivisions>
                      <subdivision type="usca">California</subdivision>
                      <subdivision type="gbbnh">Brighton and Hove</subdivision>
                    </subdivisions></localeDisplayNames></ldml>""",
            "common/supplemental/subdivisions.xml", """
                    <supplementalData><subdivisionContainment>
                      <subgroup type="GB" contains="gbeng"/>
                      <subgroup type="gbeng" contains="gbbnh"/>
                      <subgroup type="US" contains="usca"/>
                    </subdivisionContainment></supplementalData>""");

    private static final Contribution CLDR = CldrSource.read("48.2", FILES::get);

    @Test
    void readsCountriesWithCodesAndEnglishNames() {
        Contribution.Standard countries = CLDR.standard("3166-1");
        assertEquals(List.of("DE", "US", "XK"), List.copyOf(countries.entries().keySet()));
        assertEquals(Map.of("alpha_2", "DE", "alpha_3", "DEU", "numeric", "276", "name", "Germany"),
                countries.entries().get("DE"));
        assertEquals(Map.of("alpha_2", "US", "name", "United States"), countries.entries().get("US"));
        assertEquals(Map.of("DD", Withdrawal.WITHDRAWN, "YU", Withdrawal.WITHDRAWN), countries.withdrawn());
    }

    @Test
    void expandsRangesAndConvertsSubdivisionCodes() {
        Contribution.Standard subdivisions = CLDR.standard("3166-2");
        assertEquals(List.of("AD-02", "AD-03", "AD-04", "GB-BNH", "GB-ENG", "US-CA"),
                List.copyOf(subdivisions.entries().keySet()));
        assertEquals(Map.of("code", "GB-BNH", "name", "Brighton and Hove", "parent", "GB-ENG"),
                subdivisions.entries().get("GB-BNH"));
        assertFalse(subdivisions.entries().get("US-CA").containsKey("parent"), "countries aren't parents");
    }

    @Test
    void distinguishesWithdrawalFromRepresentationChoice() {
        assertEquals(Map.of("US-PR", Withdrawal.REPRESENTED_ELSEWHERE, "IN-OR", Withdrawal.WITHDRAWN),
                CLDR.standard("3166-2").withdrawn());
    }

    @Test
    void identifiesTheRelease() {
        assertEquals("Unicode CLDR 48.2", CLDR.source().toString());
        assertEquals("Unicode-3.0", CLDR.source().license());
    }
}
