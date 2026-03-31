import org.jline.terminal.TerminalBuilder

object MenuSelector {

    // ── Same ANSI palette as the dashboard / UI object ──────────────────────
    private const val RESET  = "\u001B[0m"
    private const val BLUE   = "\u001B[44;97m"    // blue bg + bright white (header bar)
    private const val DIM    = "\u001B[2m"
    private const val BOLD   = "\u001B[1m"
    private const val CYAN   = "\u001B[36m"
    private const val WHITE  = "\u001B[97m"        // bright white (selected row)

    fun selectFromList(prompt: String, options: List<String>): String? {
        if (options.isEmpty()) return null
        if (options.size == 1) return options[0]

        val terminal = TerminalBuilder.builder()
            .system(true)
            .jna(true)
            .jansi(true)
            .build()

        terminal.enterRawMode()
        val writer = terminal.writer()
        var selectedIndex = 0

        fun render() {
            val w = terminal.width.takeIf  { it > 0 } ?: 80

            // ── header bar ───────────────────────────────────────────────────
            writer.print("\u001B[1;1H")           // move to top-left (already on alt buffer)
            writer.print("$BLUE  \uD83D\uDE80  Mish CLI  v1.0${" ".repeat(maxOf(0, w - 20))}$RESET\r\n")
            writer.print("$DIM${"─".repeat(w)}$RESET\r\n")

            // ── prompt ───────────────────────────────────────────────────────
            writer.print("\r\n")
            writer.print("  $BOLD$prompt$RESET\r\n")
            writer.print("\r\n")

            // ── options ──────────────────────────────────────────────────────
            options.forEachIndexed { index, option ->
                if (index == selectedIndex) {
                    writer.print("  $BOLD$WHITE➤  $option$RESET\r\n")
                } else {
                    writer.print("  $DIM   $option$RESET\r\n")
                }
            }

            // ── footer hint ──────────────────────────────────────────────────
            writer.print("\r\n")
            writer.print("$DIM  ↑ ↓  navigate    Enter  select    Esc  cancel$RESET\r\n")

            // clear any leftover lines below
            writer.print("\u001B[J")
            writer.flush()
        }

        try {
            // Enter alternate screen buffer → fixed, no scroll-back history
            writer.print("\u001B[?1049h")
            writer.print("\u001B[2J")
            writer.print("\u001B[?25l")   // hide cursor
            writer.print("\u001B[?7l")    // disable line wrap
            writer.flush()

            while (true) {
                render()

                val key = terminal.reader().read()

                when (key) {
                    27 -> {                           // ESC or arrow sequence
                        val next1 = terminal.reader().read()
                        if (next1 == 91) {            // '[' → arrow key
                            when (terminal.reader().read()) {
                                65 -> selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else options.size - 1
                                66 -> selectedIndex = if (selectedIndex < options.size - 1) selectedIndex + 1 else 0
                            }
                        } else {                      // bare ESC → cancel
                            return null
                        }
                    }
                    13, 10 -> return options[selectedIndex]   // Enter
                    3      -> return null                     // Ctrl+C
                }
            }

        } finally {
            writer.print("\u001B[?25h")    // show cursor
            writer.print("\u001B[?7h")     // re-enable line wrap
            writer.print("\u001B[?1049l")  // exit alternate screen
            writer.flush()
            terminal.close()
        }
    }
}
