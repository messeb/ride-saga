pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://packages.confluent.io/maven/") {
            content { includeGroup("io.confluent") }
        }
    }
}

rootProject.name = "ride-saga"

include(
    "contracts",
    "common",
    "services:booking-service",
    "services:driver-matching-service",
    "services:payment-service",
    "services:notification-service",
)
