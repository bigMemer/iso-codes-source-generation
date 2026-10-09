package isocodes.source.history;

import isocodes.model.DateRange;
import isocodes.model.Holding;
import isocodes.model.Lifecycle;
import isocodes.source.history.Evidence.ChangeLogEntry;
import isocodes.source.history.Evidence.CodeEvidence;
import isocodes.source.history.Evidence.Interval;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns history evidence into lifecycles (docs/sources.md §9).
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>iso-codes is the authority on <em>whether</em> a code was held: each continuous period in iso-codes is a
 *       holding. Two periods with the same (normalised) name are the same holder; a different name is a different
 *       holder, which is flagged for review. Gaps of up to {@value #GLITCH_DAYS} days with the same holder are
 *       treated as data glitches and closed.
 *   <li>Sources lag ISO, so a source showing a change only gives the <em>latest</em> possible date. The earliest
 *       possible date stays unknown unless ISO's change log names the code in a matching entry, which gives the exact
 *       date. CLDR tightens the latest date when it showed the change first.
 *   <li>For ISO 3166-1, ISO 3166-3 supplies earlier holders with withdrawal dates, back to 1974.
 * </ul>
 */
public final class HistoryReasoner {

    static final int GLITCH_DAYS = 31;

    /** ISO 3166-3 records every ISO 3166-1 withdrawal since the standard began. */
    static final LocalDate COUNTRIES_RECORDED_SINCE = LocalDate.of(1974, 1, 1);

    private final Evidence evidence;
    private final List<HistoryNote> notes = new ArrayList<>();

    /** One name one code had for a while, for spotting names that moved between codes. */
    private record NameUse(String code, String name, LocalDate firstSeen, LocalDate lastSeen) {}

    /** Per country, every name its subdivision codes have had in iso-codes. */
    private final Map<String, List<NameUse>> namesByCountry = new LinkedHashMap<>();

    private HistoryReasoner(Evidence evidence) {
        this.evidence = evidence;
        evidence.codes().get("3166-2").forEach((code, ev) -> {
            for (Interval interval : ev.isoCodes()) {
                for (Evidence.Name name : interval.names()) {
                    namesByCountry.computeIfAbsent(code.substring(0, 2), k -> new ArrayList<>())
                            .add(new NameUse(code, name.name(), name.firstSeen(), name.lastSeen()));
                }
            }
        });
    }

    /**
     * Whether a code's change from one name to a dissimilar one was a handover to a different holder, rather than a
     * change in how the same place is written. In a renumbering, names move between codes at the moment of the switch:
     * the new name was on another code until then (MA-02's L'Oriental had been MA-04), or the old name lands on another
     * code then (IR-07's Tehrān became IR-23). A new spelling, or a switch to the local-language name, doesn't move
     * between codes, and neither does a name that an unrelated, since-retired code once had (KZ-KUS).
     */
    private boolean moved(String code, Evidence.Name from, Evidence.Name to) {
        for (NameUse use : namesByCountry.getOrDefault(code.substring(0, 2), List.of())) {
            if (use.code().equals(code)) {
                continue;
            }
            // The new name was on the other code right up to the switch...
            boolean newNameLeftThere = use.firstSeen().isBefore(to.firstSeen()) && !use.lastSeen().isBefore(from.lastSeen());
            if (newNameLeftThere && sameHolder(use.name(), to.name())) {
                return true;
            }
            // ...or the old name arrived on the other code at the switch.
            boolean oldNameArrivedThere = !use.firstSeen().isBefore(from.lastSeen()) && !use.firstSeen().isAfter(to.firstSeen());
            if (oldNameArrivedThere && sameHolder(use.name(), from.name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param evidence     the evidence
     * @param currentCodes per standard id, the codes aggregation found current
     */
    public static History reason(Evidence evidence, Map<String, Set<String>> currentCodes) {
        HistoryReasoner reasoner = new HistoryReasoner(evidence);
        Map<String, Map<String, Lifecycle>> lifecycles = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, String>>> withdrawn = new LinkedHashMap<>();
        lifecycles.put("3166-2", new LinkedHashMap<>());
        withdrawn.put("3166-2", new LinkedHashMap<>());
        lifecycles.put("3166-1", new LinkedHashMap<>());
        withdrawn.put("3166-1", new LinkedHashMap<>());

        Set<String> currentSubdivisions = currentCodes.getOrDefault("3166-2", Set.of());
        evidence.codes().get("3166-2").forEach((code, ev) ->
                reasoner.subdivision(code, ev, currentSubdivisions.contains(code), lifecycles.get("3166-2"),
                        withdrawn.get("3166-2")));

        Set<String> currentCountries = currentCodes.getOrDefault("3166-1", Set.of());
        Map<String, List<Map<String, String>>> formerByAlpha2 = new LinkedHashMap<>();
        for (Map<String, String> former : evidence.formerCountries()) {
            String alpha2 = former.getOrDefault("alpha_2", former.getOrDefault("alpha_4", "").substring(0, 2));
            formerByAlpha2.computeIfAbsent(alpha2, k -> new ArrayList<>()).add(former);
        }
        Set<String> countryCodes = new java.util.TreeSet<>(evidence.codes().get("3166-1").keySet());
        countryCodes.addAll(formerByAlpha2.keySet());
        for (String code : countryCodes) {
            CodeEvidence ev = evidence.codes().get("3166-1").getOrDefault(code, new CodeEvidence(List.of(), List.of(), List.of()));
            reasoner.country(code, ev, formerByAlpha2.getOrDefault(code, List.of()), currentCountries.contains(code),
                    lifecycles.get("3166-1"), withdrawn.get("3166-1"));
        }
        return new History(lifecycles, withdrawn, List.copyOf(reasoner.notes));
    }

    // ---- ISO 3166-2 --------------------------------------------------------------------------------------------

    private void subdivision(String code, CodeEvidence ev, boolean current, Map<String, Lifecycle> lifecycles,
            Map<String, Map<String, String>> withdrawn) {
        if (ev.isoCodes().isEmpty()) {
            if (!current) {
                note(HistoryNote.Kind.UNCONFIRMED_PAST_CODE, "3166-2", code,
                        "listed only by CLDR, " + span(ev.cldr()) + "; not an entry");
            }
            return;
        }
        List<Interval> periods = splitHandovers("3166-2", code, closeGlitches("3166-2", code, ev.isoCodes()));
        List<Holding> holdings = new ArrayList<>();
        Optional<LocalDate> previousGone = Optional.empty();
        Optional<LocalDate> previousStart = Optional.empty();
        for (int i = 0; i < periods.size(); i++) {
            Interval period = periods.get(i);
            boolean last = i == periods.size() - 1;
            boolean handedOver = i > 0 && handovers.contains(period);
            String holder = holder(period.values().get("name"));
            if (!holdings.isEmpty()) {
                String previousName = periods.get(i - 1).values().get("name");
                if (sameHolder(previousName, period.values().get("name"))) {
                    holder = holdings.get(holdings.size() - 1).holder();
                } else if (!handedOver) {
                    note(HistoryNote.Kind.HOLDER_CHANGED, "3166-2", code, "\"" + previousName + "\" then \""
                            + period.values().get("name") + "\" after a gap: treated as different holders");
                }
            }
            DateRange assigned = handedOver
                    ? holdings.get(holdings.size() - 1).withdrawn().orElseThrow()
                    : assigned(code, period, ev, previousGone, previousStart);
            Optional<DateRange> gone = Optional.empty();
            if (period.goneBy().isPresent() && !(last && current)) {
                gone = Optional.of(i + 1 < periods.size() && handovers.contains(periods.get(i + 1))
                        ? handover(code, period, periods.get(i + 1), assigned)
                        : withdrawn(code, period, ev, assigned));
            }
            if (last && current && period.goneBy().isPresent()) {
                note(HistoryNote.Kind.INCONSISTENT, "3166-2", code,
                        "current, but iso-codes stopped listing it on " + period.goneBy().get());
            }
            if (last && !current && period.goneBy().isEmpty()) {
                note(HistoryNote.Kind.INCONSISTENT, "3166-2", code,
                        "not current, but iso-codes still lists it; treated as withdrawn by its last snapshot");
                gone = Optional.of(DateRange.noLaterThan(period.lastSeen()));
            }
            holdings.add(new Holding(holder, assigned, gone));
            previousGone = period.goneBy();
            previousStart = Optional.of(period.firstSeen());
        }
        lifecycles.put(code, new Lifecycle(holdings, evidence.isoCodesObservedFrom()));
        if (!current) {
            Map<String, String> values = new LinkedHashMap<>(periods.get(periods.size() - 1).values());
            values.put("code", code);
            withdrawn.put(code, values);
        }
    }

    // ---- ISO 3166-1 --------------------------------------------------------------------------------------------

    private void country(String code, CodeEvidence ev, List<Map<String, String>> formers, boolean current,
            Map<String, Lifecycle> lifecycles, Map<String, Map<String, String>> withdrawn) {
        if (ev.isoCodes().isEmpty() && formers.isEmpty()) {
            return; // CLDR-only regions such as XK: never ISO codes
        }
        List<Map<String, String>> unmatched = new ArrayList<>(formers);
        unmatched.sort(Comparator.comparing(f -> DateRange.parse(f.get("withdrawal_date")).latest().orElseThrow()));
        List<Holding> holdings = new ArrayList<>();
        Map<String, String> lastValues = null;

        List<Interval> periods = closeGlitches("3166-1", code, ev.isoCodes());
        Optional<LocalDate> previousGone = Optional.empty();
        for (int i = 0; i < periods.size(); i++) {
            Interval period = periods.get(i);
            boolean open = i == periods.size() - 1 && (current || period.goneBy().isEmpty());
            DateRange assigned = assignedCountry(code, period, ev, previousGone);
            if (open) {
                // Former holders withdrawn before this one was assigned come first.
                addFormers(unmatched, holdings, period.firstSeen());
                holdings.add(new Holding(holder(period.values().get("name")), assigned, Optional.empty()));
            } else {
                LocalDate gone = period.goneBy().orElse(period.lastSeen());
                Optional<Map<String, String>> former = unmatched.stream()
                        .filter(f -> !DateRange.parse(f.get("withdrawal_date")).latest().orElseThrow().isAfter(gone))
                        .reduce((a, b) -> b);
                former.ifPresent(f -> {
                    addFormers(unmatched, holdings, DateRange.parse(f.get("withdrawal_date")).earliest().orElseThrow());
                    unmatched.remove(f);
                });
                String holder = former.map(f -> f.get("alpha_4")).orElse(holder(period.values().get("name")));
                DateRange withdrawnRange = former.map(f -> DateRange.parse(f.get("withdrawal_date")))
                        .orElse(DateRange.noLaterThan(gone));
                holdings.add(new Holding(holder, assigned, Optional.of(withdrawnRange)));
            }
            lastValues = period.values();
            previousGone = period.goneBy();
        }
        addFormers(unmatched, holdings, LocalDate.MAX);
        holdings.sort(Comparator.comparing(h -> h.withdrawn().flatMap(DateRange::latest).orElse(LocalDate.MAX)));
        if (holdings.isEmpty()) {
            return;
        }
        lifecycles.put(code, new Lifecycle(holdings, COUNTRIES_RECORDED_SINCE));
        if (!current) {
            Map<String, String> values = new LinkedHashMap<>();
            Holding last = holdings.get(holdings.size() - 1);
            Optional<Map<String, String>> former = formers.stream().filter(f -> f.get("alpha_4").equals(last.holder())).findFirst();
            if (lastValues != null && !former.isPresent()) {
                values.putAll(lastValues);
            } else if (former.isPresent()) {
                Map<String, String> f = former.get();
                if (lastValues != null) {
                    values.putAll(lastValues);
                }
                putIfPresent(values, "alpha_3", f.get("alpha_3"));
                putIfPresent(values, "numeric", f.get("numeric"));
                values.putIfAbsent("name", f.get("name"));
            }
            values.put("alpha_2", code);
            withdrawn.put(code, values);
        }
    }

    private static void putIfPresent(Map<String, String> values, String key, String value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    /** Adds former holders withdrawn before {@code before} as holdings with unknown assignment. */
    private static void addFormers(List<Map<String, String>> unmatched, List<Holding> holdings, LocalDate before) {
        for (var it = unmatched.iterator(); it.hasNext(); ) {
            Map<String, String> f = it.next();
            DateRange range = DateRange.parse(f.get("withdrawal_date"));
            if (range.latest().orElseThrow().isBefore(before)) {
                holdings.add(new Holding(f.get("alpha_4"), DateRange.UNKNOWN, Optional.of(range)));
                it.remove();
            }
        }
    }

    private DateRange assignedCountry(String code, Interval period, CodeEvidence ev, Optional<LocalDate> previousGone) {
        LocalDate latest = earliestCldrStart(period, ev.cldr(), previousGone).orElse(period.firstSeen());
        Pattern assign = Pattern.compile("(?i)\\bassign");
        Optional<LocalDate> exact = evidence.countryChangeLog().getOrDefault(code, List.of()).stream()
                .filter(e -> e.date().isPresent() && assign.matcher(e.text()).find())
                .map(e -> e.date().get())
                .filter(d -> !d.isAfter(latest))
                .max(Comparator.naturalOrder());
        return exact.map(DateRange::exact).orElse(DateRange.noLaterThan(latest));
    }

    // ---- shared ------------------------------------------------------------------------------------------------

    private static final String ADDED = "(?i)(\\b(added|addition of|assign\\w*|new)\\b[^.;]*\\b%1$s\\b|\\bto %1$s\\b)";
    private static final String REMOVED = "(?i)(\\b(delet\\w*|remov\\w*|withdr\\w*)\\b[^.;]*\\b%1$s\\b|\\bfrom %1$s to\\b)";

    /**
     * When the code was assigned for this period: no later than the first sighting (CLDR's, if earlier), exactly on
     * the date of a change-log entry adding it, if one falls after the previous period began.
     */
    private DateRange assigned(String code, Interval period, CodeEvidence ev, Optional<LocalDate> previousGone,
            Optional<LocalDate> previousStart) {
        LocalDate latest = earliestCldrStart(period, ev.cldr(), previousGone).orElse(period.firstSeen());
        Optional<LocalDate> exact = changeLogDate(ev.changeLog(), code, ADDED,
                d -> !d.isAfter(latest) && previousStart.map(d::isAfter).orElse(true));
        return exact.map(DateRange::exact).orElse(DateRange.noLaterThan(latest));
    }

    /** When the code was withdrawn: no later than the first source to drop it, exactly per the change log if named. */
    private DateRange withdrawn(String code, Interval period, CodeEvidence ev, DateRange assigned) {
        LocalDate latest = period.goneBy().orElseThrow();
        for (Interval cldr : ev.cldr()) {
            if (cldr.goneBy().isPresent() && cldr.goneBy().get().isAfter(period.firstSeen())
                    && cldr.goneBy().get().isBefore(latest)) {
                latest = cldr.goneBy().get();
            }
        }
        LocalDate bound = latest;
        Optional<LocalDate> exact = changeLogDate(ev.changeLog(), code, REMOVED,
                d -> !d.isAfter(bound) && !d.isBefore(assigned.earliest().orElse(period.firstSeen().minusYears(30))));
        if (exact.isPresent()) {
            return DateRange.exact(exact.get());
        }
        // ISO changes a country's subdivisions only in updates its change log records, so the withdrawal happened on
        // one of the country's change-log dates within the range. (BS-NP's 2010 entry only says the Bahamas went
        // from 21 to 32 districts, but it's the only Bahamas change before BS-NP disappeared.)
        LocalDate floor = assigned.earliest().orElse(LocalDate.MIN);
        List<LocalDate> candidates = evidence.countryChangeLog().getOrDefault(code.substring(0, 2), List.of()).stream()
                .flatMap(e -> e.date().stream())
                .filter(d -> !d.isAfter(bound) && !d.isBefore(floor))
                .sorted()
                .toList();
        return candidates.isEmpty() ? DateRange.noLaterThan(latest)
                : candidates.size() == 1 ? DateRange.exact(candidates.get(0))
                : DateRange.between(candidates.get(0), candidates.get(candidates.size() - 1));
    }

    /** CLDR's first sighting, if it started listing the code after the previous holding ended and before iso-codes. */
    private static Optional<LocalDate> earliestCldrStart(Interval period, List<Interval> cldr, Optional<LocalDate> previousGone) {
        return cldr.stream()
                .map(Interval::firstSeen)
                .filter(d -> d.isBefore(period.firstSeen()))
                .filter(d -> previousGone.map(g -> !d.isBefore(g)).orElse(true))
                .filter(d -> !d.isBefore(period.firstSeen().minusYears(10)))
                .min(Comparator.naturalOrder());
    }

    private static Optional<LocalDate> changeLogDate(List<ChangeLogEntry> entries, String code, String pattern,
            java.util.function.Predicate<LocalDate> plausible) {
        Pattern p = Pattern.compile(String.format(pattern, Pattern.quote(code)));
        return entries.stream()
                .filter(e -> e.date().isPresent() && p.matcher(e.text()).find())
                .map(e -> e.date().get())
                .filter(plausible)
                .max(Comparator.naturalOrder());
    }

    /** Sub-periods that began with a handover from a different holder, without a gap. */
    private final java.util.Set<Interval> handovers = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * Splits an interval where the code's name changed to a different holder's (MA-02: Gharb-Chrarda-Beni Hssen,
     * then L'Oriental, in Morocco's 2018 reorganisation). Spelling changes don't split.
     */
    private List<Interval> splitHandovers(String standard, String code, List<Interval> intervals) {
        List<Interval> out = new ArrayList<>();
        for (Interval interval : intervals) {
            List<Evidence.Name> names = interval.names();
            int start = 0;
            for (int i = 1; i <= names.size(); i++) {
                boolean end = i == names.size();
                if (!end && (sameHolder(names.get(i - 1).name(), names.get(i).name())
                        || !moved(code, names.get(i - 1), names.get(i)))) {
                    continue;
                }
                Evidence.Name first = names.get(start);
                Evidence.Name lastName = names.get(i - 1);
                Map<String, String> values = new LinkedHashMap<>(interval.values());
                values.put("name", lastName.name());
                Interval part = new Interval(first.firstSeen(), lastName.lastSeen(),
                        end ? interval.goneBy() : Optional.of(names.get(i).firstSeen()), values, names.subList(start, i));
                if (start > 0) {
                    handovers.add(part);
                    note(HistoryNote.Kind.HOLDER_CHANGED, standard, code, "\"" + names.get(start - 1).name() + "\" then \""
                            + first.name() + "\" (around " + first.firstSeen() + "), a name that moved between codes: "
                            + "treated as a handover");
                }
                out.add(part);
                start = i;
            }
        }
        return out;
    }

    /**
     * When a code passed from one holder to the next without a gap: no later than the first snapshot with the new
     * holder, and on one of the country's change-log dates after the previous holder's assignment.
     */
    private DateRange handover(String code, Interval from, Interval to, DateRange fromAssigned) {
        LocalDate latest = to.firstSeen();
        LocalDate floor = fromAssigned.latest().orElse(LocalDate.MIN);
        List<LocalDate> candidates = evidence.countryChangeLog().getOrDefault(code.substring(0, 2), List.of()).stream()
                .flatMap(e -> e.date().stream())
                .filter(d -> !d.isAfter(latest) && d.isAfter(floor))
                .sorted()
                .toList();
        return candidates.isEmpty() ? DateRange.noLaterThan(latest)
                : candidates.size() == 1 ? DateRange.exact(candidates.get(0))
                : DateRange.between(candidates.get(0), candidates.get(candidates.size() - 1));
    }

    private List<Interval> closeGlitches(String standard, String code, List<Interval> intervals) {
        List<Interval> out = new ArrayList<>();
        for (Interval interval : intervals) {
            if (!out.isEmpty()) {
                Interval previous = out.get(out.size() - 1);
                boolean sameHolder = sameHolder(previous.values().get("name"), interval.values().get("name"));
                long gap = ChronoUnit.DAYS.between(previous.goneBy().orElseThrow(), interval.firstSeen());
                if (sameHolder && gap <= GLITCH_DAYS) {
                    note(HistoryNote.Kind.GLITCH_CLOSED, standard, code, "missing from " + previous.goneBy().get()
                            + " to " + interval.firstSeen() + "; treated as continuous");
                    List<Evidence.Name> names = new ArrayList<>(previous.names());
                    names.addAll(interval.names());
                    out.set(out.size() - 1, new Interval(previous.firstSeen(), interval.lastSeen(), interval.goneBy(),
                            interval.values(), names));
                    continue;
                }
            }
            out.add(interval);
        }
        return out;
    }

    /**
     * Whether two names for a code, either side of a gap, are the same holder. Names change spelling far more often
     * than codes change hands, so this is generous: same name ignoring case, accents and punctuation; one containing
     * the other ({@code Wales}, {@code Wales; Cymru}); or at least 75% similar ({@code Serrai}, {@code Serres}).
     * Genuine reassignments ({@code FR-972}: Guyane, then Martinique) fall below that.
     */
    static boolean sameHolder(String a, String b) {
        String x = holder(a);
        String y = holder(b);
        if (x.equals(y) || x.contains(y) || y.contains(x)) {
            return true;
        }
        int distance = levenshtein(x, y);
        return 1.0 - (double) distance / Math.max(x.length(), y.length()) >= 0.75;
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** Holder identity: the name, case- and accent-insensitive, ignoring punctuation. */
    static String holder(String name) {
        if (name == null) {
            return "?";
        }
        String folded = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return folded.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String span(List<Interval> intervals) {
        return intervals.isEmpty() ? "never" : intervals.get(0).firstSeen() + " to "
                + intervals.get(intervals.size() - 1).goneBy().map(Object::toString).orElse("now");
    }

    private void note(HistoryNote.Kind kind, String standard, String code, String detail) {
        notes.add(new HistoryNote(kind, standard, code, detail));
    }
}
