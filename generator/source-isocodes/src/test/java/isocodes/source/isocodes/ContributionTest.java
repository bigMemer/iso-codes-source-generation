package isocodes.source.isocodes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import isocodes.model.Contribution;
import isocodes.model.Withdrawal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContributionTest {

    private static UpstreamFiles release(String subdivisions, String formerCountries) {
        Map<String, String> files = Map.of(
                "iso_3166-1.json", """
                        {"3166-1": [{"alpha_2": "IN", "alpha_3": "IND", "name": "India", "numeric": "356"}]}""",
                "iso_3166-2.json", "{\"3166-2\": [" + subdivisions + "]}",
                "iso_3166-3.json", formerCountries);
        return name -> Optional.ofNullable(files.get(name));
    }

    @Test
    void codesEarlierReleasesHadAreWithdrawn() {
        UpstreamFiles old = release("""
                {"code": "IN-OR", "name": "Odisha", "type": "State"},
                {"code": "IN-KA", "name": "Karnataka", "type": "State"}""", "{\"3166-3\": []}");
        UpstreamFiles current = release("""
                {"code": "IN-OD", "name": "Odisha", "type": "State"},
                {"code": "IN-KA", "name": "Karnataka", "type": "State"}""", """
                {"3166-3": [{"alpha_2": "CS", "alpha_3": "SCG", "alpha_4": "CSXX", "name": "Serbia and Montenegro",
                             "withdrawal_date": "2006-09-26"}]}""");

        Contribution contribution = IsoCodesSource.contribution("2.0", v -> v.equals("1.0") ? old : current,
                List.of("1.0"));

        assertEquals(List.of("IN-OD", "IN-KA"), List.copyOf(contribution.standard("3166-2").entries().keySet()));
        assertEquals(Map.of("IN-OR", Withdrawal.WITHDRAWN), contribution.standard("3166-2").withdrawn());
        assertEquals(Map.of("CS", Withdrawal.WITHDRAWN), contribution.standard("3166-1").withdrawn());
        assertEquals("Debian iso-codes 2.0", contribution.source().toString());
    }
}
