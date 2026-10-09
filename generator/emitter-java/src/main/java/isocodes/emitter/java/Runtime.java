package isocodes.emitter.java;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Fixed classes every generated library contains (strictness, match results, exceptions, the matcher), copied from
 * templates. They hold no data, so they carry only our own licence.
 */
final class Runtime {

    private Runtime() {}

    /** Template name, and the sub-package it goes in ("" for the base package). */
    private record Template(String name, String subPackage) {}

    private static final List<Template> TEMPLATES = List.of(
            new Template("Relaxation", ""),
            new Template("Strictness", ""),
            new Template("Match", ""),
            new Template("Validation", ""),
            new Template("CodeRejectedException", ""),
            new Template("UnknownCodeException", ""),
            new Template("MeaningChangedException", ""),
            new Template("HistoryState", ""),
            new Template("HistoryPolicy", ""),
            new Template("HistoryCheck", ""),
            new Template("Lookup", "internal"),
            new Template("Timeline", "internal"));

    static void write(EmitOptions options, Path outputDir) {
        String header = "// SPDX-License-Identifier: " + options.ownLicense().orElse("NOASSERTION") + "\n"
                + "// Generated runtime support. Do not edit.\n";
        for (Template template : TEMPLATES) {
            String pkg = options.basePackage() + (template.subPackage().isEmpty() ? "" : "." + template.subPackage());
            String source = header + read(template.name()).replace("__PACKAGE__", options.basePackage());
            Path file = outputDir.resolve(pkg.replace('.', '/')).resolve(template.name() + ".java");
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, source);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static String read(String name) {
        try (InputStream in = Runtime.class.getResourceAsStream("runtime/" + name + ".java.tmpl")) {
            if (in == null) {
                throw new IllegalStateException("Missing runtime template " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
