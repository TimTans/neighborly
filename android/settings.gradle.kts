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
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                username = "mapbox"
                password = (rootProject.projectDir.resolve(".env").let { if (it.exists()) it.readLines() else emptyList() })
                    .firstOrNull { it.startsWith("MAPBOX_DOWNLOADS_TOKEN=") }
                    ?.substringAfter("=")
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN")
                    ?: ""
            }
        }
    }
}

rootProject.name = "android"
include(":app")
