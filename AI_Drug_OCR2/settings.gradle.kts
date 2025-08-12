// settings.gradle.kts (位於專案根目錄)
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

// *** 專案名稱維持 "AI_Drug_OCR_App" ***
rootProject.name = "AI_Drug_OCR_App"
include(":app")







