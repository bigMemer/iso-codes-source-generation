dependencies {
    api(project(":model"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
}

tasks.withType<Test>().configureEach {
    // RealEvidenceTest checks known examples against the committed evidence file.
    systemProperty("evidenceFile", rootProject.layout.projectDirectory.file("../history/iso3166-evidence.json").asFile.path)
}
