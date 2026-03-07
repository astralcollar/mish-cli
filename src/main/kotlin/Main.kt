import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    println("--- Mish CLI v1.0 ---")
    
    if (args.isEmpty()) {
        println("Usage: mish <command>")
        println("Commands: run")
        exitProcess(1)
    }

    val command = args[0]
    if (command == "run") {
        val targetDir = if (args.size > 1) File(args[1]) else File(System.getProperty("user.dir"))
        runProject(targetDir)
    } else if (command == "logs") {
        val targetDir = File(System.getProperty("user.dir"))
        val pkg = if (args.size > 1) args[1] else AndroidProject.getPackageName(targetDir) ?: run {
            println("Usage: mish logs <package-name>")
            exitProcess(1)
        }
        startDashboardLoop(targetDir, pkg)
    } else {
        println("Unknown command: $command")
        exitProcess(1)
    }
}

fun runProject(currentDir: File) {
    println("Checking project in: ${currentDir.absolutePath}")

    // 1. Check Android Project
    if (!AndroidProject.isAndroidProject(currentDir)) {
        println("Error: Current directory is not a valid Android project.")
        exitProcess(1)
    }
    
    val packageName = AndroidProject.getPackageName(currentDir)
    if (packageName == null) {
        println("Error: Could not determine package name.")
        exitProcess(1)
    }
    println("Found Android project: $packageName")

    // 2. Check for running devices first
    val runningDevices = EmulatorManager.getRunningDevices()
    
    if (runningDevices.isNotEmpty()) {
        println("Found ${runningDevices.size} running device(s):")
        runningDevices.forEach { println("  - $it") }
        println("Using running device: ${runningDevices.first()}")
    } else {
        println("No running devices found. Checking for available emulators...")
        
        // 3. Check Emulator
        if (!EmulatorManager.isEmulatorInstalled()) {
            println("Error: 'emulator' command not found. Please install Android SDK and add emulator to PATH.")
            exitProcess(1)
        }

        // 4. List and Select Emulator
        val avds = EmulatorManager.listAvds()
        if (avds.isEmpty()) {
            println("Error: No AVDs found. Please create an AVD in Android Studio.")
            exitProcess(1)
        }
        
        val selectedAvd = if (avds.size == 1) {
            println("Found 1 AVD: ${avds[0]}")
            avds[0]
        } else {
            println("Found ${avds.size} AVDs")
            val selected = MenuSelector.selectFromList("Select an Android Virtual Device:", avds)
            if (selected == null) {
                println("Selection cancelled.")
                exitProcess(1)
            }
            selected
        }
        println("Selected emulator: $selectedAvd")

        // 5. Launch Emulator
        EmulatorManager.launchEmulator(selectedAvd)
        
        // 6. Wait for device to come online
        if (!EmulatorManager.waitForDeviceOnline()) {
            println("Error: Device failed to come online.")
            exitProcess(1)
        }
    }

    // 7. Build and Run
    if (ProjectRunner.buildAndInstall(currentDir)) {
        ProjectRunner.launchApp(packageName)
        startDashboardLoop(currentDir, packageName)
    } else {
        println("Error: Build failed.")
        exitProcess(1)
    }
}

fun startDashboardLoop(projectDir: File, packageName: String) {
    val devices = EmulatorManager.getRunningDevices()
    if (devices.isEmpty()) {
        println("Error: No devices running to attach logs to.")
        exitProcess(1)
    }
    val deviceId = devices.first()
    
    while (true) {
        val action = LogcatDashboard.start(packageName, deviceId)
        when (action) {
            LogcatDashboard.Action.QUIT -> {
                println("Exiting dashboard...")
                exitProcess(0)
            }
            LogcatDashboard.Action.HOT_RELOAD -> {
                println("--- Hot Reloading ---")
                if (ProjectRunner.buildAndInstall(projectDir, "installDebug")) {
                    ProjectRunner.launchApp(packageName)
                } else {
                    println("Hot reload failed. Press enter to return to logs.")
                    readlnOrNull()
                }
            }
            LogcatDashboard.Action.RELOAD_MENU -> {
                println("--- Reload Menu ---")
                val options = listOf(
                    "1. Simple Debug (installDebug)", 
                    "2. Clean Build (clean installDebug)", 
                    "3. Deep Clean (clean installDebug --no-build-cache --refresh-dependencies)"
                )
                val selected = MenuSelector.selectFromList("Select Reload Type:", options)
                if (selected != null) {
                    val success = when {
                        selected.startsWith("1") -> ProjectRunner.buildAndInstall(projectDir, "installDebug")
                        selected.startsWith("2") -> ProjectRunner.buildAndInstall(projectDir, "clean", "installDebug")
                        selected.startsWith("3") -> ProjectRunner.buildAndInstall(projectDir, "clean", "installDebug", "--no-build-cache", "--refresh-dependencies")
                        else -> false
                    }
                    if (success) {
                        ProjectRunner.launchApp(packageName)
                    } else {
                        println("Reload failed. Press enter to return to logs.")
                        readlnOrNull()
                    }
                }
            }
        }
    }
}
