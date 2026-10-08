subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(21)
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.1.3"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:all")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
