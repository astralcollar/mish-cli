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
        println("Success! App launched.")
    } else {
        println("Error: Build failed.")
        exitProcess(1)
    }
}
