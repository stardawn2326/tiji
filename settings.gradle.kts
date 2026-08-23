pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Tiji"
include(":app")
include(":paddleocr")
project(":paddleocr").projectDir = file("tools/paddleocr-upstream-20260814/deploy/ppocr-android/ppocr-sdk")
