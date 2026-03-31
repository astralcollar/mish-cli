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
        return try {
            val process = ProcessBuilder("emulator", "-list-avds").start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.lines().filter { it.isNotBlank() }
        } catch (e: Exception) {
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
            
            // Silently drain stdout/stderr to avoid blocking the process
            Thread {
                process.inputStream.bufferedReader().forEachLine { /* silenced */ }
            }.also { it.isDaemon = true }.start()

            // Give it some time to start booting
            print("Waiting for emulator to come online")
            System.out.flush()
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
        val startTime = System.currentTimeMillis()
        val spinner = listOf(".", "..", "...")
        var spinIndex = 0

        while ((System.currentTimeMillis() - startTime) < maxWaitSeconds * 1000) {
            val devices = getRunningDevices()
            if (devices.isNotEmpty()) {
                print("\r") // clear the spinner line
                println("Device online: ${devices.first()}")
                return true
            }
            val dots = spinner[spinIndex % spinner.size]
            print("\rWaiting for emulator to come online$dots   ") // trailing spaces erase leftover chars
            System.out.flush()
            spinIndex++
            Thread.sleep(2000)
        }

        println()
        println("Timeout waiting for device.")
        return false
    }
}
