package isocodes.emitter.java;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import isocodes.model.DateRange;
import isocodes.model.FieldDef;
import isocodes.model.Holding;
import isocodes.model.Lifecycle;
import isocodes.model.IsoCodesDataset;
import isocodes.model.StandardDef;
import isocodes.model.Table;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
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

    private static final String ACTIVE_ONLY = "ALL_INCLUDING_WITHDRAWN.stream().filter(e -> !e.withdrawn).toList()";

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
        addLifecycleMembers(builder, constructor);
        builder.addMethod(constructor.build());

        List<JavaFile> files = new ArrayList<>();
        if (target.kind() == JavaTarget.Kind.ENUM) {
            addEnumConstants(builder, table, fields);
            addEnumAll(builder, type, javaName(standard.primaryKey()));
        } else {
            files.addAll(addTableData(builder, type, table, fields));
            addTableObjectMethods(builder, type, javaName(standard.primaryKey()));
        }
        addAllAccessors(builder, type, target.kind() == JavaTarget.Kind.ENUM);
        addLookups(builder, type, standard, lookups);
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
            TypeSpec.Builder constantSpec = TypeSpec.anonymousClassBuilder(arguments(table, row, fields))
                    .addJavadoc("$L.\n", javadocText(name.valueOf(row).orElse(constant)));
            table.lifecycle(row).filter(Lifecycle::isWithdrawn).ifPresent(lifecycle -> constantSpec
                    .addJavadoc("\n@deprecated $L\n", withdrawalText(lifecycle.withdrawn().orElseThrow()))
                    .addAnnotation(AnnotationSpec.builder(Deprecated.class).addMember("forRemoval", "false").build()));
            builder.addEnumConstant(constant, constantSpec.build());
        }
    }

    private <T> List<JavaFile> addTableData(TypeSpec.Builder builder, ClassName type, Table<T> table, List<Field<T>> fields) {
        List<T> rows = table.rows();
        TypeName listType = ParameterizedTypeName.get(LIST, type);
        builder.addField(FieldSpec.builder(listType, "ALL_INCLUDING_WITHDRAWN", Modifier.PRIVATE, Modifier.STATIC,
                Modifier.FINAL).build());

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
                addTo.addStatement("out.add(new $T($L))", type, arguments(table, row, fields));
            }
            holders.add(javaFile(type.packageName(), TypeSpec.classBuilder(holder)
                    .addModifiers(Modifier.FINAL)
                    .addJavadoc("Entries $L to $L of {@link $T}.\n", start, end - 1, type)
                    .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
                    .addMethod(addTo.build())
                    .build()));
            init.addStatement("$T.addTo(list)", holder);
        }
        init.addStatement("ALL_INCLUDING_WITHDRAWN = $T.copyOf(list)", LIST);
        init.addStatement("ALL = $L", ACTIVE_ONLY);
        builder.addStaticBlock(init.build());
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

    /** {@code all()} (active entries) and {@code allIncludingWithdrawn()}, for enums and table classes alike. */
    private static void addAllAccessors(TypeSpec.Builder builder, ClassName type, boolean initializeInline) {
        TypeName listType = ParameterizedTypeName.get(LIST, type);
        FieldSpec.Builder all = FieldSpec.builder(listType, "ALL", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
        if (initializeInline) {
            all.initializer(ACTIVE_ONLY);
        }
        builder.addField(all.build())
                .addMethod(MethodSpec.methodBuilder("all")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(listType)
                        .addJavadoc("Returns every active entry, in upstream order.\n\n@return an unmodifiable list\n")
                        .addStatement("return ALL")
                        .build())
                .addMethod(MethodSpec.methodBuilder("allIncludingWithdrawn")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(listType)
                        .addJavadoc("Returns every entry: active ones in upstream order, then withdrawn ones.\n\n"
                                + "@return an unmodifiable list\n")
                        .addStatement("return ALL_INCLUDING_WITHDRAWN")
                        .build());
    }

    /** Lifecycle fields, constructor parameters and accessors (output spec §5.1). */
    private static void addLifecycleMembers(TypeSpec.Builder builder, MethodSpec.Builder constructor) {
        builder.addField(boolean.class, "withdrawn", Modifier.PRIVATE, Modifier.FINAL);
        constructor.addParameter(boolean.class, "withdrawn").addStatement("this.withdrawn = withdrawn");
        for (String f : List.of("assignedEarliest", "assignedLatest", "withdrawnEarliest", "withdrawnLatest",
                "recordedSince", "timeline")) {
            builder.addField(STRING, f, Modifier.PRIVATE, Modifier.FINAL);
            constructor.addParameter(STRING, f).addStatement("this.$N = $N", f, f);
        }
        TypeName optionalDate = ParameterizedTypeName.get(OPTIONAL, ClassName.get(LocalDate.class));
        builder.addMethod(MethodSpec.methodBuilder("isWithdrawn")
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addJavadoc("Whether ISO has withdrawn this entry's code.\n\n@return true if withdrawn\n")
                .addStatement("return withdrawn")
                .build());
        String[][] dates = {
                {"assignedEarliest", "The earliest day ISO could have assigned the code to this entry"},
                {"assignedLatest", "The latest day by which ISO had assigned the code to this entry"},
                {"withdrawnEarliest", "The earliest day ISO could have withdrawn the code"},
                {"withdrawnLatest", "The latest day by which ISO had withdrawn the code"}};
        for (String[] d : dates) {
            builder.addMethod(MethodSpec.methodBuilder(d[0])
                    .addModifiers(Modifier.PUBLIC)
                    .returns(optionalDate)
                    .addJavadoc("$L.\n\n@return the day, or empty if unbounded or not applicable\n", d[1])
                    .addStatement("return $N == null ? $T.empty() : $T.of($T.parse($N))", d[0], OPTIONAL, OPTIONAL,
                            LocalDate.class, d[0])
                    .build());
        }
        builder.addMethod(MethodSpec.methodBuilder("recordedSince")
                .addModifiers(Modifier.PUBLIC)
                .returns(LocalDate.class)
                .addJavadoc("The earliest date from which the library's sources account for this code's status.\n\n"
                        + "@return the date\n")
                .addStatement("return $T.parse(recordedSince)", LocalDate.class)
                .build());
    }

    private static String withdrawalText(DateRange range) {
        if (range.isExact()) {
            return "Withdrawn by ISO on " + range.earliest().get() + ".";
        }
        if (range.earliest().isPresent() && range.latest().isPresent()
                && range.earliest().get().getYear() == range.latest().get().getYear()
                && range.earliest().get().getDayOfYear() == 1 && range.latest().get().getMonthValue() == 12
                && range.latest().get().getDayOfMonth() == 31) {
            return "Withdrawn by ISO in " + range.earliest().get().getYear() + ".";
        }
        return range.latest().map(d -> "Withdrawn by ISO by " + d + ".").orElse("Withdrawn by ISO (date unknown).");
    }

    /** Enums also return the canonical code from {@code toString()}. */
    private static void addEnumAll(TypeSpec.Builder builder, ClassName type, String key) {
        TypeName listType = ParameterizedTypeName.get(LIST, type);
        builder.addField(FieldSpec.builder(listType, "ALL_INCLUDING_WITHDRAWN", Modifier.PRIVATE, Modifier.STATIC,
                                Modifier.FINAL)
                        .initializer("$T.of(values())", LIST)
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
     * without a {@code Strictness}; for the primary code field also with a written-at date and history policy. All
     * share one {@code Lookup}, which implements the spec's matching algorithm and history checks.
     */
    private <T> void addLookups(TypeSpec.Builder builder, ClassName type, StandardDef<T> standard, List<Field<T>> lookups) {
        ClassName lookup = ClassName.get(basePackage + ".internal", "Lookup");
        ClassName strictness = ClassName.get(basePackage, "Strictness");
        ClassName match = ClassName.get(basePackage, "Match");
        ClassName validation = ClassName.get(basePackage, "Validation");
        ClassName unknown = ClassName.get(basePackage, "UnknownCodeException");
        ClassName meaningChanged = ClassName.get(basePackage, "MeaningChangedException");
        ClassName policy = ClassName.get(basePackage, "HistoryPolicy");
        TypeName matchOfT = ParameterizedTypeName.get(match, type);
        TypeName optionalT = ParameterizedTypeName.get(OPTIONAL, type);
        TypeName optionalMatch = ParameterizedTypeName.get(OPTIONAL, matchOfT);
        TypeName validationOfT = ParameterizedTypeName.get(validation, type);
        TypeName date = ClassName.get(LocalDate.class);

        CodeBlock.Builder init = CodeBlock.builder();
        for (Field<T> field : lookups) {
            String index = indexName(field);
            String active = index.toLowerCase(Locale.ROOT) + "Active";
            String withdrawn = index.toLowerCase(Locale.ROOT) + "Withdrawn";
            boolean primary = field.def().id().equals(standard.primaryKey());
            DateRange introduced = standard.introduced().getOrDefault(field.def().id(), DateRange.UNKNOWN);
            builder.addField(FieldSpec.builder(ParameterizedTypeName.get(lookup, type), index,
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).build());
            init.addStatement("$T<$T, $T> $N = new $T<>()", Map.class, STRING, type, active, HashMap.class);
            init.addStatement("$T<$T, $T> $N = new $T<>()", Map.class, STRING, type, withdrawn, HashMap.class);
            init.beginControlFlow("for ($T entry : ALL_INCLUDING_WITHDRAWN)", type);
            if (!field.required()) {
                init.beginControlFlow("if (entry.$N == null)", field.java()).addStatement("continue").endControlFlow();
            }
            init.beginControlFlow("if (!entry.withdrawn)")
                    .addStatement("$N.put(entry.$N, entry)", active, field.java())
                    .nextControlFlow("else")
                    // Where withdrawn entries share a value, the most recently withdrawn wins (spec §6.4 step 7).
                    .addStatement("$N.merge(entry.$N, entry, (a, b) -> $T.compare(a.withdrawnLatest, b.withdrawnLatest, "
                            + "$T.nullsFirst($T.naturalOrder())) >= 0 ? a : b)", withdrawn, field.java(),
                            java.util.Objects.class, java.util.Comparator.class, java.util.Comparator.class)
                    .endControlFlow();
            init.endControlFlow();
            CodeBlock history = primary
                    ? CodeBlock.of("new $T.History<>() {\n"
                            + "  @Override public $T timeline($T e) { return e.timeline; }\n"
                            + "  @Override public $T recordedSince($T e) { return e.recordedSince(); }\n}",
                            lookup, STRING, type, date, type)
                    : CodeBlock.of("null");
            init.addStatement("$N = new $T<>($S, $S, $L, $N, $N, $LL, $LL, $L)", index, lookup, standard.id(),
                    field.def().id(), field.def().id().equals("numeric"), active, withdrawn,
                    introduced.earliest().map(LocalDate::toEpochDay).orElse(Long.MIN_VALUE),
                    introduced.latest().map(LocalDate::toEpochDay).orElse(Long.MIN_VALUE), history);

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

            if (primary) {
                addHistoryMethods(builder, index, p, suffix, id, type, optionalT, optionalMatch, matchOfT, validationOfT,
                        strictness, policy, unknown, meaningChanged, validation, match);
            }
        }
        builder.addStaticBlock(init.build());
    }

    /** The written-at overloads of every operation, for the primary code field (spec §5.3, §6.7). */
    private void addHistoryMethods(TypeSpec.Builder builder, String index, String p, String suffix, String id,
            ClassName type, TypeName optionalT, TypeName optionalMatch, TypeName matchOfT, TypeName validationOfT,
            ClassName strictness, ClassName policy, ClassName unknown, ClassName meaningChanged, ClassName validation,
            ClassName match) {
        ClassName lookup = ClassName.get(basePackage + ".internal", "Lookup");
        String doc = "@param $N the code to look up\n@param strictness which relaxations to allow\n"
                + "@param writtenAt when the value was written\n";
        String policyDoc = "@param history when the history check passes\n";
        String nullDoc = "@throws NullPointerException if an argument is null\n";
        String[][] ops = {{"from", "Detailed"}, {"parse", "Detailed"}, {"isValid", "Detailed"}};
        for (String[] op : ops) {
            for (boolean detailed : new boolean[] {false, true}) {
                String name = op[0] + suffix + (detailed ? op[1] : "");
                TypeName returns = switch (op[0]) {
                    case "from" -> detailed ? optionalMatch : optionalT;
                    case "parse" -> detailed ? matchOfT : type;
                    default -> detailed ? validationOfT : TypeName.BOOLEAN;
                };
                String summary = "As {@link #" + op[0] + suffix + (detailed ? op[1] : "") + "(String, Strictness)}, also "
                        + "checking what the code meant on the date it was written (default {@code HistoryPolicy.PESSIMISTIC}).";
                builder.addMethod(method(name, returns, p, true)
                        .addParameter(LocalDate.class, "writtenAt")
                        .addJavadoc("$L\n\n" + doc + "@return as the overload without a date\n" + nullDoc, summary, p)
                        .addStatement("return $N($N, strictness, writtenAt, $T.PESSIMISTIC)", name, p, policy)
                        .build());
                MethodSpec.Builder full = method(name, returns, p, true)
                        .addParameter(LocalDate.class, "writtenAt")
                        .addParameter(policy, "history")
                        .addStatement("$T m = $N.match($N, strictness, writtenAt, history)", optionalMatch, index, p);
                String throwsDoc = "";
                switch (op[0]) {
                    case "from" -> {
                        if (detailed) {
                            full.addStatement("return m");
                        } else {
                            full.addStatement("return m.filter($T::passes).map($T::entry)", lookup, match);
                        }
                    }
                    case "parse" -> {
                        full.addStatement("$T found = m.orElseThrow(() -> $N.unknown($N, strictness))", matchOfT, index, p);
                        if (detailed) {
                            full.addStatement("return found");
                            throwsDoc = "@throws " + unknown.canonicalName() + " if nothing matches today\n";
                        } else {
                            full.beginControlFlow("if (!$T.passes(found))", lookup)
                                    .addStatement("throw $N.meaningChanged($N, strictness, found)", index, p)
                                    .endControlFlow()
                                    .addStatement("return found.entry()");
                            throwsDoc = "@throws " + unknown.canonicalName() + " if nothing matches today\n@throws "
                                    + meaningChanged.canonicalName() + " if the history check fails\n";
                        }
                    }
                    default -> {
                        if (detailed) {
                            full.addStatement("return new $T<>(m.isPresent() && $T.passes(m.get()), m)", validation, lookup);
                        } else {
                            full.addStatement("return m.isPresent() && $T.passes(m.get())", lookup);
                        }
                    }
                }
                String returnDoc = switch (op[0]) {
                    case "from" -> detailed ? "the match with its history check, or empty if nothing matches today"
                            : "the entry, or empty if nothing matches or the history check fails";
                    case "parse" -> detailed ? "the match with its history check" : "the entry";
                    default -> detailed ? "the validation result" : "true if an entry matches and the history check passes";
                };
                builder.addMethod(full.addJavadoc("Looks up the code and checks what it meant on {@code writtenAt} "
                        + "(spec §6.7).\n\n" + doc + policyDoc + "@return $L\n" + throwsDoc + nullDoc, p, returnDoc)
                        .build());
            }
        }
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

    private static <T> CodeBlock arguments(Table<T> table, T row, List<Field<T>> fields) {
        List<CodeBlock> args = new ArrayList<>();
        for (Field<T> field : fields) {
            args.add(field.def().valueOf(row).map(v -> CodeBlock.of("$S", v)).orElse(CodeBlock.of("null")));
        }
        Lifecycle lifecycle = table.lifecycle(row).orElseGet(() -> defaultLifecycle(table.standard()));
        Holding current = lifecycle.current();
        args.add(CodeBlock.of("$L", lifecycle.isWithdrawn()));
        args.add(date(current.assigned().earliest()));
        args.add(date(current.assigned().latest()));
        args.add(date(current.withdrawn().flatMap(DateRange::earliest)));
        args.add(date(current.withdrawn().flatMap(DateRange::latest)));
        args.add(CodeBlock.of("$S", lifecycle.recordedSince()));
        args.add(CodeBlock.of("$S", timeline(lifecycle)));
        return CodeBlock.join(args, ", ");
    }

    /** For data without history: active, nothing known about dates. */
    private static Lifecycle defaultLifecycle(StandardDef<?> standard) {
        LocalDate introduced = standard.introduced().get(standard.primaryKey()).earliest().orElseThrow();
        return new Lifecycle(List.of(new Holding("", DateRange.UNKNOWN, Optional.empty())), introduced);
    }

    private static CodeBlock date(Optional<LocalDate> day) {
        return day.map(d -> CodeBlock.of("$S", d.toString())).orElse(CodeBlock.of("null"));
    }

    /** The encoding {@code internal.Timeline} parses. */
    private static String timeline(Lifecycle lifecycle) {
        String own = lifecycle.current().holder();
        List<String> holdings = new ArrayList<>();
        for (Holding h : lifecycle.holdings()) {
            holdings.add(String.join(",",
                    h.holder().equals(own) ? "S" : "O",
                    h.assigned().earliest().map(Object::toString).orElse(""),
                    h.assigned().latest().map(Object::toString).orElse(""),
                    h.withdrawn().flatMap(DateRange::earliest).map(Object::toString).orElse(""),
                    h.withdrawn().flatMap(DateRange::latest).map(Object::toString).orElse(""),
                    h.withdrawn().isPresent() ? "W" : "-"));
        }
        return String.join("|", holdings);
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
