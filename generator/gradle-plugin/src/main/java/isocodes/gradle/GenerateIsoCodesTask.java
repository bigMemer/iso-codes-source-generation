package isocodes.gradle;

import isocodes.emitter.java.JavaEmitter;
import isocodes.model.IsoCodesDataset;
import isocodes.source.isocodes.IsoCodesSource;
import isocodes.source.isocodes.SalsaFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** Reads one iso-codes release into the model and emits Java sources from it. */
@CacheableTask
public abstract class GenerateIsoCodesTask extends DefaultTask {

    @Input
    public abstract Property<String> getIsoCodesVersion();

    @Input
    public abstract Property<String> getBasePackage();

    /** Where downloaded JSON files are kept between builds. */
    @Internal
    public abstract DirectoryProperty getDownloadDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        String version = getIsoCodesVersion().get();
        Path downloads = getDownloadDirectory().get().getAsFile().toPath().resolve(version);
        Path output = getOutputDirectory().get().getAsFile().toPath();
        deleteRecursively(output);
        // The only place a source meets an emitter. Swapping either side means changing this line, nothing else.
        IsoCodesDataset dataset = IsoCodesDataset.fromSource(IsoCodesSource.read(version, new SalsaFiles(version, downloads)));
        JavaEmitter.write(dataset, getBasePackage().get(), output);
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
