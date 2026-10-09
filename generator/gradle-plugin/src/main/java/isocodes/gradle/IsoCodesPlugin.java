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
            task.setDescription("Aggregates CLDR, iso-codes and overrides, and generates Java sources.");
            task.getIsoCodesVersion().set(extension.getVersion());
            task.getCldrVersion().set(extension.getCldrVersion());
            task.getDatasetVersion().set(extension.getDatasetVersion());
            task.getBasePackage().set(extension.getBasePackage());
            task.getOwnLicense().set(extension.getOwnLicense());
            task.getOverridesFile().set(extension.getOverridesFile());
            task.getHistoryFile().set(extension.getHistoryFile());
            task.getReportFile().set(project.getLayout().getBuildDirectory().file("reports/iso-codes/aggregation.md"));
            task.getDownloadDirectory().set(project.getLayout().getBuildDirectory().dir("downloads"));
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated/sources/iso-codes"));
        });

        project.getExtensions().getByType(SourceSetContainer.class).getByName("main").getJava()
                .srcDir(generate.flatMap(GenerateIsoCodesTask::getOutputDirectory));
    }
}
