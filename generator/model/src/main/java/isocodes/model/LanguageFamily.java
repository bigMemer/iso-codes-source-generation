package isocodes.model;

import java.util.List;

/** An ISO 639-5 language family or group, in any schema version. */
public sealed interface LanguageFamily permits LanguageFamily.V1 {

    /** Upcasts this entry to the newest schema version. */
    V1 toLatest();

    /** Schema 1. */
    record V1(String alpha3, String name) implements LanguageFamily {

        public V1 {
            Require.nonNull(alpha3, name);
        }

        @Override
        public V1 toLatest() {
            return this;
        }
    }

    StandardDef<V1> DEFINITION = new StandardDef<>(
            "639-5",
            "ISO 639-5 codes for language families and groups.",
            List.of(
                    FieldDef.required("alpha_3", "Three letter code of the language family or group", "[a-z]{3}",
                            V1::alpha3),
                    FieldDef.required("name", "Name of the language family or group", null, V1::name)),
            "alpha_3",
            List.of("alpha_3"));
}
