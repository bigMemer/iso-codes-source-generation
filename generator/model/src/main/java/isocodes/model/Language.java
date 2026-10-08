package isocodes.model;

import java.util.List;
import java.util.Optional;

/** An ISO 639-3 language, in any schema version. */
public sealed interface Language permits Language.V1 {

    /** Upcasts this entry to the newest schema version. */
    V1 toLatest();

    /** Schema 1. */
    record V1(
            String alpha3,
            String name,
            String scope,
            String type,
            Optional<String> alpha2,
            Optional<String> commonName,
            Optional<String> invertedName,
            Optional<String> bibliographic) implements Language {

        public V1 {
            Require.nonNull(alpha3, name, scope, type, alpha2, commonName, invertedName, bibliographic);
        }

        @Override
        public V1 toLatest() {
            return this;
        }
    }

    StandardDef<V1> DEFINITION = new StandardDef<>(
            "639-3",
            "ISO 639-3 codes for comprehensive coverage of languages.",
            List.of(
                    FieldDef.required("alpha_3", "Three letter terminology code of the language", "[a-z]{3}",
                            V1::alpha3),
                    FieldDef.required("name", "Reference name of the language", null, V1::name),
                    FieldDef.required("scope",
                            "Scope of the language: I(ndividual), M(acrolanguage), S(pecial)", "[IMS]", V1::scope),
                    FieldDef.required("type",
                            "Type of the language: A(ncient), C(onstructed), E(xtinct), H(istorical), L(iving), S(pecial)",
                            "[ACEHLS]", V1::type),
                    FieldDef.optional("alpha_2", "Two letter alphabetic code of the language from part 1",
                            "[a-z]{2}", V1::alpha2),
                    FieldDef.optional("common_name", "Common name of the language", null, V1::commonName),
                    FieldDef.optional("inverted_name", "Inverted name of the language", null, V1::invertedName),
                    FieldDef.optional("bibliographic",
                            "Three letter bibliographic code of the language from part 2", "[a-z]{3}",
                            V1::bibliographic)),
            "alpha_3",
            List.of("alpha_3", "alpha_2", "bibliographic"));
}
