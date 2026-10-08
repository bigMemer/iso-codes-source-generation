package isocodes.source.isocodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One iso-codes data file and every shape it has been published in.
 *
 * @param code   the standard's number as used in iso-codes, e.g. {@code 3166-1}
 * @param shapes known shapes, in any order
 * @param <R>    the model's sealed interface for the standard
 */
record StandardFile<R>(String code, List<Shape<? extends R>> shapes) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    StandardFile {
        shapes = shapes.stream().sorted(Comparator.comparingInt((Shape<? extends R> s) -> s.schema()).reversed()).toList();
    }

    String fileName() {
        return "iso_" + code + ".json";
    }

    /** Parses the file with the newest shape that fits every entry. */
    List<R> parse(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(fileName() + " is not valid JSON", e);
        }
        JsonNode array = root.get(code);
        if (array == null || !array.isArray()) {
            throw new UnknownShapeException(fileName() + " has no \"" + code + "\" array");
        }
        List<JsonNode> rows = new ArrayList<>();
        array.forEach(rows::add);

        List<String> rejections = new ArrayList<>();
        for (Shape<? extends R> shape : shapes) {
            var mismatch = shape.mismatch(rows);
            if (mismatch.isEmpty()) {
                return rows.stream().<R>map(row -> shape.parser().apply(row)).toList();
            }
            rejections.add("schema " + shape.schema() + ": " + mismatch.get());
        }
        throw new UnknownShapeException(fileName() + " matches no known shape. Add a new schema version to the model "
                + "and a matching shape here.\n  " + String.join("\n  ", rejections));
    }
}
