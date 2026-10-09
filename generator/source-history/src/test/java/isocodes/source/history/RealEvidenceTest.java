package isocodes.source.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import isocodes.model.DateRange;
import isocodes.model.Holding;
import isocodes.model.Lifecycle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Known histories, checked against the committed evidence (history/iso3166-evidence.json). */
class RealEvidenceTest {

    private static History history;

    @BeforeAll
    static void reason() throws Exception {
        Evidence evidence = Evidence.parse(Files.readString(Path.of(System.getProperty("evidenceFile"))));
        // Treat every code iso-codes currently lists as current.
        Map<String, Set<String>> current = Map.of(
                "3166-1", currentIn(evidence, "3166-1"),
                "3166-2", currentIn(evidence, "3166-2"));
        history = HistoryReasoner.reason(evidence, current);
    }

    private static Set<String> currentIn(Evidence evidence, String standard) {
        Set<String> codes = new java.util.HashSet<>();
        evidence.codes().get(standard).forEach((code, ev) -> {
            if (!ev.isoCodes().isEmpty() && ev.isoCodes().get(ev.isoCodes().size() - 1).goneBy().isEmpty()) {
                codes.add(code);
            }
        });
        return codes;
    }

    private static Lifecycle lifecycle(String standard, String code) {
        return history.lifecycles().get(standard).get(code);
    }

    private static LocalDate d(String date) {
        return LocalDate.parse(date);
    }

    @Test
    void bsNpWasWithdrawnAndReassignedToTheSameRegion() {
        List<Holding> h = lifecycle("3166-2", "BS-NP").holdings();
        assertEquals(2, h.size());
        assertEquals(h.get(0).holder(), h.get(1).holder(), "New Providence both times");
        assertEquals(Optional.of(d("2004-02-22")), h.get(0).assigned().latest());
        // The only Bahamas change-log entry before BS-NP disappeared (iso-codes dropped it on 2010-08-29).
        assertEquals(DateRange.exact(d("2010-06-30")), h.get(0).withdrawn().orElseThrow());
        assertEquals(DateRange.exact(d("2018-11-26")), h.get(1).assigned(), "from ISO's change log");
        assertTrue(h.get(1).withdrawn().isEmpty());
        assertEquals(d("2004-02-22"), lifecycle("3166-2", "BS-NP").recordedSince());
    }

    @Test
    void renamedCodesAreWithdrawnOnTheChangeLogDate() {
        assertEquals(DateRange.exact(d("2023-11-23")), lifecycle("3166-2", "IN-OR").withdrawn().orElseThrow());
        assertEquals(DateRange.exact(d("2021-11-25")), lifecycle("3166-2", "GT-AV").withdrawn().orElseThrow());
        assertTrue(history.withdrawn().get("3166-2").containsKey("IN-OR"));
    }

    @Test
    void codesAssignedBeforeOurHistoryHaveOnlyALatestDate() {
        Lifecycle usCa = lifecycle("3166-2", "US-CA");
        assertEquals(1, usCa.holdings().size());
        assertEquals(DateRange.noLaterThan(d("2004-02-22")), usCa.current().assigned());
        assertFalse(usCa.isWithdrawn());
    }

    @Test
    void genuinelyReassignedCodesHaveDifferentHolders() {
        List<Holding> h = lifecycle("3166-2", "FR-972").holdings();
        assertTrue(h.size() >= 2);
        assertFalse(h.get(0).holder().equals(h.get(h.size() - 1).holder()), "Guyane, then Martinique");
    }

    @Test
    void countryCodesCarryFormerHoldersFromIso31663() {
        List<Holding> cs = lifecycle("3166-1", "CS").holdings();
        assertEquals(List.of("CSHH", "CSXX"), cs.stream().map(Holding::holder).toList());
        assertEquals(DateRange.exact(d("1993-06-15")), cs.get(0).withdrawn().orElseThrow());
        assertEquals(DateRange.exact(d("2006-09-26")), cs.get(1).withdrawn().orElseThrow());

        List<Holding> ai = lifecycle("3166-1", "AI").holdings();
        assertEquals("AIDJ", ai.get(0).holder(), "French Afars and Issas");
        assertEquals(DateRange.parse("1977"), ai.get(0).withdrawn().orElseThrow());
        assertTrue(ai.get(ai.size() - 1).withdrawn().isEmpty(), "Anguilla holds it now");
        assertFalse(history.withdrawn().get("3166-1").containsKey("AI"), "the active holder is the entry");
    }

    @Test
    void newCountriesAreAssignedOnTheChangeLogDate() {
        assertEquals(DateRange.exact(d("2011-08-09")), lifecycle("3166-1", "SS").current().assigned());
        assertEquals(d("1974-01-01"), lifecycle("3166-1", "SS").recordedSince());
    }
}
