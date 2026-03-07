import java.io.BufferedReader
import java.io.InputStreamReader

class LogcatManager(
    private val deviceId: String,
    private val packageName: String,
    private val onLogLine: (String) -> Unit
) {
    private var process: Process? = null
    private var isRunning = false
    @Volatile private var currentPid: String? = null

    var currentLevel: LogLevel = LogLevel.VERBOSE
    var currentTag: String = "*"

    enum class LogLevel(val charCode: Char, val weight: Int) {
        VERBOSE('V', 0),
        DEBUG('D', 1),
        INFO('I', 2),
        WARN('W', 3),
        ERROR('E', 4),
        FATAL('F', 5);

        companion object {
            fun fromChar(c: Char): LogLevel {
                return values().find { it.charCode == c } ?: VERBOSE
            }
        }
        
        fun next(): LogLevel {
            val nextOrdinal = (this.ordinal + 1) % values().size
            return values()[nextOrdinal]
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        
        Thread {
            try {
                // Clear old logs immediately
                ProcessBuilder("adb", "-s", deviceId, "logcat", "-c").start().waitFor()

                process = ProcessBuilder("adb", "-s", deviceId, "logcat", "-v", "threadtime", "*:V")
                    .redirectErrorStream(true)
                    .start()

                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                var line: String? = null
                while (isRunning && reader.readLine().also { line = it } != null) {
                    if (line != null) {
                        processLine(line!!)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    onLogLine("Logcat error: ${e.message}")
                }
            }
        }.start()

        // Background thread to constantly poll and update the PID
        Thread {
            while (isRunning) {
                try {
                    val p = ProcessBuilder("adb", "-s", deviceId, "shell", "pidof", packageName).start()
                    val pid = p.inputStream.bufferedReader().readText().trim()
                    if (pid.isNotEmpty() && pid.all { it.isDigit() }) {
                        currentPid = pid
                    } else if (pid.contains(" ")) {
                        // Sometimes pidof returns multiple PIDs separated by space, take the first one
                        currentPid = pid.split(" ").firstOrNull { it.all { c -> c.isDigit() } }
                    } else {
                        currentPid = null // App is dead or not launched yet
                    }
                } catch (e: Exception) {}
                Thread.sleep(1000)
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        process?.destroy()
        process = null
    }

    private fun processLine(line: String) {
        val activePid = currentPid ?: return // STRICLY ignore logs until we have the app PID
        
        val match = Regex("""^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+(\d+)\s+\d+\s+([VDIWEF])\s+(.*?):\s*(.*)$""").find(line)
        if (match != null) {
            val logPid = match.groupValues[1]
            if (logPid != activePid) return // Only show logs from our app's PID
            
            val levelChar = match.groupValues[2][0]
            val tag = match.groupValues[3].trim()
            val level = LogLevel.fromChar(levelChar)

            if (level.weight < currentLevel.weight) return
            if (currentTag != "*" && !tag.contains(currentTag, ignoreCase = true)) return
            
            val colorCode = when (level) {
                LogLevel.VERBOSE -> "\u001B[90m"
                LogLevel.DEBUG -> "\u001B[36m"
                LogLevel.INFO -> "\u001B[32m"
                LogLevel.WARN -> "\u001B[33m"
                LogLevel.ERROR -> "\u001B[31m"
                LogLevel.FATAL -> "\u001B[35m"
            }
            val resetCode = "\u001B[0m"
            onLogLine("$colorCode$line$resetCode")
        } else {
            if (currentLevel == LogLevel.VERBOSE) {
                onLogLine(line)
            }
        }
    }
}
