package isocodes.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** An ISO 3166-2 country subdivision, in any schema version. */
public sealed interface Subdivision permits Subdivision.V1 {

    /** Upcasts this entry to the newest schema version. */
    V1 toLatest();

    /** Schema 1. */
    record V1(String code, String name, Optional<String> parent, String type) implements Subdivision {

        public V1 {
            Require.nonNull(code, name, parent, type);
        }

        @Override
        public V1 toLatest() {
            return this;
        }
    }

    StandardDef<V1> DEFINITION = new StandardDef<>(
            "3166-2",
            "ISO 3166-2 codes for the representation of country subdivisions.",
            List.of(
                    FieldDef.required("code", "Code of the country subset item", "[A-Z]{2}-[A-Z0-9]{1,3}", V1::code),
                    FieldDef.required("name", "Name of the country subset item", null, V1::name),
                    FieldDef.optional("parent", "Parent of the country subset item", null, V1::parent),
                    FieldDef.required("type", "Type of subset of the country", null, V1::type)),
            "code",
            List.of("code"),
            f -> new V1(f.required("code"), f.required("name"), f.optional("parent"), f.required("type")),
            // ISO 3166-2's first edition, per ISO's catalogue: 1998-12.
            Map.of("code", DateRange.parse("1998-12")));
}
