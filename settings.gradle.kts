rootProject.name = "kube-of-pie-tools"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "core",
    "config",
    "imagegenerator",
    "inventorygenerator",
)