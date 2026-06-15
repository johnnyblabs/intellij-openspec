package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-unit coverage for {@link OpenSpecTerminalLauncher}. The {@link
 * OpenSpecTerminalLauncher#launchCommand} path requires a real {@code Project} +
 * Terminal tool window and is exercised via manual sandbox verification (task 9.4);
 * here we lock in the contract of the fallback-message helper that pairs with the
 * clipboard copy when the terminal isn't available.
 */
class OpenSpecTerminalLauncherTest {

    @Test
    void fallbackMessage_includesTheCommand() {
        String msg = OpenSpecTerminalLauncher.fallbackMessage("openspec config profile");
        assertTrue(msg.contains("openspec config profile"),
                "fallback message must echo the command so the user knows what was copied: " + msg);
    }

    @Test
    void fallbackMessage_pointsAtClipboardAndTerminal() {
        // Two halves of the fallback UX: the user needs to know (a) it's on the
        // clipboard and (b) they should open a terminal and paste. If either half
        // disappears, the notification reads as a dead-end.
        String msg = OpenSpecTerminalLauncher.fallbackMessage("openspec config profile");
        assertTrue(msg.toLowerCase().contains("clipboard"),
                "fallback message must mention the clipboard: " + msg);
        assertTrue(msg.toLowerCase().contains("terminal") || msg.toLowerCase().contains("paste"),
                "fallback message must direct the user to a terminal or to paste: " + msg);
    }
}
