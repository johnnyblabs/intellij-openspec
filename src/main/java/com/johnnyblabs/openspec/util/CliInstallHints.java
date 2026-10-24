package com.johnnyblabs.openspec.util;

/**
 * The one place the plugin says how to install the OpenSpec CLI. The commands are upstream's README Quick Start,
 * word for word: the standalone installer first (no Node.js), then npm, then Homebrew outside Windows. The plugin
 * only shows them; it never runs an installer.
 */
public final class CliInstallHints {

    /** Upstream's installation page, which covers every route (the README Quick Start links here too). */
    public static final String INSTALL_DOCS_URL = "https://openspec.dev/docs/installation";
    /** The label of every link or action that opens {@link #INSTALL_DOCS_URL}. */
    public static final String INSTALL_DOCS_LABEL = "Install instructions";

    static final String UNIX_STANDALONE =
            "curl -fsSL https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.sh | sh";
    static final String WINDOWS_STANDALONE =
            "irm https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.ps1 | iex";
    static final String NPM = "npm install -g @fission-ai/openspec@latest";
    static final String BREW = "brew install openspec";

    private CliInstallHints() {
    }

    /** The standalone installer's one-liner for the host OS. */
    public static String standaloneCommand(boolean isWindows) {
        return isWindows ? WINDOWS_STANDALONE : UNIX_STANDALONE;
    }

    /** Where the standalone installer puts the binary by default. */
    public static String standaloneLocation(boolean isWindows) {
        return isWindows ? "%LOCALAPPDATA%\\Programs\\openspec\\openspec.exe" : "~/.local/bin/openspec";
    }

    /** One short sentence for a notification balloon, which is too narrow for the one-liners. */
    public static String notificationText(boolean isWindows) {
        return isWindows
                ? "Install it with OpenSpec's installer (no Node.js needed) or npm."
                : "Install it with OpenSpec's installer (no Node.js needed), npm or Homebrew.";
    }

    /**
     * An HTML fragment (no {@code <html>} wrapper) with the commands for the host OS. Labels can't follow links, so
     * callers add a link to {@link #INSTALL_DOCS_URL} beside it.
     */
    public static String html(boolean isWindows) {
        StringBuilder sb = new StringBuilder()
                .append("Install with: <code>").append(escape(standaloneCommand(isWindows))).append("</code> (no Node.js needed)")
                .append(", or <code>").append(NPM).append("</code> (needs Node.js 20.19 or later)");
        if (!isWindows) {
            sb.append(", or <code>").append(BREW).append("</code>");
        }
        return sb.append('.').toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
