import java.io.File
import java.util.concurrent.TimeUnit

object ProjectRunner {
    fun buildAndInstall(projectDir: File, vararg tasks: String = arrayOf("installDebug")): Boolean {
        val gradlew = if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew"
        val importantLines = mutableListOf<String>()

        // Spinner thread: animates a single line while Gradle runs
        var building = true
        val spinnerThread = Thread {
            val frames = listOf(".", "..", "...")
            var i = 0
            while (building) {
                print("\rBuilding${frames[i % frames.size]}   ")
                System.out.flush()
                i++
                Thread.sleep(500)
            }
        }.also { it.isDaemon = true; it.start() }

        try {
            val process = ProcessBuilder(gradlew, *tasks)
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()

            // Drain output, only keep lines that look like errors or warnings
            process.inputStream.bufferedReader().forEachLine { line ->
                val lower = line.lowercase()
                if (lower.contains("error") || lower.contains("warning") ||
                    lower.contains("exception") || lower.contains("failure")) {
                    importantLines.add(line.trim())
                }
            }

            process.waitFor()
            building = false
            spinnerThread.join()

            return if (process.exitValue() == 0) {
                print("\r") // clear spinner line
                println("✅ Build successful")
                true
            } else {
                print("\r")
                println("❌ Build failed")
                importantLines.forEach { println("   $it") }
                false
            }
        } catch (e: Exception) {
            building = false
            spinnerThread.join()
            print("\r")
            println("❌ Build failed: ${e.message}")
            return false
        }
    }

    fun launchApp(packageName: String) {
        try {
            // Use monkey to find and launch the main activity — silence all output
            val process = ProcessBuilder("adb", "shell", "monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1")
                .redirectErrorStream(true)
                .start()
            // Drain silently so the process doesn't block
            Thread { process.inputStream.bufferedReader().forEachLine { } }.also { it.isDaemon = true }.start()
            process.waitFor()
        } catch (e: Exception) {
            println("  Failed to launch app: ${e.message}")
        }
    }
}
