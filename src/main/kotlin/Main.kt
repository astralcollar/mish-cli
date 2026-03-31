import java.io.File
import kotlin.system.exitProcess

// ─── ANSI helpers ────────────────────────────────────────────────────────────
private const val RESET  = "\u001B[0m"
private const val BLUE   = "\u001B[44;97m"   // blue bg, bright white text  (matches dashboard)
private const val BOLD   = "\u001B[1m"
private const val DIM    = "\u001B[2m"
private const val GREEN  = "\u001B[32m"
private const val RED    = "\u001B[31m"
private const val YELLOW = "\u001B[33m"
private const val CYAN   = "\u001B[36m"

object UI {
    /** Detect terminal width (fallback 80) */
    private fun termWidth(): Int {
        return try {
            val proc = ProcessBuilder("tput", "cols").inheritIO()
                .redirectOutput(ProcessBuilder.Redirect.PIPE).start()
            proc.inputStream.bufferedReader().readLine()?.trim()?.toIntOrNull() ?: 80
        } catch (_: Exception) { 80 }
    }

    fun banner() {
        val w = termWidth()
        val title  = "  🚀  Mish CLI  v1.0"
        val pad    = " ".repeat(maxOf(0, w - title.length))
        println()
        println("$BLUE$title$pad$RESET")
        println("$DIM${"─".repeat(w)}$RESET")
    }

    fun info(label: String, value: String)    = println("  $DIM$label$RESET  $BOLD$value$RESET")
    fun step(msg: String)                      = println("  $CYAN▸$RESET $msg")
    fun ok(msg: String)                        = println("  $GREEN✔$RESET $msg")
    fun warn(msg: String)                      = println("  $YELLOW⚠$RESET $msg")
    fun error(msg: String)                     = println("  $RED✘$RESET $msg")
    fun divider()  { val w = termWidth(); println("$DIM${"─".repeat(w)}$RESET") }
    fun spacer()   = println()
}

// ─── Entry point ─────────────────────────────────────────────────────────────
fun main(args: Array<String>) {
    UI.banner()

    if (args.isEmpty()) {
        UI.error("No command specified.")
        UI.info("Usage:", "mish <command>")
        UI.info("Commands:", "run, logs")
        exitProcess(1)
    }

    val command = args[0]
    when (command) {
        "run" -> {
            val targetDir = if (args.size > 1) File(args[1]) else File(System.getProperty("user.dir"))
            runProject(targetDir)
        }
        "logs" -> {
            val targetDir = File(System.getProperty("user.dir"))
            val pkg = if (args.size > 1) args[1] else AndroidProject.getPackageName(targetDir) ?: run {
                UI.error("Could not determine package name.")
                UI.info("Usage:", "mish logs <package-name>")
                exitProcess(1)
            }
            startDashboardLoop(targetDir, pkg)
        }
        else -> {
            UI.error("Unknown command: $command")
            exitProcess(1)
        }
    }
}

fun runProject(currentDir: File) {
    UI.info("Project dir:", currentDir.absolutePath)

    // 1. Check Android Project
    if (!AndroidProject.isAndroidProject(currentDir)) {
        UI.error("Not a valid Android project directory.")
        exitProcess(1)
    }

    val packageName = AndroidProject.getPackageName(currentDir)
    if (packageName == null) {
        UI.error("Could not determine package name.")
        exitProcess(1)
    }
    UI.info("Package:", packageName)
    UI.divider()

    // 2. Check for running devices first
    val runningDevices = EmulatorManager.getRunningDevices()

    if (runningDevices.isNotEmpty()) {
        UI.ok("Found ${runningDevices.size} running device(s):")
        runningDevices.forEach { UI.info("  Device:", it) }
        UI.info("Target:", runningDevices.first())
    } else {
        UI.warn("No running devices — checking for AVDs...")

        // 3. Check Emulator
        if (!EmulatorManager.isEmulatorInstalled()) {
            UI.error("'emulator' not found. Add Android SDK emulator to PATH.")
            exitProcess(1)
        }

        // 4. List and Select Emulator
        val avds = EmulatorManager.listAvds()
        if (avds.isEmpty()) {
            UI.error("No AVDs found. Create one in Android Studio.")
            exitProcess(1)
        }

        val selectedAvd = if (avds.size == 1) {
            UI.ok("Found 1 AVD: ${avds[0]}")
            avds[0]
        } else {
            UI.ok("Found ${avds.size} AVDs")
            val selected = MenuSelector.selectFromList("Select an Android Virtual Device:", avds)
            if (selected == null) {
                UI.warn("Selection cancelled.")
                exitProcess(1)
            }
            selected
        }
        UI.info("Launching:", selectedAvd)

        // 5. Launch Emulator
        EmulatorManager.launchEmulator(selectedAvd)

        // 6. Wait for device to come online
        if (!EmulatorManager.waitForDeviceOnline()) {
            UI.error("Device failed to come online.")
            exitProcess(1)
        }
    }

    UI.divider()

    // 7. Build and Run
    if (ProjectRunner.buildAndInstall(currentDir)) {
        ProjectRunner.launchApp(packageName)
        startDashboardLoop(currentDir, packageName)
    } else {
        UI.error("Build failed. See errors above.")
        exitProcess(1)
    }
}

fun startDashboardLoop(projectDir: File, packageName: String) {
    val devices = EmulatorManager.getRunningDevices()
    if (devices.isEmpty()) {
        UI.error("No devices running to attach logs to.")
        exitProcess(1)
    }
    val deviceId = devices.first()

    while (true) {
        val action = LogcatDashboard.start(packageName, deviceId)
        when (action) {
            LogcatDashboard.Action.QUIT -> {
                UI.spacer()
                UI.ok("Dashboard closed. Goodbye!")
                UI.spacer()
                exitProcess(0)
            }
            LogcatDashboard.Action.HOT_RELOAD -> {
                UI.divider()
                UI.step("Hot Reloading...")
                if (ProjectRunner.buildAndInstall(projectDir, "installDebug")) {
                    ProjectRunner.launchApp(packageName)
                } else {
                    UI.error("Hot reload failed. Press Enter to return to logs.")
                    readlnOrNull()
                }
            }
            LogcatDashboard.Action.RELOAD_MENU -> {
                UI.divider()
                val options = listOf(
                    "1. Simple Debug (installDebug)",
                    "2. Clean Build (clean installDebug)",
                    "3. Deep Clean (clean installDebug --no-build-cache --refresh-dependencies)"
                )
                val selected = MenuSelector.selectFromList("Select Reload Type:", options)
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
