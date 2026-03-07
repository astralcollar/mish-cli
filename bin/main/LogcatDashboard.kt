import org.jline.terminal.TerminalBuilder
import org.jline.utils.NonBlockingReader
import java.util.concurrent.ConcurrentLinkedQueue

object LogcatDashboard {
    enum class Action {
        QUIT,
        HOT_RELOAD,
        RELOAD_MENU
    }

    private val logs = ConcurrentLinkedQueue<String>()
    @Volatile private var isPaused = false
    private val maxLogs = 1000

    fun start(packageName: String, deviceId: String): Action {
        val terminal = TerminalBuilder.builder()
            .system(true)
            .jna(true)
            .jansi(true)
            .build()

        terminal.enterRawMode()
        val writer = terminal.writer()
        val reader = terminal.reader()

        var actionToReturn = Action.QUIT
        var currentHeight = terminal.height.takeIf { it > 0 } ?: 24
        
        writer.print("\u001B[2J") // Clear screen
        writer.print("\u001B[?25l") // Hide cursor
        writer.print("\u001B[?7l")  // Disable line wrapping
        writer.print("\u001B[6;${currentHeight}r") // Set scrolling region from line 6 to bottom
        writer.print("\u001B[${currentHeight};1H") // Move cursor to bottom left
        writer.flush()

        val logManager = LogcatManager(deviceId, packageName) { line ->
            if (!isPaused) {
                logs.add(line)
                if (logs.size > maxLogs) {
                    logs.poll()
                }
                // Write log directly and scroll naturally within the ANSI scrolling region
                writer.print("\r$line\n")
                writer.flush()
            }
        }
        logManager.start()

        try {
            var isRunning = true
            var isTypingTag = false
            var currentTagInput = ""

            while (isRunning) {
                val height = terminal.height.takeIf { it > 0 } ?: 24
                val width = terminal.width.takeIf { it > 0 } ?: 80

                // If terminal shrinks/grows, update the scroll region
                if (height != currentHeight) {
                    currentHeight = height
                    writer.print("\u001B[6;${currentHeight}r")
                    writer.print("\u001B[${currentHeight};1H")
                }

                // --- Draw Fixed Header ---
                writer.print("\u001B[s") // Save cursor (which is at the bottom streaming logs)
                writer.print("\u001B[1;1H") // Move to top-left
                
                writer.print("\u001B[44;37m")
                writer.print(" ".repeat(width) + "\r")
                writer.println(" \uD83D\uDE80 Mish Live Dashboard  │  Device: $deviceId  │  App: $packageName\r")
                
                writer.print(" ".repeat(width) + "\r")
                val statusText = if (isPaused) "⏸ PAUSED" else "● LIVE"
                val pidText = if (logManager.currentLevel != null) "WAITING FOR PID..." else "" // Just indicating it might be waiting
                val tagText = if (currentTagInput.isEmpty()) logManager.currentTag else currentTagInput + "█"
                writer.println(" Level: [${logManager.currentLevel.name}]  │  Tag: [$tagText]  │  Status: $statusText\r")
                
                writer.print(" ".repeat(width) + "\r")
                writer.println(" [l] Level  [t] Tag  [p] Pause  [c] Clear  [h] Hot Reload  [r] Reload Menu  [q] Quit \r")
                writer.print("\u001B[0m")
                writer.println("═".repeat(width) + "\r")
                
                writer.print("\u001B[u") // Restore cursor back to the logging area
                writer.flush()

                // --- Read Input ---
                var key = -2
                if (reader is NonBlockingReader) {
                    key = reader.read(100)
                } else {
                    if (reader.ready()) {
                         key = reader.read()
                    } else {
                         Thread.sleep(100)
                    }
                }

                if (key > 0) {
                    val c = key.toChar()
                    
                    if (isTypingTag) {
                        when (key) {
                            10, 13 -> {
                                logManager.currentTag = if (currentTagInput.isEmpty()) "*" else currentTagInput
                                isTypingTag = false
                                currentTagInput = ""
                            }
                            27 -> {
                                isTypingTag = false
                                currentTagInput = ""
                            }
                            127, 8 -> {
                                if (currentTagInput.isNotEmpty()) {
                                    currentTagInput = currentTagInput.dropLast(1)
                                }
                            }
                            else -> {
                                if (c.isLetterOrDigit() || c == '_' || c == '-') {
                                    currentTagInput += c
                                }
                            }
                        }
                    } else {
                        when (c) {
                            'q', 'Q' -> { isRunning = false; actionToReturn = Action.QUIT }
                            'h', 'H' -> { isRunning = false; actionToReturn = Action.HOT_RELOAD }
                            'r', 'R' -> { isRunning = false; actionToReturn = Action.RELOAD_MENU }
                            'l', 'L' -> { logManager.currentLevel = logManager.currentLevel.next() }
                            'p', 'P' -> { isPaused = !isPaused }
                            'c', 'C' -> { 
                                logs.clear() 
                                writer.print("\u001B[s") // save
                                writer.print("\u001B[6;1H") // goto line 6
                                writer.print("\u001B[J") // clear to end of screen
                                writer.print("\u001B[${currentHeight};1H") // cursor to bottom
                                writer.print("\u001B[u") // restore
                                // Actually, just moving the cursor to bottom is enough for scroll to resume nicely
                                writer.print("\u001B[${currentHeight};1H")
                                writer.flush()
                            }
                            't', 'T' -> { isTypingTag = true; currentTagInput = "" }
                            3.toChar() -> { isRunning = false; actionToReturn = Action.QUIT }
                        }
                    }
                }
            }

        } finally {
            logManager.stop()
            writer.print("\u001B[?25h") // Show cursor
            writer.print("\u001B[?7h")  // Enable line wrapping
            writer.print("\u001B[1;${terminal.height}r") // Reset scrolling region to full screen
            writer.print("\u001B[0m\u001B[2J\u001B[H")
            writer.flush()
            terminal.close()
        }

        return actionToReturn
    }
}
