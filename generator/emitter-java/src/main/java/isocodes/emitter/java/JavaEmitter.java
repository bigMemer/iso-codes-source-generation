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

    /** Emits the dataset into a source directory. */
    public static void write(IsoCodesDataset dataset, EmitOptions options, Path outputDir) {
        for (JavaFile file : emit(dataset, options)) {
            try {
                file.writeTo(outputDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
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
            addLookups(builder, type, lookups, CodeBlock.of("values()"));
        } else {
            files.addAll(addTableData(builder, type, table.rows(), fields));
            addLookups(builder, type, lookups, CodeBlock.of("ALL"));
            addTableObjectMethods(builder, type, javaName(standard.primaryKey()));
        }
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

    /** Lookup fields are unique by construction: the model rejects datasets where they aren't. */
    private static <T> void addLookups(TypeSpec.Builder builder, ClassName type, List<Field<T>> lookups, CodeBlock source) {
        TypeName mapType = ParameterizedTypeName.get(MAP, STRING, type);
        CodeBlock.Builder init = CodeBlock.builder();
        for (Field<T> field : lookups) {
            String index = indexName(field);
            builder.addField(FieldSpec.builder(mapType, index, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).build());
            init.addStatement("$T<$T, $T> $N = new $T<>()", Map.class, STRING, type, index.toLowerCase(Locale.ROOT),
                    HashMap.class);
        }
        if (lookups.isEmpty()) {
            return;
        }
        init.beginControlFlow("for ($T entry : $L)", type, source);
        for (Field<T> field : lookups) {
            String local = indexName(field).toLowerCase(Locale.ROOT);
            if (field.required()) {
                init.addStatement("$N.put(entry.$N, entry)", local, field.java());
            } else {
                init.beginControlFlow("if (entry.$N != null)", field.java())
                        .addStatement("$N.put(entry.$N, entry)", local, field.java())
                        .endControlFlow();
            }
        }
        init.endControlFlow();
        for (Field<T> field : lookups) {
            String index = indexName(field);
            init.addStatement("$N = $T.copyOf($N)", index, MAP, index.toLowerCase(Locale.ROOT));

            String method = "from" + Character.toUpperCase(field.java().charAt(0)) + field.java().substring(1);
            builder.addMethod(MethodSpec.methodBuilder(method)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(ParameterizedTypeName.get(OPTIONAL, type))
                    .addParameter(STRING, field.java())
                    .addJavadoc("Finds the entry whose {@code $L} is exactly the given value.\n\n"
                            + "@param $N the value to look up (case-sensitive)\n"
                            + "@return the matching entry, or empty if there is none\n", field.def().id(), field.java())
                    .addStatement("return $T.ofNullable($N.get($N))", OPTIONAL, index, field.java())
                    .build());
        }
        builder.addStaticBlock(init.build());
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
