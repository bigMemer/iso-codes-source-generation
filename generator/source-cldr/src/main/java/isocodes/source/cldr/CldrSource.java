package isocodes.source.cldr;

import isocodes.model.Contribution;
import isocodes.model.SourceInfo;
import isocodes.model.Withdrawal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Reads one CLDR release as a contribution to ISO 3166-1 and ISO 3166-2. */
public final class CldrSource {

    private CldrSource() {}

    /**
     * @param version a CLDR release, e.g. {@code 48.2}
     * @param files   the release's files by path, e.g. a {@link CldrFiles}
     */
    public static Contribution read(String version, Function<String, String> files) {
        Document regionValidity = Xml.parse(files.apply("common/validity/region.xml"));
        Document subdivisionValidity = Xml.parse(files.apply("common/validity/subdivision.xml"));
        Document metadata = Xml.parse(files.apply("common/supplemental/supplementalMetadata.xml"));
        Document supplemental = Xml.parse(files.apply("common/supplemental/supplementalData.xml"));
        Document regionNames = Xml.parse(files.apply("common/main/en.xml"));
        Document subdivisionNames = Xml.parse(files.apply("common/subdivisions/en.xml"));
        Document containment = Xml.parse(files.apply("common/supplemental/subdivisions.xml"));

        return new Contribution(
                new SourceInfo("Unicode CLDR", version, "Unicode-3.0"),
                Map.of(
                        "3166-1", countries(regionValidity, metadata, supplemental, regionNames),
                        "3166-2", subdivisions(subdivisionValidity, metadata, subdivisionNames, containment)));
    }

    private static Contribution.Standard countries(
            Document validity, Document metadata, Document supplemental, Document names) {
        Map<String, Element> codes = byType(Xml.elements(supplemental, "territoryCodes"));
        Map<String, String> englishNames = namesWithoutAlt(Xml.elements(names, "territory"));

        Map<String, Map<String, String>> entries = new LinkedHashMap<>();
        for (String id : ids(validity, "region", "regular")) {
            if (!id.matches("[A-Z]{2}")) {
                continue;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("alpha_2", id);
            Element territory = codes.get(id);
            if (territory != null && !territory.getAttribute("alpha3").isEmpty()) {
                fields.put("alpha_3", territory.getAttribute("alpha3"));
            }
            if (territory != null && !territory.getAttribute("numeric").isEmpty()) {
                fields.put("numeric", territory.getAttribute("numeric"));
            }
            if (englishNames.containsKey(id)) {
                fields.put("name", englishNames.get(id));
            }
            entries.put(id, fields);
        }

        Map<String, String> reasons = aliasReasons(Xml.elements(metadata, "territoryAlias"));
        Map<String, Withdrawal> withdrawn = new TreeMap<>();
        for (String id : ids(validity, "region", "deprecated")) {
            if (id.matches("[A-Z]{2}")) {
                withdrawn.put(id, withdrawal(reasons.get(id)));
            }
        }
        return new Contribution.Standard(entries, withdrawn);
    }

    private static Contribution.Standard subdivisions(
            Document validity, Document metadata, Document names, Document containment) {
        Map<String, String> englishNames = namesWithoutAlt(Xml.elements(names, "subdivision"));
        Map<String, String> parents = new HashMap<>();
        for (Element subgroup : Xml.elements(containment, "subgroup")) {
            String container = subgroup.getAttribute("type");
            if (container.matches("[A-Z]{2}")) {
                continue; // a country, not a parent subdivision
            }
            for (String child : subgroup.getAttribute("contains").trim().split("\\s+")) {
                parents.put(child, isoCode(container));
            }
        }

        Map<String, Map<String, String>> entries = new LinkedHashMap<>();
        for (String id : ids(validity, "subdivision", "regular")) {
            String code = isoCode(id);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("code", code);
            if (englishNames.containsKey(id)) {
                fields.put("name", englishNames.get(id));
            }
            if (parents.containsKey(id)) {
                fields.put("parent", parents.get(id));
            }
            entries.put(code, fields);
        }

        Map<String, String> reasons = aliasReasons(Xml.elements(metadata, "subdivisionAlias"));
        Map<String, Withdrawal> withdrawn = new TreeMap<>();
        for (String id : ids(validity, "subdivision", "deprecated")) {
            withdrawn.put(isoCode(id), withdrawal(reasons.get(id)));
        }
        return new Contribution.Standard(entries, withdrawn);
    }

    /** CLDR writes {@code US-CA} as {@code usca}. */
    static String isoCode(String cldrId) {
        String id = cldrId.toUpperCase(Locale.ROOT);
        return id.substring(0, 2) + "-" + id.substring(2);
    }

    /**
     * Ids listed in a validity file under one status, sorted, with CLDR's range shorthand expanded: {@code ad02~8}
     * means {@code ad02} to {@code ad08}, varying the last character.
     */
    static List<String> ids(Document validity, String type, String status) {
        List<String> ids = new ArrayList<>();
        for (Element id : Xml.elements(validity, "id")) {
            if (!id.getAttribute("type").equals(type) || !id.getAttribute("idStatus").equals(status)) {
                continue;
            }
            for (String token : id.getTextContent().trim().split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                int tilde = token.indexOf('~');
                if (tilde < 0) {
                    ids.add(token);
                    continue;
                }
                String start = token.substring(0, tilde);
                String end = token.substring(tilde + 1);
                if (end.length() != 1) {
                    throw new IllegalStateException("Unsupported CLDR range " + token);
                }
                for (char c = start.charAt(start.length() - 1); c <= end.charAt(0); c++) {
                    ids.add(start.substring(0, start.length() - 1) + c);
                }
            }
        }
        ids.sort(null);
        return ids;
    }

    private static Withdrawal withdrawal(String reason) {
        return "overlong".equals(reason) ? Withdrawal.REPRESENTED_ELSEWHERE : Withdrawal.WITHDRAWN;
    }

    private static Map<String, String> aliasReasons(List<Element> aliases) {
        Map<String, String> reasons = new HashMap<>();
        for (Element alias : aliases) {
            reasons.put(alias.getAttribute("type"), alias.getAttribute("reason"));
        }
        return reasons;
    }

    private static Map<String, String> namesWithoutAlt(List<Element> elements) {
        Map<String, String> names = new HashMap<>();
        for (Element element : elements) {
            if (element.getAttribute("alt").isEmpty()) {
                names.put(element.getAttribute("type"), element.getTextContent().trim());
            }
        }
        return names;
    }

    private static Map<String, Element> byType(List<Element> elements) {
        Map<String, Element> map = new HashMap<>();
        elements.forEach(e -> map.put(e.getAttribute("type"), e));
        return map;
    }
}
