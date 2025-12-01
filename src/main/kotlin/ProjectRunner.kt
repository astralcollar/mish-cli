import java.io.File
import java.util.concurrent.TimeUnit

object ProjectRunner {
    fun buildAndInstall(projectDir: File): Boolean {
        println("Building and installing app...")
        val gradlew = if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew"
        
        try {
            println("Running: $gradlew installDebug")
            val process = ProcessBuilder(gradlew, "installDebug")
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            
            process.inputStream.bufferedReader().forEachLine { println("[Gradle]: $it") }
            
            process.waitFor()
            return process.exitValue() == 0
        } catch (e: Exception) {
            println("Build failed: ${e.message}")
            return false
        }
    }

    fun launchApp(packageName: String) {
        println("Launching app: $packageName...")
        try {
            // Use monkey to find and launch the main activity
            val process = ProcessBuilder("adb", "shell", "monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1")
                .inheritIO()
                .start()
            
            process.waitFor()
        } catch (e: Exception) {
            println("Failed to launch app: ${e.message}")
        }
    }
}
