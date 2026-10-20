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

rootProject.name = "AutoSkip"
include(":app")      // 실제 도우미 앱
include(":mockhost") // 테스트용 모의 호스트 앱 + 모의 광고 Activity
