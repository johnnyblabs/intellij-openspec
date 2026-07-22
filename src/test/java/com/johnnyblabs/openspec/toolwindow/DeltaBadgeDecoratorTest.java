package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.util.MarkdownHtmlRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Delta operation badges. Each of ADDED/MODIFIED/REMOVED/RENAMED must gain the stable marker class;
 * a plain {@code ## Requirements} heading must NOT (the never-conflate-a-main-spec guard); and
 * decorating twice must not double-badge (idempotence). Input HTML is produced by the real
 * commonmark renderer, not hand-authored, so the test tracks the actual heading shape.
 */
class DeltaBadgeDecoratorTest {

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    @Test
    void everyOperationHeaderGetsBadged() {
        for (String op : new String[]{"ADDED", "MODIFIED", "REMOVED", "RENAMED"}) {
            String html = MarkdownHtmlRenderer.render("## " + op + " Requirements");
            String decorated = DeltaBadgeDecorator.decorate(html);
            assertTrue(decorated.contains(DeltaBadgeDecorator.BADGE_CLASS),
                    op + " header must carry the badge marker class");
            assertTrue(decorated.contains("openspec-op-" + op.toLowerCase()),
                    op + " header must carry its per-operation class");
            assertTrue(decorated.contains(op), op + " keyword text must be preserved");
        }
    }

    @Test
    void plainRequirementsHeadingIsNotBadged() {
        // The never-conflate guard: a main spec's "## Requirements" must never be badged.
        String html = MarkdownHtmlRenderer.render("## Requirements");
        String decorated = DeltaBadgeDecorator.decorate(html);
        assertFalse(decorated.contains(DeltaBadgeDecorator.BADGE_CLASS),
                "a plain Requirements heading must not be badged");
    }

    @Test
    void decorationIsIdempotent() {
        String html = MarkdownHtmlRenderer.render("## ADDED Requirements");
        String once = DeltaBadgeDecorator.decorate(html);
        String twice = DeltaBadgeDecorator.decorate(once);
        assertEquals(once, twice, "decorating an already-badged header must be a no-op");
        assertEquals(1, countOccurrences(twice, DeltaBadgeDecorator.BADGE_CLASS),
                "the badge must appear exactly once, not doubled");
    }

    @Test
    void nullInputIsReturnedUnchanged() {
        assertNull(DeltaBadgeDecorator.decorate(null));
    }
}
