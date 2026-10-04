rootProject.name = "neon-td"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include("core", "desktop", "web")
