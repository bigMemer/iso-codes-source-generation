package isocodes.source.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import isocodes.model.Contribution;
import isocodes.model.Country;
import isocodes.model.SourceInfo;
import isocodes.model.Subdivision;
import isocodes.model.Withdrawal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AggregatorTest {

    private static Map<String, String> sub(String code, String name, String type) {
        Map<String, String> fields = new LinkedHashMap<>(Map.of("code", code, "name", name));
        if (type != null) {
            fields.put("type", type);
        }
        return fields;
    }

    private static Contribution contribution(String name, Map<String, Map<String, String>> subdivisions,
            Map<String, Withdrawal> withdrawn) {
        return new Contribution(new SourceInfo(name, "1", "CC0-1.0"), Map.of(
                "3166-1", new Contribution.Standard(Map.of(), Map.of()),
                "3166-2", new Contribution.Standard(subdivisions, withdrawn)));
    }

    @SafeVarargs
    private static Map<String, Map<String, String>> entries(Map<String, String>... rows) {
        Map<String, Map<String, String>> map = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            map.put(row.get("code"), row);
        }
        return map;
    }

    private static Set<String> codes(Aggregator.Result result) {
        return result.data().subdivisions().stream().map(s -> s.toLatest().code()).collect(Collectors.toSet());
    }

    private static Set<Flag.Kind> kinds(Aggregator.Result result, String code) {
        return result.flags().stream().filter(f -> f.code().equals(code)).map(Flag::kind).collect(Collectors.toSet());
    }

    @Test
    void appliesEveryInclusionRule() {
        Contribution backbone = contribution("Backbone", entries(
                sub("AA-BOTH", "Both", null),
                sub("AA-NEW", "New", null),
                sub("AA-GONE", "Removed by authority", null)),
                Map.of("AA-ELSE", Withdrawal.REPRESENTED_ELSEWHERE, "AA-OLD", Withdrawal.WITHDRAWN));
        Contribution authority = contribution("Authority", entries(
                sub("AA-BOTH", "Both (ISO)", "Region"),
                sub("AA-ELSE", "Elsewhere", "Region"),
                sub("AA-OLD", "Withdrawn by backbone", "Region"),
                sub("AA-ONLY", "Authority only", "Region")),
                Map.of("AA-GONE", Withdrawal.WITHDRAWN));
        Overrides overrides = new Overrides(Map.of("3166-2", Map.of("AA-NEW",
                new Overrides.Override("AA-NEW", Overrides.Action.SET, Map.of("type", "Province"), "r", "e"))));

        Aggregator.Result result = Aggregator.aggregate(backbone, authority, overrides);

        assertEquals(Set.of("AA-BOTH", "AA-NEW", "AA-ELSE", "AA-ONLY"), codes(result));
        assertEquals(Set.of(), kinds(result, "AA-BOTH"));
        assertTrue(kinds(result, "AA-NEW").contains(Flag.Kind.UNCONFIRMED));
        assertEquals(Set.of(Flag.Kind.WITHDRAWN_BY_AUTHORITY), kinds(result, "AA-GONE"));
        assertEquals(Set.of(Flag.Kind.REPRESENTED_ELSEWHERE), kinds(result, "AA-ELSE"));
        assertEquals(Set.of(Flag.Kind.WITHDRAWN_BY_BACKBONE), kinds(result, "AA-OLD"));
        assertEquals(Set.of(Flag.Kind.ABSENT_FROM_BACKBONE), kinds(result, "AA-ONLY"));
        assertFalse(result.hasErrors());
    }

    @Test
    void authorityWinsOnValuesAndItsSilenceIsAStatement() {
        Map<String, String> fromBackbone = sub("AA-01", "Backbone name", null);
        fromBackbone.put("parent", "AA-XX");
        Contribution backbone = contribution("Backbone", entries(fromBackbone), Map.of());
        Contribution authority = contribution("Authority", entries(sub("AA-01", "ISO name", "Region")), Map.of());

        Subdivision.V1 entry = Aggregator.aggregate(backbone, authority, Overrides.NONE)
                .data().subdivisions().get(0).toLatest();

        assertEquals("ISO name", entry.name());
        assertEquals(Optional.empty(), entry.parent(), "the authority has the entry and no parent");
    }

    @Test
    void unconfirmedCodesFallBackToTheBackboneAndAreFlagged() {
        Contribution backbone = contribution("Backbone", entries(sub("AA-01", "Backbone name", null)), Map.of());
        Contribution authority = contribution("Authority", entries(), Map.of());
        Overrides overrides = new Overrides(Map.of("3166-2", Map.of("AA-01",
                new Overrides.Override("AA-01", Overrides.Action.SET, Map.of("type", "Region"), "r", "e"))));

        Aggregator.Result result = Aggregator.aggregate(backbone, authority, overrides);

        assertEquals("Backbone name", result.data().subdivisions().get(0).toLatest().name());
        assertTrue(kinds(result, "AA-01").contains(Flag.Kind.VALUE_FROM_FALLBACK));
        assertTrue(kinds(result, "AA-01").contains(Flag.Kind.VALUE_FROM_OVERRIDE));
    }

    @Test
    void missingRequiredFieldIsAnError() {
        Contribution backbone = contribution("Backbone", entries(sub("AA-01", "No type anywhere", null)), Map.of());
        Aggregator.Result result = Aggregator.aggregate(backbone, contribution("Authority", entries(), Map.of()),
                Overrides.NONE);

        assertTrue(result.hasErrors());
        assertEquals(List.of(), result.data().subdivisions());
    }

    @Test
    void overridesExcludeAndInclude() {
        Contribution backbone = contribution("Backbone", entries(sub("AA-01", "One", "Region")), Map.of());
        Contribution authority = contribution("Authority", entries(sub("AA-01", "One", "Region")),
                Map.of("AA-02", Withdrawal.WITHDRAWN));
        Overrides overrides = new Overrides(Map.of("3166-2", Map.of(
                "AA-01", new Overrides.Override("AA-01", Overrides.Action.EXCLUDE, Map.of(), "r", "e"),
                "AA-02", new Overrides.Override("AA-02", Overrides.Action.INCLUDE,
                        Map.of("name", "Two", "type", "Region"), "r", "e"))));

        assertEquals(Set.of("AA-02"), codes(Aggregator.aggregate(backbone, authority, overrides)));
    }

    @Test
    void codeFieldsPreferTheBackboneAndFlagDisagreement() {
        Contribution backbone = new Contribution(new SourceInfo("Backbone", "1", "CC0-1.0"), Map.of("3166-1",
                new Contribution.Standard(Map.of("DE", Map.of("alpha_2", "DE", "alpha_3", "DEU", "numeric", "276")),
                        Map.of())));
        Contribution authority = new Contribution(new SourceInfo("Authority", "1", "CC0-1.0"), Map.of("3166-1",
                new Contribution.Standard(Map.of("DE", Map.of("alpha_2", "DE", "alpha_3", "DEU", "numeric", "999",
                        "name", "Germany")), Map.of())));

        Aggregator.Result result = Aggregator.aggregate(backbone, authority, Overrides.NONE);
        Country.V2 germany = result.data().countries().get(0).toLatest();

        assertEquals("276", germany.numeric());
        assertEquals("🇩🇪", germany.flag(), "derived, not sourced");
        assertEquals(Set.of(Flag.Kind.VALUE_DISAGREEMENT), kinds(result, "DE"));
    }

    @Test
    void overridesNeedReasonAndEvidence() {
        assertThrows(IllegalArgumentException.class, () -> Overrides.parse("""
                {"3166-1": [{"code": "XK", "action": "exclude", "reason": "", "evidence": "x"}]}"""));
        Overrides parsed = Overrides.parse("""
                {"3166-1": [{"code": "XK", "action": "exclude", "reason": "Not ISO", "evidence": "https://x"}]}""");
        assertEquals(Overrides.Action.EXCLUDE, parsed.forStandard("3166-1").get("XK").action());
    }
}
