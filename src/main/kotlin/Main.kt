import java.io.File
import kotlin.system.exitProcess

// ─── ANSI palette (shared with dashboard & MenuSelector) ─────────────────────
private const val RESET  = "\u001B[0m"
private const val BLUE   = "\u001B[44;97m"   // blue bg + bright white
private const val BOLD   = "\u001B[1m"
private const val DIM    = "\u001B[2m"
private const val GREEN  = "\u001B[32m"
private const val RED    = "\u001B[31m"
private const val CYAN   = "\u001B[36m"

// ─── UI helpers ───────────────────────────────────────────────────────────────
object UI {
    fun termWidth(): Int = try {
        val p = ProcessBuilder("tput", "cols")
            .redirectOutput(ProcessBuilder.Redirect.PIPE).start()
        p.inputStream.bufferedReader().readLine()?.trim()?.toIntOrNull() ?: 80
    } catch (_: Exception) { 80 }

    /** Fixed header bar — call once, or call again after a sub-menu to redraw. */
    fun header(packageName: String? = null) {
        val w = termWidth()
        val left  = "  \uD83D\uDE80  Mish CLI  v1.0"
        val right  = if (packageName != null) "  \u2502  Package: $packageName  " else "  "
        val mid   = " ".repeat(maxOf(0, w - left.length - right.length))
        print("\u001B[2J\u001B[H")          // clear screen, cursor to top
        println("$BLUE$left$mid$right$RESET")
        println("$DIM${"─".repeat(w)}$RESET")
        println()
    }

    fun ok(msg: String)    = println("  $GREEN✔$RESET $msg")
    fun error(msg: String) = println("  $RED✘$RESET $msg")
    fun step(msg: String)  = println("  $CYAN▸$RESET $msg")
    fun divider() { println("$DIM${"─".repeat(termWidth())}$RESET") }
    fun spacer()  = println()
}

// ─── Entry point ──────────────────────────────────────────────────────────────
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        UI.header()
        UI.error("No command specified.")
        print("  ${DIM}Usage:$RESET  $BOLD mish run$RESET  or  $BOLD mish logs$RESET\n")
        exitProcess(1)
    }

    when (val command = args[0]) {
        "run" -> {
            val targetDir = if (args.size > 1) File(args[1]) else File(System.getProperty("user.dir"))
            runProject(targetDir)
        }
        "logs" -> {
            val targetDir = File(System.getProperty("user.dir"))
            val pkg = if (args.size > 1) args[1] else AndroidProject.getPackageName(targetDir) ?: run {
                UI.header()
                UI.error("Could not determine package name. Use: mish logs <package>")
                exitProcess(1)
            }
            UI.header(pkg)
            startDashboardLoop(targetDir, pkg)
        }
        else -> {
            UI.header()
            UI.error("Unknown command: $command")
            exitProcess(1)
        }
    }
}

// ─── Main run flow ─────────────────────────────────────────────────────────────
fun runProject(currentDir: File) {
    // ── 1. Resolve package name silently ──────────────────────────────────────
    if (!AndroidProject.isAndroidProject(currentDir)) {
        UI.header()
        UI.error("Not a valid Android project directory.")
        exitProcess(1)
    }
    val packageName = AndroidProject.getPackageName(currentDir) ?: run {
        UI.header()
        UI.error("Could not determine package name.")
        exitProcess(1)
    }

    // ── 2. Paint the header (with package) once — nothing else goes above it ──
    UI.header(packageName)

    // ── 3. Silently check for running devices ─────────────────────────────────
    val deviceId: String = run {
        val running = EmulatorManager.getRunningDevices()
        if (running.isNotEmpty()) {
            UI.ok("Device: ${running.first()}")
            running.first()
        } else {
            // no device — silently check emulator / AVDs
            if (!EmulatorManager.isEmulatorInstalled()) {
                UI.error("'emulator' not found. Add Android SDK emulator to PATH.")
                exitProcess(1)
            }

            val avds = EmulatorManager.listAvds()
            if (avds.isEmpty()) {
                UI.error("No AVDs found. Create one in Android Studio.")
                exitProcess(1)
            }

            val selectedAvd = if (avds.size == 1) {
                avds[0]
            } else {
                // MenuSelector takes over full screen; when it exits we redraw our header
                val sel = MenuSelector.selectFromList("Select an Android Virtual Device:", avds)
                if (sel == null) {
                    UI.header(packageName)
                    UI.error("No AVD selected.")
                    exitProcess(1)
                }
                sel
            }

            // Redraw header after selector closes
            UI.header(packageName)
            UI.step("Launching emulator: $selectedAvd")
            EmulatorManager.launchEmulator(selectedAvd)

            val online = EmulatorManager.waitForDeviceOnline()
            // clear spinner line
            print("\r\u001B[2K")
            if (!online) {
                UI.error("Device failed to come online.")
                exitProcess(1)
            }
            EmulatorManager.getRunningDevices().firstOrNull() ?: run {
                UI.error("Could not detect device after launch.")
                exitProcess(1)
            }
        }
    }

    UI.divider()

    // ── 4. Build & install ────────────────────────────────────────────────────
    if (ProjectRunner.buildAndInstall(currentDir)) {
        ProjectRunner.launchApp(packageName)
        startDashboardLoop(currentDir, packageName)
    } else {
        UI.error("Build failed. See errors above.")
        exitProcess(1)
    }
}

// ─── Dashboard loop ───────────────────────────────────────────────────────────
fun startDashboardLoop(projectDir: File, packageName: String) {
    val devices = EmulatorManager.getRunningDevices()
    if (devices.isEmpty()) {
        UI.error("No devices running to attach logs to.")
        exitProcess(1)
    }
    val deviceId = devices.first()

    while (true) {
        when (LogcatDashboard.start(packageName, deviceId)) {
            LogcatDashboard.Action.QUIT -> {
                UI.spacer(); UI.ok("Goodbye!"); UI.spacer()
                exitProcess(0)
            }
            LogcatDashboard.Action.HOT_RELOAD -> {
                UI.header(packageName)
                UI.step("Hot Reloading...")
                if (ProjectRunner.buildAndInstall(projectDir, "installDebug")) {
                    ProjectRunner.launchApp(packageName)
                } else {
                    UI.error("Hot reload failed. Press Enter to return to logs.")
                    readlnOrNull()
                }
            }
            LogcatDashboard.Action.RELOAD_MENU -> {
                val options = listOf(
                    "1. Simple Debug",
                    "2. Clean Build",
                    "3. Deep Clean (no cache)"
                )
                val selected = MenuSelector.selectFromList("Select Reload Type:", options)

                // Redraw header after selector
                UI.header(packageName)

                if (selected != null) {
                    UI.step("Running: $selected")
                    val success = when {
                        selected.startsWith("1") -> ProjectRunner.buildAndInstall(projectDir, "installDebug")
                        selected.startsWith("2") -> ProjectRunner.buildAndInstall(projectDir, "clean", "installDebug")
                        selected.startsWith("3") -> ProjectRunner.buildAndInstall(projectDir, "clean", "installDebug", "--no-build-cache", "--refresh-dependencies")
                        else -> false
                    }
                    if (success) {
                        ProjectRunner.launchApp(packageName)
                    } else {
                        UI.error("Reload failed. Press Enter to return to logs.")
                        readlnOrNull()
                    }
                }
            }
        }
    }
}
