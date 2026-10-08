package isocodes.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;

/** Adds a {@code generateIsoCodes} task whose output is compiled as part of the main source set. */
public class IsoCodesPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        IsoCodesExtension extension = project.getExtensions().create("isoCodes", IsoCodesExtension.class);

        var generate = project.getTasks().register("generateIsoCodes", GenerateIsoCodesTask.class, task -> {
            task.setDescription("Generates Java sources from Debian iso-codes.");
            task.getIsoCodesVersion().set(extension.getVersion());
            task.getBasePackage().set(extension.getBasePackage());
            task.getDownloadDirectory().set(project.getLayout().getBuildDirectory().dir("iso-codes-json"));
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated/sources/iso-codes"));
        });

        project.getExtensions().getByType(SourceSetContainer.class).getByName("main").getJava().srcDir(generate);
    }
}
