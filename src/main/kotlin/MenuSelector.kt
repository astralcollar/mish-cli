import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp

object MenuSelector {
    fun selectFromList(prompt: String, options: List<String>): String? {
        if (options.isEmpty()) return null
        if (options.size == 1) return options[0]
        
        val terminal = TerminalBuilder.builder()
            .system(true)
            .build()
        
        terminal.enterRawMode()
        var selectedIndex = 0
        
        try {
            while (true) {
                // Clear screen and move cursor to top
                terminal.writer().print("\u001B[2J\u001B[H")
                terminal.writer().println(prompt)
                terminal.writer().println()
                
                // Display options
                options.forEachIndexed { index, option ->
                    if (index == selectedIndex) {
                        terminal.writer().println("➤ $option")
                    } else {
                        terminal.writer().println("  $option")
                    }
                }
                
                terminal.writer().println()
                terminal.writer().println("Use ↑/↓ arrows to navigate, Enter to select")
                terminal.writer().flush()
                
                // Read key input
                val key = terminal.reader().read()
                
                when (key) {
                    27 -> { // ESC sequence
                        val next1 = terminal.reader().read()
                        if (next1 == 91) { // '[' 
                            when (terminal.reader().read()) {
                                65 -> { // Up arrow
                                    selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else options.size - 1
                                }
                                66 -> { // Down arrow
                                    selectedIndex = if (selectedIndex < options.size - 1) selectedIndex + 1 else 0
                                }
                            }
                        }
                    }
                    13, 10 -> { // Enter
                        terminal.writer().print("\u001B[2J\u001B[H")
                        terminal.writer().flush()
                        return options[selectedIndex]
                    }
                    3 -> { // Ctrl+C
                        terminal.writer().print("\u001B[2J\u001B[H")
                        terminal.writer().flush()
                        return null
                    }
                }
            }
        } finally {
            terminal.close()
        }
    }
}
