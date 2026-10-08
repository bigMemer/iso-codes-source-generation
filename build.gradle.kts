import isocodes.gen.GenerateIsoCodesTask

plugins {
    java
}

val upstreamVersion = providers.gradleProperty("isoCodesVersion").get()

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

val generateIsoCodes = tasks.register<GenerateIsoCodesTask>("generateIsoCodes") {
    isoCodesVersion = upstreamVersion
    basePackage = providers.gradleProperty("basePackage")
    downloadDirectory = layout.buildDirectory.dir("iso-codes-json")
    outputDirectory = layout.buildDirectory.dir("generated/sources/iso-codes")
}

// Generated sources are compiled and tested here so a broken generator never reaches the output repo.
sourceSets.main {
    java.srcDir(generateIsoCodes)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("basePackage", providers.gradleProperty("basePackage").get())
}

val writeVersionFile = tasks.register("writeVersionFile") {
    val versionFile = layout.buildDirectory.file("iso-codes-version/iso-codes.version")
    val version = upstreamVersion // local copy so the action doesn't capture the build script
    inputs.property("isoCodesVersion", version)
    outputs.file(versionFile)
    doLast {
        versionFile.get().asFile.writeText("$version\n")
    }
}

// The exact files the iso-codes-java output repo tracks: generated sources plus the upstream version they came from.
tasks.register<Sync>("exportOutput") {
    description = "Assembles the generated files for the iso-codes-java output repo in build/output."
    from(generateIsoCodes) { into("src/main/java") }
    from(writeVersionFile)
    into(layout.buildDirectory.dir("output"))
}
