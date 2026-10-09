package isocodes.model;

import java.util.List;

import java.util.Map;
import java.util.Optional;

/**
 * All entries of one standard, in the newest schema version: active entries in source order, then withdrawn ones.
 *
 * @param standard   the standard
 * @param rows       the entries
 * @param lifecycles history per primary code; may be empty when no history is available
 */
public record Table<T>(StandardDef<T> standard, List<T> rows, Map<String, Lifecycle> lifecycles) {

    public Table {
        rows = List.copyOf(rows);
        lifecycles = Map.copyOf(lifecycles);
    }

    public Table(StandardDef<T> standard, List<T> rows) {
        this(standard, rows, Map.of());
    }

    public String primaryCode(T row) {
        return standard.field(standard.primaryKey()).valueOf(row).orElseThrow();
    }

    public Optional<Lifecycle> lifecycle(T row) {
        return Optional.ofNullable(lifecycles.get(primaryCode(row)));
    }

    public boolean isWithdrawn(T row) {
        return lifecycle(row).map(Lifecycle::isWithdrawn).orElse(false);
    }
}
