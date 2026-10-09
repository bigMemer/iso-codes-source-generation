package isocodes.gradle;

import isocodes.emitter.java.EmitOptions;
import isocodes.emitter.java.JavaEmitter;
import isocodes.model.Contribution;
import isocodes.model.IsoCodesDataset;
import isocodes.model.SourceData;
import isocodes.model.SourceInfo;
import isocodes.source.history.Evidence;
import isocodes.source.history.History;
import isocodes.source.aggregate.Aggregator;
import isocodes.source.aggregate.Overrides;
import isocodes.source.aggregate.Report;
import isocodes.source.cldr.CldrFiles;
import isocodes.source.cldr.CldrSource;
import isocodes.source.isocodes.IsoCodesSource;
import isocodes.source.isocodes.SalsaFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Aggregates CLDR, iso-codes and overrides into the model, writes a report, and emits Java sources. */
@CacheableTask
public abstract class GenerateIsoCodesTask extends DefaultTask {

    /** The iso-codes release, e.g. {@code 4.20.1}: the authority. */
    @Input
    public abstract Property<String> getIsoCodesVersion();

    /** The CLDR release, e.g. {@code 48.2}: the backbone. */
    @Input
    public abstract Property<String> getCldrVersion();

    @Input
    public abstract Property<String> getDatasetVersion();

    @Input
    public abstract Property<String> getBasePackage();

    /** SPDX expression for our own contribution, combined with the sources' licences in file headers. */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getOwnLicense();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    @org.gradle.api.tasks.Optional
    public abstract RegularFileProperty getOverridesFile();

    /** History evidence written by {@code scripts/build_history.py}. Without it, there are no withdrawn entries. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    @org.gradle.api.tasks.Optional
    public abstract RegularFileProperty getHistoryFile();

    /** Where downloaded source files are kept between builds. */
    @Internal
    public abstract DirectoryProperty getDownloadDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void generate() throws IOException {
        String isoVersion = getIsoCodesVersion().get();
        String cldrVersion = getCldrVersion().get();
        Path downloads = getDownloadDirectory().get().getAsFile().toPath();
        Path output = getOutputDirectory().get().getAsFile().toPath();
        Path report = getReportFile().get().getAsFile().toPath();

        // The only place sources, aggregation and an emitter meet.
        Contribution cldr = CldrSource.read(cldrVersion,
                new CldrFiles(cldrVersion, downloads.resolve("cldr/" + cldrVersion)));
        Contribution isoCodes = IsoCodesSource.contribution(
                isoVersion,
                v -> new SalsaFiles(v, downloads.resolve("iso-codes/" + v)),
                earlierIsoCodesReleases(isoVersion));
        Overrides overrides = getOverridesFile().isPresent()
                ? Overrides.parse(Files.readString(getOverridesFile().get().getAsFile().toPath()))
                : Overrides.NONE;

        Aggregator.Result result = Aggregator.aggregate(cldr, isoCodes, overrides);
        String reportText = Report.markdown(result, overrides);
        SourceData data = result.data();
        if (getHistoryFile().isPresent() && !result.hasErrors()) {
            Evidence evidence = Evidence.parse(Files.readString(getHistoryFile().get().getAsFile().toPath()));
            History.Result withHistory = History.of(evidence, data)
                    .apply(data, new SourceInfo("iso3166-updates", evidence.changeLogCommit().substring(0, 7), "MIT"));
            data = withHistory.data();
            reportText += withHistory.markdown();
        }
        Files.createDirectories(report.getParent());
        Files.writeString(report, reportText);
        if (result.hasErrors()) {
            throw new GradleException("Aggregation needs overrides before it can generate; see " + report.toUri());
        }

        IsoCodesDataset dataset = IsoCodesDataset.fromSource(data);
        deleteRecursively(output);
        JavaEmitter.write(dataset,
                new EmitOptions(getBasePackage().get(), getDatasetVersion().get(),
                        Optional.ofNullable(getOwnLicense().getOrNull())),
                output);
        getLogger().lifecycle("Aggregation report: {}", report.toUri());
    }

    private static List<String> earlierIsoCodesReleases(String version) {
        String oldest = SalsaFiles.versionKey(IsoCodesSource.OLDEST_SUPPORTED);
        String current = SalsaFiles.versionKey(version);
        return SalsaFiles.releases().stream()
                .filter(v -> SalsaFiles.versionKey(v).compareTo(oldest) >= 0)
                .filter(v -> SalsaFiles.versionKey(v).compareTo(current) < 0)
                .toList();
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
