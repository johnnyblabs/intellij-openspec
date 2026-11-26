#!/bin/bash
# Capture JetBrains Marketplace screenshots at 1280x800
# Usage: ./scripts/capture-screenshots.sh
#
# Automated: ./gradlew screenshotTour (see MarketplaceScreenshotTour) emits the whole set
# unattended and is the normal way to regenerate it; this manual flow is for one-off re-takes.
#
# Prerequisites: IDE must be running with the plugin loaded
# The script will resize the frontmost window and capture it.

set -e

OUTPUT_DIR="docs/screenshots"
mkdir -p "$OUTPUT_DIR"

# Marketplace set, numbered in upload order (docs/marketplace-page.md). Capture 11 LAST,
# and cancel its preview, so the demo project stays on 1.x.
SHOTS=(
    "01-spec-browser:Hero — a spec open in the editor beside the Browse tree, workflow chips and the change's deltas"
    "02-change-deltas:Browse tab — a change selected in the tree with its consolidated, badged spec deltas (ADDED/MODIFIED/REMOVED/RENAMED) in the preview pane, grouped by capability"
    "03-tree-badges:Browse tab — a change expanded in the tree showing corner status badges on the artifact icons (done/ready/blocked/not-created) plus the change node's apply-ready badge and X/Y task count"
    "04-explore-first-step:Explore with a topic — the notice that the explore command was copied for the AI agent"
    "05-change-workflow:Browse tab with demo-add-farewell selected — workflow chips showing done/ready/blocked and the Generate button"
    "06-regenerate-note:A done chip's Regenerate dialog with a note and the downstream warning"
    "07-revise-change:Revise Change dialog with a note for the agent's update workflow"
    "08-validation-quickfix:keyword-in-header spec.md with the keyword-placement inspection, after Validate"
    "09-validation-console:Console tab — a failed Validate run grouped by file, colored by severity"
    "10-coordination-stores:Coordination tab — Stores with store-doctor health, Worksets with members, health strip, Full-tier toolbar actions"
    "11-update-migration-preview:Update OpenSpec's preview of a 1.x project, with the OpenSpec 2 migration banner"
)

# Shots where a popup/dialog is the frontmost window: window-id capture would
# snap only the popup, so capture the fixed screen region of the IDE frame
# (positioned at 50,50 sized 1280x800 by the resize step above) instead.
REGION_SHOTS="06-regenerate-note 07-revise-change 08-validation-quickfix 11-update-migration-preview"

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
