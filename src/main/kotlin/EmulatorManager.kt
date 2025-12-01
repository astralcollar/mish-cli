import java.io.File
import java.util.concurrent.TimeUnit

object EmulatorManager {
    fun isEmulatorInstalled(): Boolean {
        return try {
            val process = ProcessBuilder("emulator", "-help").start()
            process.waitFor(5, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun listAvds(): List<String> {
        println("Checking for available AVDs...")
        return try {
            val process = ProcessBuilder("emulator", "-list-avds").start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            val avds = output.lines().filter { it.isNotBlank() }
            println("Found AVDs: $avds")
            avds
        } catch (e: Exception) {
            println("Error listing AVDs: ${e.message}")
            emptyList()
        }
    }

    fun launchEmulator(avdName: String) {
        println("Launching emulator: $avdName...")
        try {
            // Launch in background
            val processBuilder = ProcessBuilder("emulator", "-avd", avdName)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()
            
            // Read output in a separate thread to avoid blocking but show status
            Thread {
                process.inputStream.bufferedReader().forEachLine { line ->
                    println("[Emulator]: $line")
                }
            }.start()

            // Give it some time to start booting
            println("Waiting for emulator to come online...")
            Thread.sleep(5000)
        } catch (e: Exception) {
            println("Failed to launch emulator: ${e.message}")
        }
    }
    
    fun getRunningDevices(): List<String> {
        return try {
            val process = ProcessBuilder("adb", "devices").start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            // Parse output: skip first line (header) and filter online devices
            output.lines()
                .drop(1)
                .filter { it.isNotBlank() && it.contains("\tdevice") }
                .map { it.split("\t")[0].trim() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun waitForDeviceOnline(maxWaitSeconds: Int = 60): Boolean {
        println("Waiting for device to come online...")
        val startTime = System.currentTimeMillis()
        
        while ((System.currentTimeMillis() - startTime) < maxWaitSeconds * 1000) {
            val devices = getRunningDevices()
            if (devices.isNotEmpty()) {
                println("Device online: ${devices.first()}")
                return true
            }
            Thread.sleep(2000)
            print(".")
        }
        
        println()
        println("Timeout waiting for device.")
        return false
    }
}
