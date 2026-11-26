pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "TVGuard"
include(":app")    // ứng dụng cài trên TV
include(":phone")  // ứng dụng cài trên điện thoại Android của người lớn
