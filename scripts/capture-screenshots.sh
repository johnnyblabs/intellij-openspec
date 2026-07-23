#!/bin/bash
# Capture JetBrains Marketplace screenshots at 1280x800
# Usage: ./scripts/capture-screenshots.sh
#
# Automated alternative: ./gradlew screenshotTour (see MarketplaceScreenshotTour) emits
# shots 01-04+06 unattended; this manual flow remains for shot 05 (Settings) and re-takes.
#
# Prerequisites: IDE must be running with the plugin loaded
# The script will resize the frontmost window and capture it.

set -e

OUTPUT_DIR="docs/screenshots"
mkdir -p "$OUTPUT_DIR"

# v0.4.0 set (designed 2026-07-16). Carousel narrative: daily loop → catches
# mistakes → 1.5/1.6 stores → safe upgrades. Capture 06 LAST — the cleanup flow
# stops re-raising once resolved.
SHOTS=(
    "01-spec-browser:Hero — Browse tab with Specs+Changes tree expanded, greeting spec.md open in the editor"
    "02-change-workflow:Browse tab with demo-add-farewell selected — workflow chips showing done/ready/blocked and the Generate button"
    "03-validation-quickfix:keyword-in-header spec.md with the keyword-placement inspection + Alt+Enter quick-fix, Console tab showing validate results"
    "04-coordination-stores:Coordination tab — Stores with store-doctor health, Worksets with members, health strip, Full-tier toolbar actions"
    "05-schema-authoring:Settings > Tools > OpenSpec, Schemas section — provenance tags, inline Validate result, Open Templates"
    "06-update-legacy-cleanup:OpenSpec Legacy File Cleanup dialog listing CLI-reported opsx files with checkboxes"
    "07-spec-preview:Browse tab master/detail — a spec selected in the tree with its rendered markdown in the preview pane beside it"
    "08-change-deltas:Browse tab — a change selected in the tree with its consolidated, badged spec deltas (ADDED/MODIFIED/REMOVED/RENAMED) in the preview pane, grouped by capability"
)

# Shots where a popup/dialog is the frontmost window: window-id capture would
# snap only the popup, so capture the fixed screen region of the IDE frame
# (positioned at 50,50 sized 1280x800 by the resize step above) instead.
REGION_SHOTS="03-validation-quickfix 06-update-legacy-cleanup"

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

    if [[ " $REGION_SHOTS " == *" $name "* ]]; then
        # Popup/dialog shot: capture the IDE frame's screen region so the
        # frame AND the floating popup both land in the shot.
        screencapture -o -R 50,50,1280,800 "$filepath"
    else
        # Capture the frontmost window by window id
        WINDOW_ID=$(osascript -e '
        tell application "System Events"
            set frontApp to name of first application process whose frontmost is true
            tell process frontApp
                set winId to id of window 1
            end tell
        end tell
        return winId
        ' 2>/dev/null)

        screencapture -o -l "$WINDOW_ID" "$filepath" 2>/dev/null || \
        screencapture -o -w "$filepath"
    fi

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
