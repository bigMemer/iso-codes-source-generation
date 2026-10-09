package isocodes.emitter.java;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import isocodes.model.FieldDef;
import isocodes.model.IsoCodesDataset;
import isocodes.model.StandardDef;
import isocodes.model.Table;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Modifier;

/** Turns a dataset into Java source files. */
public final class JavaEmitter {

    /** Rows per data holder class, chosen to stay well inside the 64 KiB method and constant pool limits. */
    private static final int ROWS_PER_HOLDER = 500;

    private static final ClassName STRING = ClassName.get(String.class);
    private static final ClassName OPTIONAL = ClassName.get(Optional.class);
    private static final ClassName LIST = ClassName.get(List.class);
    private static final ClassName MAP = ClassName.get(Map.class);

    private final String basePackage;
    private final EmitOptions options;
    private final String source;
    private final String license;

    private JavaEmitter(EmitOptions options, IsoCodesDataset dataset) {
        this.basePackage = options.basePackage();
        this.options = options;
        this.source = dataset.sources().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
        List<String> licenses = new ArrayList<>();
        options.ownLicense().ifPresent(own -> licenses.add(own.contains(" ") ? "(" + own + ")" : own));
        licenses.addAll(dataset.sourceLicenses());
        this.license = String.join(" AND ", licenses);
    }

    /** Emits every standard in the dataset, plus a class recording the dataset version and its sources. */
    public static List<JavaFile> emit(IsoCodesDataset dataset, EmitOptions options) {
        JavaEmitter emitter = new JavaEmitter(options, dataset);
        List<JavaFile> files = new ArrayList<>();
        for (Table<?> table : dataset.tables()) {
            files.addAll(emitter.emitTable(table));
        }
        files.add(emitter.versionClass(dataset));
        return files;
    }

