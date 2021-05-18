pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}


rootProject.name = "cldr-plural-rules"

include("shared")
include("plurals")
include("maker")
