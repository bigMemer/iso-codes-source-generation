package isocodes.source.aggregate;

import isocodes.model.Contribution;
import isocodes.model.Country;
import isocodes.model.FieldDef;
import isocodes.model.SourceData;
import isocodes.model.StandardDef;
import isocodes.model.Subdivision;
import isocodes.model.Withdrawal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/**
 * Combines a backbone source, an authority source and overrides.
 *
 * <p><b>Which codes exist</b> (docs/sources.md §3), for each code either source mentions:
 *
 * <table>
 *   <caption>Inclusion rules</caption>
 *   <tr><th>Backbone</th><th>Authority</th><th>Result</th></tr>
 *   <tr><td>lists it</td><td>lists it</td><td>included</td></tr>
 *   <tr><td>lists it</td><td>removed it</td><td>withdrawn: the authority is trusted to be right</td></tr>
 *   <tr><td>lists it</td><td>never had it</td><td>included, unconfirmed</td></tr>
 *   <tr><td>represents it elsewhere</td><td>lists it</td><td>included</td></tr>
 *   <tr><td>withdrew it</td><td>lists it</td><td>withdrawn: the backbone is usually first to change</td></tr>
 *   <tr><td>doesn't mention it</td><td>lists it</td><td>included</td></tr>
 * </table>
 *
 * <p>Overrides win over all of these.
 *
 * <p><b>Which value each field gets</b> (§4): an override if there is one. Then, for code fields other than the
 * primary key, the backbone before the authority. For every other field, the authority's entry if it has one, even
 * when that entry leaves an optional field out (the authority's silence is a statement); otherwise the backbone.
 * Values that fall back to the backbone, and disagreements on code fields, are flagged.
 */
public final class Aggregator {

    /**
     * @param data  the combined data, in the authority's order followed by any other codes sorted
     * @param flags everything a reviewer should see
     */
    public record Result(SourceData data, List<Flag> flags) {

        public boolean hasErrors() {
            return flags.stream().anyMatch(f -> f.kind().severity() == Flag.Severity.ERROR);
        }
    }

    private final Contribution backbone;
    private final Contribution authority;
    private final Overrides overrides;
    private final List<Flag> flags = new ArrayList<>();

    private Aggregator(Contribution backbone, Contribution authority, Overrides overrides) {
        this.backbone = Objects.requireNonNull(backbone);
        this.authority = Objects.requireNonNull(authority);
        this.overrides = Objects.requireNonNull(overrides);
    }

    public static Result aggregate(Contribution backbone, Contribution authority, Overrides overrides) {
        Aggregator aggregator = new Aggregator(backbone, authority, overrides);
        List<Country.V2> countries = aggregator.combine(Country.DEFINITION, List.of("alpha_3", "numeric"),
                Map.of("flag", Country::flagOf));
        List<Subdivision.V1> subdivisions = aggregator.combine(Subdivision.DEFINITION, List.of(), Map.of());
        SourceData data = new SourceData(List.of(backbone.source(), authority.source()), countries, subdivisions);
        return new Result(data, List.copyOf(aggregator.flags));
    }

    /**
     * @param backboneFirstFields code fields where the backbone takes precedence over the authority
     * @param derived             fields computed from the primary code rather than taken from any source
     */
    private <T> List<T> combine(StandardDef<T> standard, List<String> backboneFirstFields,
            Map<String, UnaryOperator<String>> derived) {
        String id = standard.id();
        Contribution.Standard b = backbone.standard(id);
        Contribution.Standard a = authority.standard(id);
        Map<String, Overrides.Override> o = overrides.forStandard(id);

        Set<String> codes = new LinkedHashSet<>(a.entries().keySet());
        Set<String> others = new TreeSet<>(b.entries().keySet());
        others.addAll(o.keySet());
        codes.addAll(others);

        List<T> rows = new ArrayList<>();
        for (String code : codes) {
            if (!included(id, code, b, a, o.get(code))) {
                continue;
            }
            Map<String, String> values = values(standard, code, b.entries().get(code), a.entries().get(code),
                    o.get(code), backboneFirstFields, derived);
            if (values != null) {
                rows.add(standard.build(values));
            }
        }
        return rows;
    }

