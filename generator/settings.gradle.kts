dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "generator"

// The dependency graph enforces the seams: sources and emitters only ever see the model, never each other.
include("model")            // our intermediate representation; depends on nothing
include("source-isocodes")  // Debian iso-codes JSON -> model
include("source-cldr")      // Unicode CLDR XML -> model contributions
include("source-aggregate") // combines contributions and overrides -> model
include("source-history")   // history evidence -> lifecycles and withdrawn entries
include("emitter-java")     // model -> Java sources
include("gradle-plugin")    // wires a source to an emitter inside a Gradle build
