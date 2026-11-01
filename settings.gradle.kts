rootProject.name = "CloudStreamRepo"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}

// Automatically include all plugin subdirectories that contain build.gradle.kts
File(rootDir, ".").listFiles()?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }?.forEach { dir ->
    include(dir.name)
}
