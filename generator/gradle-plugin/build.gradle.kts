plugins {
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":source-isocodes"))
    implementation(project(":emitter-java"))
}

gradlePlugin {
    plugins {
        create("isoCodes") {
            id = "isocodes.generator"
            implementationClass = "isocodes.gradle.IsoCodesPlugin"
        }
    }
}
