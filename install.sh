#!/bin/bash

# Build the project
./gradlew installDist

# Get absolute path
INSTALL_DIR="$(pwd)/build/install/mish-cli/bin"
EXECUTABLE="$INSTALL_DIR/mish-cli"

echo "Executable created at: $EXECUTABLE"

# Add to ~/.zshrc if it exists
RC_FILE="$HOME/.zshrc"
if [ -f "$RC_FILE" ]; then
    echo ""
    echo "Adding configuration to $RC_FILE..."
    
    # Check if already added to avoid duplicates
    if grep -q "mish-cli" "$RC_FILE"; then
        echo "Configuration already exists in $RC_FILE"
    else
        echo "" >> "$RC_FILE"
        echo "# Mish CLI" >> "$RC_FILE"
        echo "export PATH=\"\$PATH:$INSTALL_DIR\"" >> "$RC_FILE"
        echo "alias mish='mish-cli'" >> "$RC_FILE"
        echo "Added to $RC_FILE!"
    fi
    
    echo ""
    echo "✅ Installation complete!"
    echo "👉 Please run this command to refresh your current shell:"
    echo "   source ~/.zshrc"
else
    echo "Could not find ~/.zshrc. Please add this manually:"
    echo "export PATH=\"\$PATH:$INSTALL_DIR\""
    echo "alias mish='mish-cli'"
fi
