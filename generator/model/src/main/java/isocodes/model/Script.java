package isocodes.model;

import java.util.List;

/** An ISO 15924 script, in any schema version. */
public sealed interface Script permits Script.V1 {

    /** Upcasts this entry to the newest schema version. */
    V1 toLatest();

    /** Schema 1. */
    record V1(String alpha4, String name, String numeric) implements Script {

        public V1 {
            Require.nonNull(alpha4, name, numeric);
        }

        @Override
        public V1 toLatest() {
            return this;
        }
    }

    StandardDef<V1> DEFINITION = new StandardDef<>(
            "15924",
            "ISO 15924 codes for the representation of names of scripts.",
            List.of(
                    FieldDef.required("alpha_4", "Four letter alphabetic code of the script", "[A-Z][a-z]{3}",
                            V1::alpha4),
                    FieldDef.required("name", "Name of the script", null, V1::name),
                    FieldDef.required("numeric", "Three digit numeric code of the script, including leading zeros",
                            "[0-9]{3}", V1::numeric)),
            "alpha_4",
            List.of("alpha_4", "numeric"));
}
