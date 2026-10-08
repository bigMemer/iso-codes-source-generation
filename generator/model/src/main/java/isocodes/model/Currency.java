package isocodes.model;

import java.util.List;

/** An ISO 4217 currency, in any schema version. */
public sealed interface Currency permits Currency.V1 {

    /** Upcasts this entry to the newest schema version. */
    V1 toLatest();

    /** Schema 1. */
    record V1(String alpha3, String name, String numeric) implements Currency {

        public V1 {
            Require.nonNull(alpha3, name, numeric);
        }

        @Override
        public V1 toLatest() {
            return this;
        }
    }

    StandardDef<V1> DEFINITION = new StandardDef<>(
            "4217",
            "ISO 4217 codes for the representation of currencies.",
            List.of(
                    FieldDef.required("alpha_3", "Three letter code of the currency", "[A-Z]{3}", V1::alpha3),
                    FieldDef.required("name", "Name of currency", null, V1::name),
                    FieldDef.required("numeric", "Three digit numeric code of the item, including leading zeros",
                            "[0-9]{3}", V1::numeric)),
            "alpha_3",
            List.of("alpha_3", "numeric"));
}
