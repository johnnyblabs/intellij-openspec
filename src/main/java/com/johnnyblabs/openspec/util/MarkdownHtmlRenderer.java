package com.johnnyblabs.openspec.util;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.awt.*;

/**
 * Converts markdown to themed HTML suitable for display in a JEditorPane.
 */
public final class MarkdownHtmlRenderer {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

    private MarkdownHtmlRenderer() {}

    /**
     * Renders markdown text to an HTML fragment (no html/body wrapper).
     */
    public static String render(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        return RENDERER.render(PARSER.parse(markdown));
    }

    /**
     * Builds a CSS stylesheet derived from the current IntelliJ theme colors and fonts.
     */
    public static String buildThemeStylesheet() {
        Font labelFont = JBUI.Fonts.label();
        String fontFamily = labelFont.getFamily();
        int fontSize = labelFont.getSize();

        String fg = colorToHex(JBColor.foreground());
        String bg = colorToHex(JBColor.background());
        String border = colorToHex(JBColor.border());
        String muted = colorToHex(JBColor.GRAY);

        return "body { font-family: '" + fontFamily + "', sans-serif; font-size: " + fontSize + "pt; "
                + "color: " + fg + "; background-color: " + bg + "; margin: 8px; line-height: 1.5; }"
                + "h1 { font-size: " + (fontSize + 6) + "pt; margin: 12px 0 6px 0; }"
                + "h2 { font-size: " + (fontSize + 4) + "pt; margin: 10px 0 5px 0; }"
                + "h3 { font-size: " + (fontSize + 2) + "pt; margin: 8px 0 4px 0; }"
                + "h4 { font-size: " + fontSize + "pt; font-weight: bold; margin: 6px 0 3px 0; }"
                + "code { font-family: monospace; background-color: " + border + "; padding: 1px 4px; }"
                + "pre { font-family: monospace; background-color: " + border + "; "
                + "padding: 8px; margin: 8px 0; border: 1px solid " + border + "; overflow: auto; }"
                + "pre code { background-color: transparent; padding: 0; }"
                + "table { border-collapse: collapse; margin: 8px 0; width: 100%; }"
                + "th, td { border: 1px solid " + border + "; padding: 4px 8px; text-align: left; }"
                + "th { font-weight: bold; }"
                + "blockquote { border-left: 3px solid " + border + "; margin: 8px 0; "
                + "padding: 4px 12px; color: " + muted + "; }"
                + "ul, ol { margin: 4px 0; padding-left: 24px; }"
                + "li { margin: 2px 0; }"
                + "a { color: " + fg + "; }"
                + "hr { border: none; border-top: 1px solid " + border + "; margin: 12px 0; }";
    }

    /**
     * Wraps an HTML fragment and CSS stylesheet into a complete HTML document for JEditorPane.
     */
    public static String wrapInHtml(String cssStylesheet, String htmlFragment) {
        return "<html><head><style>" + cssStylesheet + "</style></head><body>"
                + htmlFragment + "</body></html>";
    }

    private static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
