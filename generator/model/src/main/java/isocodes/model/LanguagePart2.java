package isocodes.model;

import java.util.List;
import java.util.Optional;

/** An ISO 639-2 language, in any schema version. */
public sealed interface LanguagePart2 permits LanguagePart2.V1 {

    /** Upcasts this entry to the newest schema version. */
    V1 toLatest();

    /** Schema 1. */
    record V1(
            String alpha3,
            String name,
            Optional<String> alpha2,
            Optional<String> bibliographic,
            Optional<String> commonName) implements LanguagePart2 {

        public V1 {
            Require.nonNull(alpha3, name, alpha2, bibliographic, commonName);
        }

        @Override
        public V1 toLatest() {
            return this;
        }
    }

    StandardDef<V1> DEFINITION = new StandardDef<>(
            "639-2",
            "ISO 639-2 alpha-3 codes for the representation of names of languages.",
            List.of(
                    FieldDef.required("alpha_3", "Three letter terminology code of the language",
                            "[a-z]{3}(-[a-z]{3})?", V1::alpha3),
                    FieldDef.required("name", "Name of the item", null, V1::name),
                    FieldDef.optional("alpha_2", "Two letter alphabetic code of the language from part 1",
                            "[a-z]{2}", V1::alpha2),
                    FieldDef.optional("bibliographic", "Three letter bibliographic code of the language",
                            "[a-z]{3}", V1::bibliographic),
                    FieldDef.optional("common_name", "Common name of the language", null, V1::commonName)),
            "alpha_3",
            List.of("alpha_3", "alpha_2", "bibliographic"));
}
