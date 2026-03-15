#!/bin/bash
# Capture JetBrains Marketplace screenshots at 1280x800
# Usage: ./scripts/capture-screenshots.sh
#
# Prerequisites: IDE must be running with the plugin loaded
# The script will resize the frontmost window and capture it.

set -e

OUTPUT_DIR="docs/screenshots"
mkdir -p "$OUTPUT_DIR"

SHOTS=(
    "01-tree-view:Show the tool window with Specs, Changes, and Archive expanded"
    "02-workflow-panel:Show the workflow panel with pipeline chips and Generate button"
    "03-setup-wizard:Show the setup wizard welcome step with brand icon"
    "04-gutter-markers:Show a Java file with @spec gutter icons and Coverage tab"
    "05-getting-started:Show the getting started panel with brand icon and tagline"
)

echo "=== OpenSpec Screenshot Capture ==="
echo "Make sure IntelliJ IDEA is running with the OpenSpec plugin loaded."
echo ""

# Resize frontmost window to 1280x800
echo "Resizing IDE window to 1280x800..."
osascript -e '
tell application "System Events"
    set frontApp to name of first application process whose frontmost is true
    tell process frontApp
        set position of window 1 to {50, 50}
        set size of window 1 to {1280, 800}
    end tell
end tell
'
echo "Window resized."
echo ""

for shot in "${SHOTS[@]}"; do
    name="${shot%%:*}"
    description="${shot#*:}"
    filepath="$OUTPUT_DIR/${name}.png"

    echo "---"
    echo "Screenshot: $name"
    echo "  → $description"
    echo ""
    echo "  Navigate to the correct view in the IDE, then press ENTER to capture."
    echo "  (Press 's' to skip, 'q' to quit)"
    read -r input

    if [ "$input" = "q" ]; then
        echo "Quitting."
        exit 0
    fi

    if [ "$input" = "s" ]; then
        echo "  Skipped."
        continue
    fi

    # Capture the frontmost window
    # Get window ID of frontmost app
    WINDOW_ID=$(osascript -e '
    tell application "System Events"
        set frontApp to name of first application process whose frontmost is true
        tell process frontApp
            set winId to id of window 1
        end tell
    end tell
    return winId
    ' 2>/dev/null)

    # Use screencapture with window selection
    screencapture -o -l "$WINDOW_ID" "$filepath" 2>/dev/null || \
    screencapture -o -w "$filepath"

    if [ -f "$filepath" ]; then
        # Resize to exactly 1280x800 if sips is available
        sips -z 800 1280 "$filepath" --out "$filepath" > /dev/null 2>&1 || true
        SIZE=$(stat -f%z "$filepath")
        echo "  ✓ Saved: $filepath ($SIZE bytes)"
    else
        echo "  ✗ Capture failed"
    fi
done

echo ""
echo "=== Done ==="
echo "Screenshots saved to $OUTPUT_DIR/"
ls -la "$OUTPUT_DIR/"
