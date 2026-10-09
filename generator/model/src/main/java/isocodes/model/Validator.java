package isocodes.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Checks a table against its standard: required fields non-blank, formats, uniqueness. */
final class Validator {

    private Validator() {}

    static <T> void check(Table<T> table, List<String> problems) {
        StandardDef<T> standard = table.standard();
        for (FieldDef<T> field : standard.fields()) {
            boolean primary = field.id().equals(standard.primaryKey());
            boolean unique = standard.uniqueFields().contains(field.id());
            Map<String, Integer> firstRowByValue = new HashMap<>();
            for (int i = 0; i < table.rows().size(); i++) {
                T row = table.rows().get(i);
                Optional<String> value = field.valueOf(row);
                String where = "ISO " + standard.id() + " entry " + i + ", " + field.id();
                if (value.isEmpty()) {
                    if (!field.optional()) {
                        problems.add(where + ": missing");
                    }
                    continue;
                }
                String text = value.get();
                if (text.isBlank()) {
                    problems.add(where + ": blank");
                }
                if (field.pattern().isPresent() && !field.pattern().get().matcher(text).matches()) {
                    problems.add(where + ": \"" + text + "\" does not match " + field.pattern().get());
                }
                // Withdrawn entries may share secondary codes with active ones (a reused alpha-3); lookups prefer the
                // active entry. Primary codes stay unique across all entries.
                if (!primary && table.isWithdrawn(row)) {
                    continue;
                }
                Integer first = firstRowByValue.putIfAbsent(text, i);
                if (unique && first != null) {
                    problems.add(where + ": \"" + text + "\" duplicates entry " + first);
                }
            }
        }
    }
}
