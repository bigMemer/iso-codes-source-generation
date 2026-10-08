package isocodes.emitter.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import isocodes.model.Country;
import isocodes.model.IsoCodesDataset;
import isocodes.model.SourceData;
import isocodes.model.Subdivision;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaEmitterTest {

    private static IsoCodesDataset dataset() {
        List<Subdivision.V1> subdivisions = new ArrayList<>();
        // Enough rows to need more than one data holder class.
        IntStream.range(0, 600).forEach(i -> subdivisions.add(new Subdivision.V1(
                "DE-" + Integer.toString(i, 36).toUpperCase(java.util.Locale.ROOT),
                "Subdivision " + i, Optional.empty(), "State")));
        return IsoCodesDataset.fromSource(new SourceData("Test data", "9.9", "CC0-1.0",
                List.of(new Country.V2("DE", "DEU", "🇩🇪", "Germany", "276", Optional.empty(), Optional.empty())),
                subdivisions));
    }

    @Test
    void emittedSourcesCompile(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("src");
        JavaEmitter.write(dataset(), "test.out", src);

        List<String> files;
        try (Stream<Path> paths = Files.walk(src)) {
            files = paths.filter(p -> p.toString().endsWith(".java")).map(Path::toString).toList();
        }
        List<String> args = new ArrayList<>(List.of("-d", dir.resolve("classes").toString(), "-Xlint:all", "-Werror"));
        args.addAll(files);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
    }

    @Test
    void emitsSourceMetadataAndLookups(@TempDir Path dir) throws IOException {
        JavaEmitter.write(dataset(), "test.out", dir);
        String country = Files.readString(dir.resolve("test/out/iso3166/Country.java"));
        assertTrue(country.startsWith("// SPDX-License-Identifier: CC0-1.0\n// Generated from Test data 9.9."), country);
        assertTrue(country.contains("DE(\"DE\", \"DEU\", \"🇩🇪\", \"Germany\", \"276\", null, null)"), country);
        assertTrue(country.contains("public static Optional<Country> fromAlpha3(String alpha3)"), country);
        assertTrue(Files.exists(dir.resolve("test/out/iso3166/SubdivisionData1.java")));
    }

    @Test
    void javaNames() {
        assertEquals("englishName", JavaEmitter.javaName("name"));
        assertEquals("alpha2", JavaEmitter.javaName("alpha_2"));
        assertEquals("withdrawalDate", JavaEmitter.javaName("withdrawal_date"));
        assertEquals("QAA_QTZ", JavaEmitter.constantName("qaa-qtz"));
        assertEquals("_123", JavaEmitter.constantName("123"));
    }
}
