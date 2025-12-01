#!/bin/bash

echo "Uninstalling Mish CLI..."

# Remove from ~/.zshrc if it exists
RC_FILE="$HOME/.zshrc"
if [ -f "$RC_FILE" ]; then
    echo ""
    echo "Removing configuration from $RC_FILE..."
    
    # Remove the Mish CLI section
    if grep -q "# Mish CLI" "$RC_FILE"; then
        # Create a backup
        cp "$RC_FILE" "$RC_FILE.backup"
        echo "Created backup at $RC_FILE.backup"
        
        # Remove the mish-cli lines
        sed -i.tmp '/# Mish CLI/,/alias mish=/d' "$RC_FILE"
        rm -f "$RC_FILE.tmp"
        
        echo "Removed Mish CLI configuration from $RC_FILE"
    else
        echo "No Mish CLI configuration found in $RC_FILE"
    fi
fi

# Remove build artifacts
echo ""
echo "Removing build artifacts..."
rm -rf build/
rm -rf .gradle/

echo ""
echo "✅ Uninstall complete!"
echo ""
echo "To complete the removal:"
echo "1. Run: source ~/.zshrc (to refresh your current shell)"
echo "2. Optionally delete this entire directory if you don't need it anymore"
