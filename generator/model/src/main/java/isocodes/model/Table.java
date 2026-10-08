package isocodes.model;

import java.util.List;

/** All entries of one standard, in the newest schema version, in source order. */
public record Table<T>(StandardDef<T> standard, List<T> rows) {

    public Table {
        rows = List.copyOf(rows);
    }
}