    private boolean included(String id, String code, Contribution.Standard b, Contribution.Standard a,
            Overrides.Override override) {
        if (override != null && override.action() == Overrides.Action.EXCLUDE) {
            flags.add(new Flag(Flag.Kind.EXCLUDED_BY_OVERRIDE, id, code, override.reason()));
            return false;
        }
        boolean inBackbone = b.entries().containsKey(code);
        boolean inAuthority = a.entries().containsKey(code);
        Withdrawal backboneWithdrawal = b.withdrawn().get(code);
        boolean authorityRemoved = a.withdrawn().get(code) == Withdrawal.WITHDRAWN;

        Flag.Kind kind;
        boolean include;
        if (inBackbone && inAuthority) {
            kind = null;
            include = true;
        } else if (inBackbone) {
            kind = authorityRemoved ? Flag.Kind.WITHDRAWN_BY_AUTHORITY : Flag.Kind.UNCONFIRMED;
            include = !authorityRemoved;
        } else if (inAuthority) {
            if (backboneWithdrawal == Withdrawal.REPRESENTED_ELSEWHERE) {
                kind = Flag.Kind.REPRESENTED_ELSEWHERE;
                include = true;
            } else if (backboneWithdrawal == Withdrawal.WITHDRAWN) {
                kind = Flag.Kind.WITHDRAWN_BY_BACKBONE;
                include = false;
            } else {
                kind = Flag.Kind.ABSENT_FROM_BACKBONE;
                include = true;
            }
        } else {
            kind = null;
            include = false;
        }

        if (override != null && override.action() == Overrides.Action.INCLUDE) {
            flags.add(new Flag(Flag.Kind.INCLUDED_BY_OVERRIDE, id, code, override.reason()));
            return true;
        }
        if (kind != null) {
            flags.add(new Flag(kind, id, code, kind.description()));
        }
        return include;
    }

    /** Returns the field values for one included code, or null (with an error flag) if a required field is missing. */
    private <T> Map<String, String> values(StandardDef<T> standard, String code, Map<String, String> fromBackbone,
            Map<String, String> fromAuthority, Overrides.Override override, List<String> backboneFirstFields,
            Map<String, UnaryOperator<String>> derived) {
        String id = standard.id();
        Map<String, String> values = new LinkedHashMap<>();
        boolean missing = false;
        for (FieldDef<T> field : standard.fields()) {
            String f = field.id();
            String value = null;
            if (override != null && override.values().containsKey(f)) {
                value = override.values().get(f);
                flags.add(new Flag(Flag.Kind.VALUE_FROM_OVERRIDE, id, code, f + " = " + value));
            } else if (derived.containsKey(f)) {
                value = derived.get(f).apply(code);
            } else if (f.equals(standard.primaryKey())) {
                value = code;
            } else if (backboneFirstFields.contains(f)) {
                value = first(fromBackbone, fromAuthority, f);
                String b = fromBackbone == null ? null : fromBackbone.get(f);
                String a = fromAuthority == null ? null : fromAuthority.get(f);
                if (b != null && a != null && !b.equals(a)) {
                    flags.add(new Flag(Flag.Kind.VALUE_DISAGREEMENT, id, code,
                            f + ": " + backbone.source() + " says " + b + ", " + authority.source() + " says " + a));
                }
            } else if (fromAuthority != null) {
                value = fromAuthority.get(f);
            } else if (fromBackbone != null && fromBackbone.get(f) != null) {
                value = fromBackbone.get(f);
                flags.add(new Flag(Flag.Kind.VALUE_FROM_FALLBACK, id, code,
                        f + " = " + value + " (from " + backbone.source() + ")"));
            }
            if (value != null) {
                values.put(f, value);
            } else if (!field.optional()) {
                flags.add(new Flag(Flag.Kind.MISSING_REQUIRED, id, code, f));
                missing = true;
            }
        }
        return missing ? null : values;
    }

    private static String first(Map<String, String> preferred, Map<String, String> fallback, String field) {
        if (preferred != null && preferred.get(field) != null) {
            return preferred.get(field);
        }
        return fallback == null ? null : fallback.get(field);
    }
}
