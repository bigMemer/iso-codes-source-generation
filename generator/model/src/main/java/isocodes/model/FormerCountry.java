package isocodes.model;

import java.util.List;
import java.util.Optional;

/** An ISO 3166-3 formerly used country name, in any schema version. */
public sealed interface FormerCountry permits FormerCountry.V1, FormerCountry.V2 {

    /** Upcasts this entry to the newest schema version. */
    V2 toLatest();

    /** Schema 1: no alpha-2 code (iso-codes before 3.76). */
    record V1(
            String alpha3,
            String alpha4,
            String name,
            Optional<String> numeric,
            Optional<String> comment,
            String withdrawalDate) implements FormerCountry {

        public V1 {
            Require.nonNull(alpha3, alpha4, name, numeric, comment, withdrawalDate);
        }

        /** ISO 3166-3 alpha-4 codes start with the former alpha-2 code, so it can always be recovered. */
        @Override
        public V2 toLatest() {
            return new V2(alpha4.substring(0, 2), alpha3, alpha4, name, numeric, comment, withdrawalDate);
        }
    }

    /** Schema 2: adds the former alpha-2 code. */
    record V2(
            String alpha2,
            String alpha3,
            String alpha4,
            String name,
            Optional<String> numeric,
            Optional<String> comment,
            String withdrawalDate) implements FormerCountry {

        public V2 {
            Require.nonNull(alpha2, alpha3, alpha4, name, numeric, comment, withdrawalDate);
        }

        @Override
        public V2 toLatest() {
            return this;
        }
    }

    StandardDef<V2> DEFINITION = new StandardDef<>(
            "3166-3",
            "ISO 3166-3 codes for formerly used names of countries.",
            List.of(
                    FieldDef.required("alpha_2", "Two letter alphabetic code of the item", "[A-Z]{2}", V2::alpha2),
                    FieldDef.required("alpha_3", "Three letter alphabetic code of the item", "[A-Z]{3}", V2::alpha3),
                    FieldDef.required("alpha_4", "Four letter alphabetic code of the item", "[A-Z]{2,4}", V2::alpha4),
                    FieldDef.required("name", "Name of the item", null, V2::name),
                    FieldDef.optional("numeric", "Three digit numeric code of the item, including leading zeros",
                            "[0-9]{3}", V2::numeric),
                    FieldDef.optional("comment", "Comment for the item", null, V2::comment),
                    FieldDef.required("withdrawal_date", "Date of withdrawal from ISO 3166-1",
                            "[0-9]{4}(|-[0-9]{2}){2}", V2::withdrawalDate)),
            "alpha_4",
            List.of("alpha_4"));
}