    /** Emits the dataset, plus the fixed runtime classes it uses, into a source directory. */
    public static void write(IsoCodesDataset dataset, EmitOptions options, Path outputDir) {
        for (JavaFile file : emit(dataset, options)) {
            try {
                file.writeTo(outputDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Runtime.write(options, outputDir);
    }

    /** A model field as it appears on the generated type. */
    private record Field<T>(FieldDef<T> def, String java) {
        boolean required() {
            return !def.optional();
        }

        TypeName accessorType() {
            return required() ? STRING : ParameterizedTypeName.get(OPTIONAL, STRING);
        }
    }

    private <T> List<JavaFile> emitTable(Table<T> table) {
        StandardDef<T> standard = table.standard();
        JavaTarget target = JavaTarget.of(standard.id());
        String pkg = basePackage + "." + target.subPackage();
        ClassName type = ClassName.get(pkg, target.className());
        List<Field<T>> fields = standard.fields().stream().map(def -> new Field<>(def, javaName(def.id()))).toList();
        List<Field<T>> lookups = standard.uniqueFields().stream()
                .map(id -> fields.stream().filter(f -> f.def().id().equals(id)).findFirst().orElseThrow())
                .toList();

        TypeSpec.Builder builder = target.kind() == JavaTarget.Kind.ENUM
                ? TypeSpec.enumBuilder(type)
                : TypeSpec.classBuilder(type).addModifiers(Modifier.FINAL);
        builder.addModifiers(Modifier.PUBLIC)
                .addJavadoc("$L\n\n<p>Generated from $L ($L entries). Do not edit.\n",
                        standard.summary(), source, table.rows().size());

        MethodSpec.Builder constructor = MethodSpec.constructorBuilder();
        for (Field<T> field : fields) {
            builder.addField(STRING, field.java(), Modifier.PRIVATE, Modifier.FINAL);
            constructor.addParameter(STRING, field.java());
            constructor.addStatement("this.$N = $N", field.java(), field.java());
            builder.addMethod(accessor(field));
        }
        builder.addMethod(constructor.build());

        List<JavaFile> files = new ArrayList<>();
        if (target.kind() == JavaTarget.Kind.ENUM) {
            addEnumConstants(builder, table, fields);
            addEnumAll(builder, type, javaName(standard.primaryKey()));
        } else {
            files.addAll(addTableData(builder, type, table.rows(), fields));
            addTableObjectMethods(builder, type, javaName(standard.primaryKey()));
        }
        addLookups(builder, type, standard.id(), lookups);
        files.add(0, javaFile(pkg, builder.build()));
        return files;
    }

    private static MethodSpec accessor(Field<?> field) {
        MethodSpec.Builder method = MethodSpec.methodBuilder(field.java())
                .addModifiers(Modifier.PUBLIC)
                .returns(field.accessorType());
        method.addJavadoc("$L.\n\n@return $L\n", field.def().description(),
                field.required() ? "the value, never null" : "the value, or empty if this entry has none");
        return field.required()
                ? method.addStatement("return $N", field.java()).build()
                : method.addStatement("return $T.ofNullable($N)", OPTIONAL, field.java()).build();
    }

    private static <T> void addEnumConstants(TypeSpec.Builder builder, Table<T> table, List<Field<T>> fields) {
        StandardDef<T> standard = table.standard();
        FieldDef<T> key = standard.field(standard.primaryKey());
        FieldDef<T> name = standard.field("name");
        Set<String> seen = new HashSet<>();
        for (T row : table.rows()) {
            String constant = constantName(key.valueOf(row).orElseThrow());
            if (!seen.add(constant)) {
                throw new IllegalStateException("ISO " + standard.id() + ": duplicate enum constant " + constant);
            }
            builder.addEnumConstant(constant, TypeSpec.anonymousClassBuilder(arguments(row, fields))
                    .addJavadoc("$L.\n", javadocText(name.valueOf(row).orElse(constant)))
                    .build());
        }
    }

    private <T> List<JavaFile> addTableData(TypeSpec.Builder builder, ClassName type, List<T> rows, List<Field<T>> fields) {
        TypeName listType = ParameterizedTypeName.get(LIST, type);
        builder.addField(FieldSpec.builder(listType, "ALL", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).build());

        CodeBlock.Builder init = CodeBlock.builder()
                .addStatement("$T list = new $T<>($L)", listType, ArrayList.class, rows.size());
        List<JavaFile> holders = new ArrayList<>();
        for (int start = 0, n = 0; start < rows.size(); start += ROWS_PER_HOLDER, n++) {
            int end = Math.min(start + ROWS_PER_HOLDER, rows.size());
            ClassName holder = type.peerClass(type.simpleName() + "Data" + n);
            MethodSpec.Builder addTo = MethodSpec.methodBuilder("addTo")
                    .addModifiers(Modifier.STATIC)
                    .addParameter(listType, "out");
            for (T row : rows.subList(start, end)) {
                addTo.addStatement("out.add(new $T($L))", type, arguments(row, fields));
            }
            holders.add(javaFile(type.packageName(), TypeSpec.classBuilder(holder)
                    .addModifiers(Modifier.FINAL)
                    .addJavadoc("Entries $L to $L of {@link $T}.\n", start, end - 1, type)
                    .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
                    .addMethod(addTo.build())
                    .build()));
            init.addStatement("$T.addTo(list)", holder);
        }
        init.addStatement("ALL = $T.copyOf(list)", LIST);
        builder.addStaticBlock(init.build());

        builder.addMethod(MethodSpec.methodBuilder("all")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(listType)
                .addJavadoc("Returns every entry, in upstream order.\n\n@return an unmodifiable list of all entries\n")
                .addStatement("return ALL")
                .build());
        return holders;
    }

    private static void addTableObjectMethods(TypeSpec.Builder builder, ClassName type, String key) {
        builder.addMethod(MethodSpec.methodBuilder("equals")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(boolean.class)
                        .addParameter(Object.class, "other")
                        .addStatement("return other instanceof $T that && $N.equals(that.$N)", type, key, key)
                        .build())
                .addMethod(MethodSpec.methodBuilder("hashCode")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(int.class)
                        .addStatement("return $N.hashCode()", key)
                        .build())
                .addMethod(MethodSpec.methodBuilder("toString")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(String.class)
                        .addStatement("return $N", key)
                        .build());
    }

    /** Enums get the same {@code all()} as table classes, and {@code toString()} returns the canonical code. */
    private static void addEnumAll(TypeSpec.Builder builder, ClassName type, String key) {
        TypeName listType = ParameterizedTypeName.get(LIST, type);
        builder.addField(FieldSpec.builder(listType, "ALL", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$T.of(values())", LIST)
                        .build())
                .addMethod(MethodSpec.methodBuilder("all")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(listType)
                        .addJavadoc("Returns every entry, in upstream order.\n\n@return an unmodifiable list of all entries\n")
                        .addStatement("return ALL")
                        .build())
                .addMethod(MethodSpec.methodBuilder("toString")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(String.class)
                        .addJavadoc("Returns the canonical code, which may differ from {@link #name()}.\n\n"
                                + "@return the canonical $L\n", key)
                        .addStatement("return $N", key)
                        .build());
    }

    /**
     * For each code field: {@code from}, {@code parse} and {@code isValid}, each plain and detailed, each with and
     * without a {@code Strictness}. All share one {@code Lookup}, which implements the spec's matching algorithm.
     */
    private <T> void addLookups(TypeSpec.Builder builder, ClassName type, String standardId, List<Field<T>> lookups) {
        ClassName lookup = ClassName.get(basePackage + ".internal", "Lookup");
        ClassName strictness = ClassName.get(basePackage, "Strictness");
        ClassName match = ClassName.get(basePackage, "Match");
        ClassName validation = ClassName.get(basePackage, "Validation");
        ClassName unknown = ClassName.get(basePackage, "UnknownCodeException");
        TypeName matchOfT = ParameterizedTypeName.get(match, type);
        TypeName optionalT = ParameterizedTypeName.get(OPTIONAL, type);
        TypeName optionalMatch = ParameterizedTypeName.get(OPTIONAL, matchOfT);
        TypeName validationOfT = ParameterizedTypeName.get(validation, type);

        CodeBlock.Builder init = CodeBlock.builder();
        for (Field<T> field : lookups) {
            String index = indexName(field);
            String local = index.toLowerCase(Locale.ROOT);
            builder.addField(FieldSpec.builder(ParameterizedTypeName.get(lookup, type), index,
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).build());
            init.addStatement("$T<$T, $T> $N = new $T<>()", Map.class, STRING, type, local, HashMap.class);
            init.beginControlFlow("for ($T entry : ALL)", type);
            if (field.required()) {
                init.addStatement("$N.put(entry.$N, entry)", local, field.java());
            } else {
                init.beginControlFlow("if (entry.$N != null)", field.java())
                        .addStatement("$N.put(entry.$N, entry)", local, field.java())
                        .endControlFlow();
            }
            init.endControlFlow();
            init.addStatement("$N = new $T<>($S, $S, $L, $N)", index, lookup, standardId, field.def().id(),
                    field.def().id().equals("numeric"), local);

            String suffix = Character.toUpperCase(field.java().charAt(0)) + field.java().substring(1);
            String p = field.java();
            String id = field.def().id();
            String strictDoc = "@param $N the code to look up\n";
            String withDoc = strictDoc + "@param strictness which relaxations to allow\n";
            String nullDoc = "@throws NullPointerException if an argument is null\n";

            // from
            builder.addMethod(method("from" + suffix, optionalT, p, false)
                    .addJavadoc("Finds the entry whose {@code $L} is exactly the given code ({@code Strictness.STRICT}).\n\n"
                            + strictDoc + "@return the entry, or empty if none matches\n" + nullDoc, id, p)
                    .addStatement("return from$L($N, $T.STRICT)", suffix, p, strictness).build());
            builder.addMethod(method("from" + suffix, optionalT, p, true)
                    .addJavadoc("Finds the entry whose {@code $L} matches the code under the given strictness.\n\n"
                            + withDoc + "@return the entry, or empty if none matches\n" + nullDoc, id, p)
                    .addStatement("return $N.match($N, strictness).map($T::entry)", index, p, match).build());
            builder.addMethod(method("from" + suffix + "Detailed", optionalMatch, p, false)
                    .addJavadoc("As {@link #from$L(String)}, also reporting the relaxations the match needed.\n\n"
                            + strictDoc + "@return the match, or empty if none\n" + nullDoc, suffix, p)
                    .addStatement("return from$LDetailed($N, $T.STRICT)", suffix, p, strictness).build());
            builder.addMethod(method("from" + suffix + "Detailed", optionalMatch, p, true)
                    .addJavadoc("As {@link #from$L(String, $T)}, also reporting the relaxations the match needed.\n\n"
                            + withDoc + "@return the match, or empty if none\n" + nullDoc, suffix, strictness, p)
                    .addStatement("return $N.match($N, strictness)", index, p).build());
            // parse
            builder.addMethod(method("parse" + suffix, type, p, false)
                    .addJavadoc("Returns the entry whose {@code $L} is exactly the given code ({@code Strictness.STRICT}).\n\n"
                            + strictDoc + "@return the entry\n@throws $T if none matches\n" + nullDoc, id, p, unknown)
                    .addStatement("return parse$L($N, $T.STRICT)", suffix, p, strictness).build());
            builder.addMethod(method("parse" + suffix, type, p, true)
                    .addJavadoc("Returns the entry whose {@code $L} matches the code under the given strictness.\n\n"
                            + withDoc + "@return the entry\n@throws $T if none matches\n" + nullDoc, id, p, unknown)
                    .addStatement("return parse$LDetailed($N, strictness).entry()", suffix, p).build());
            builder.addMethod(method("parse" + suffix + "Detailed", matchOfT, p, false)
                    .addJavadoc("As {@link #parse$L(String)}, also reporting the relaxations the match needed.\n\n"
                            + strictDoc + "@return the match\n@throws $T if none matches\n" + nullDoc, suffix, p, unknown)
                    .addStatement("return parse$LDetailed($N, $T.STRICT)", suffix, p, strictness).build());
            builder.addMethod(method("parse" + suffix + "Detailed", matchOfT, p, true)
                    .addJavadoc("As {@link #parse$L(String, $T)}, also reporting the relaxations the match needed.\n\n"
                            + withDoc + "@return the match\n@throws $T if none matches\n" + nullDoc,
                            suffix, strictness, p, unknown)
                    .addStatement("return $N.match($N, strictness).orElseThrow(() -> $N.unknown($N, strictness))",
                            index, p, index, p).build());
            // isValid
            builder.addMethod(method("isValid" + suffix, TypeName.BOOLEAN, p, false)
                    .addJavadoc("Whether the code is exactly a known {@code $L} ({@code Strictness.STRICT}).\n\n"
                            + strictDoc + "@return true if an entry matches\n" + nullDoc, id, p)
                    .addStatement("return isValid$L($N, $T.STRICT)", suffix, p, strictness).build());
            builder.addMethod(method("isValid" + suffix, TypeName.BOOLEAN, p, true)
                    .addJavadoc("Whether the code matches a known {@code $L} under the given strictness.\n\n"
                            + withDoc + "@return true if an entry matches\n" + nullDoc, id, p)
                    .addStatement("return $N.match($N, strictness).isPresent()", index, p).build());
            builder.addMethod(method("isValid" + suffix + "Detailed", validationOfT, p, false)
                    .addJavadoc("As {@link #isValid$L(String)}, with the match details.\n\n"
                            + strictDoc + "@return the validation result\n" + nullDoc, suffix, p)
                    .addStatement("return isValid$LDetailed($N, $T.STRICT)", suffix, p, strictness).build());
            builder.addMethod(method("isValid" + suffix + "Detailed", validationOfT, p, true)
                    .addJavadoc("As {@link #isValid$L(String, $T)}, with the match details.\n\n"
                            + withDoc + "@return the validation result\n" + nullDoc, suffix, strictness, p)
                    .addStatement("$T m = $N.match($N, strictness)", optionalMatch, index, p)
                    .addStatement("return new $T<>(m.isPresent(), m)", validation).build());
        }
        builder.addStaticBlock(init.build());
    }

    private MethodSpec.Builder method(String name, TypeName returns, String param, boolean withStrictness) {
        MethodSpec.Builder method = MethodSpec.methodBuilder(name)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(returns)
                .addParameter(STRING, param);
        if (withStrictness) {
            method.addParameter(ClassName.get(basePackage, "Strictness"), "strictness");
        }
        return method;
    }

    private static String indexName(Field<?> field) {
        return "BY_" + constantName(field.def().id());
    }

    private JavaFile versionClass(IsoCodesDataset dataset) {
        return javaFile(basePackage, TypeSpec.classBuilder("IsoCodes")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("Information about the dataset these classes were generated from.\n")
                .addField(FieldSpec.builder(STRING, "VERSION", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addJavadoc("The dataset version, e.g. {@code 2026.10.0}.\n")
                        .initializer("$S", options.datasetVersion())
                        .build())
                .addField(FieldSpec.builder(ParameterizedTypeName.get(LIST, STRING), "SOURCES",
                                Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addJavadoc("The upstream sources and their versions, e.g. {@code Unicode CLDR 48.2}.\n")
                        .initializer("$T.of($L)", LIST, CodeBlock.join(dataset.sources().stream()
                                .map(s -> CodeBlock.of("$S", s.toString())).toList(), ", "))
                        .build())
                .addField(FieldSpec.builder(STRING, "LICENSE", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addJavadoc("SPDX licence expression covering the generated code and its data.\n")
                        .initializer("$S", license)
                        .build())
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
                .build());
    }

    private static <T> CodeBlock arguments(T row, List<Field<T>> fields) {
        List<CodeBlock> args = new ArrayList<>();
        for (Field<T> field : fields) {
            args.add(field.def().valueOf(row).map(v -> CodeBlock.of("$S", v)).orElse(CodeBlock.of("null")));
        }
        return CodeBlock.join(args, ", ");
    }

    /**
     * {@code name} would clash with {@link Enum#name()}, and the model's names are English (translations ship
     * separately), so the field is exposed as {@code englishName}.
     */
    static String javaName(String id) {
        if (id.equals("name")) {
            return "englishName";
        }
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char c : id.toCharArray()) {
            if (c == '_' || c == '-') {
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }

    static String constantName(String value) {
        String name = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
        return Character.isDigit(name.charAt(0)) ? "_" + name : name;
    }

    private static String javadocText(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("*/", "*&#47;");
    }

    private JavaFile javaFile(String pkg, TypeSpec type) {
        return JavaFile.builder(pkg, type)
                .addFileComment("SPDX-License-Identifier: $L\n", license)
                .addFileComment("Generated from $L. Do not edit.", source)
                .skipJavaLangImports(true)
                .build();
    }
}
