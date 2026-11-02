pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io") {
            content { includeGroup("com.github.i30mb1") }
        }
    }
}

// Shared n7 Glyph look (https://github.com/i30mb1/n7-glyph-ui).
// A checkout next to this repo is used directly; without it the JitPack artifact is downloaded.
val localGlyphUi = file("../n7-glyph-ui")
if (localGlyphUi.resolve("settings.gradle.kts").exists()) {
    includeBuild(localGlyphUi) {
        dependencySubstitution {
            substitute(module("com.github.i30mb1:n7-glyph-ui")).using(project(":n7-glyph-ui"))
        }
    }
}

rootProject.name = "Glyph Pet"
include(":app")
