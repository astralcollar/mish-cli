import java.io.File

object AndroidProject {
    fun isAndroidProject(path: File): Boolean {
        // Check for typical Android project indicators
        val hasGradlew = File(path, "gradlew").exists()
        val hasAppDir = File(path, "app").exists()
        val hasBuildGradle = File(path, "build.gradle.kts").exists() || File(path, "build.gradle").exists()
        
        return hasGradlew && hasAppDir && hasBuildGradle
    }

    fun getPackageName(path: File): String? {
        // Try to parse from app/build.gradle or app/build.gradle.kts
        val appDir = File(path, "app")
        val buildGradleKts = File(appDir, "build.gradle.kts")
        val buildGradle = File(appDir, "build.gradle")

        if (buildGradleKts.exists()) {
            val content = buildGradleKts.readText()
            // Simple regex to find applicationId
            val match = Regex("applicationId\\s*=\\s*\"([^\"]+)\"").find(content)
            if (match != null) return match.groupValues[1]
        }

        if (buildGradle.exists()) {
            val content = buildGradle.readText()
            val match = Regex("applicationId\\s*[\"']([^\"']+)[\"']").find(content)
            if (match != null) return match.groupValues[1]
        }
        
        // Fallback: Try parsing AndroidManifest.xml
        val manifest = File(appDir, "src/main/AndroidManifest.xml")
        if (manifest.exists()) {
             val content = manifest.readText()
             val match = Regex("package=\"([^\"]+)\"").find(content)
             if (match != null) return match.groupValues[1]
        }

        return null
    }
}
