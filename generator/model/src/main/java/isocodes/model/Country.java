package isocodes.model;

import java.util.List;
import java.util.Optional;

/** An ISO 3166-1 country, in any schema version. */
public sealed interface Country permits Country.V1, Country.V2 {

    /** Upcasts this entry to the newest schema version. */
    V2 toLatest();

    /** Schema 1: no flag (iso-codes before 4.8.0). */
    record V1(
            String alpha2,
            String alpha3,
            String name,
            String numeric,
            Optional<String> officialName,
            Optional<String> commonName) implements Country {

        public V1 {
            Require.nonNull(alpha2, alpha3, name, numeric, officialName, commonName);
        }

        @Override
        public V2 toLatest() {
            return new V2(alpha2, alpha3, flagOf(alpha2), name, numeric, officialName, commonName);
        }
    }

    /** Schema 2: adds the flag emoji. */
    record V2(
            String alpha2,
            String alpha3,
            String flag,
            String name,
            String numeric,
            Optional<String> officialName,
            Optional<String> commonName) implements Country {

        public V2 {
            Require.nonNull(alpha2, alpha3, flag, name, numeric, officialName, commonName);
        }

        @Override
        public V2 toLatest() {
            return this;
        }
    }

    StandardDef<V2> DEFINITION = new StandardDef<>(
            "3166-1",
            "ISO 3166-1 codes for the representation of names of countries.",
            List.of(
                    FieldDef.required("alpha_2", "Two letter alphabetic code of the item", "[A-Z]{2}", V2::alpha2),
                    FieldDef.required("alpha_3", "Three letter alphabetic code of the item", "[A-Z]{3}", V2::alpha3),
                    FieldDef.required("flag", "Flag of country, using Unicode regional indicator symbol letters",
                            "[\\x{1F1E6}-\\x{1F1FF}]{2}", V2::flag),
                    FieldDef.required("name", "Name of the item", null, V2::name),
                    FieldDef.required("numeric", "Three digit numeric code of the item, including leading zeros",
                            "[0-9]{3}", V2::numeric),
                    FieldDef.optional("official_name", "Official name of the item", null, V2::officialName),
                    FieldDef.optional("common_name", "Common name of the item", null, V2::commonName)),
            "alpha_2",
            List.of("alpha_2", "alpha_3", "numeric"));

    /** A flag emoji is the alpha-2 code spelled in Unicode regional indicator symbols. */
    private static String flagOf(String alpha2) {
        StringBuilder flag = new StringBuilder();
        alpha2.chars().forEach(c -> flag.appendCodePoint(0x1F1E6 + c - 'A'));
        return flag.toString();
    }
}
