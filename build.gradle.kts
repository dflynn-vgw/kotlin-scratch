plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.dependency.management) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.versions) apply false
}

// Common configuration for all subprojects
subprojects {
    apply(plugin = "com.diffplug.spotless")
    // Disable bootJar for library modules (only http and wrkr should produce executable jars)
    tasks.whenTaskAdded {
        if (name == "bootJar" && project.name !in listOf("http", "wrkr")) {
            enabled = false
        }
    }
}
