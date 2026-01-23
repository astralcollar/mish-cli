# Mish CLI

A simple Kotlin terminal utility to automate the Android project execution flow.

## Features

- 🕵️ **Autodetects** Android projects.
- 📱 **Emulator Management**: Checks for installed emulators, lists available AVDs, and launches one automatically if needed.
- 🚀 **Build & Run**: Builds your app (`installDebug`) and launches it on the emulator automatically.
- 📍 **Flexible**: Can run in the current directory or by specifying a path.

## Prerequisites

- **Java Development Kit (JDK)** 11 or higher.
- **Android SDK** installed and configured.
- **Gradle** (optional, the project includes a wrapper but having it installed is recommended).

## Installation

### Quick Option (Automated Script)

1. Navigate to the project directory:
   ```bash
   cd mish-cli
   ```
2. Run the installation script:
   ```bash
   ./install.sh
   ```
   This will:
   - Build the project
   - Automatically add `mish` to your `~/.zshrc`
   - Create an alias for easy access

3. Refresh your shell:
   ```bash
   source ~/.zshrc
   ```

Now you can use `mish` from any directory!

### Manual Option

1. Build the project:
   ```bash
   ./gradlew installDist
   ```
2. The executable will be generated at:
   ```
   build/install/mish-cli/bin/mish-cli
   ```
3. Add that directory to your PATH or create an alias in your `.zshrc` / `.bash_profile`:
   ```bash
   alias mish="/absolute/path/to/mish-cli/build/install/mish-cli/bin/mish-cli"
   ```

## Uninstallation

To remove Mish CLI from your system:

```bash
cd mish-cli
./uninstall.sh
```

This will:
- Remove the configuration from your `~/.zshrc` (creates a backup first)
- Clean up build artifacts
- Provide instructions to complete the removal

After running, refresh your shell:
```bash
source ~/.zshrc
```

## Usage

### In your current project

Navigate to the root of any Android project and run:

```bash
mish run
```

### In another directory

You can specify the path of the Android project you want to run:

```bash
mish run /Users/user/Projects/MyAndroidApp
```


# FW



https://github.com/user-attachments/assets/4b5f3077-cd31-42f5-b4a7-db077532338f



## Troubleshooting

- **`gradle: command not found`**: Install Gradle with `brew install gradle`.
- **`emulator: command not found`**: Ensure Android SDK tools (`platform-tools`, `emulator`) are in your PATH.
- if gradle are corrrupted and use home brew installer :```gradle wrapper```  
