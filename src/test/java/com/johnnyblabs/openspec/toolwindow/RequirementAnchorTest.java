package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.util.MarkdownHtmlRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requirement anchoring. {@code anchorId} must be stable and collision-free across distinct names,
 * and {@code injectAnchors} must emit an anchor whose name equals {@code anchorId(name)} — a
 * round-trip invariant, NOT a hardcoded slug (so the slug scheme can evolve without editing the
 * test). Heading HTML is produced by the real renderer.
 */
class RequirementAnchorTest {

    @Test
    void anchorIdIsStableForTheSameName() {
        assertEquals(RequirementAnchors.anchorId("Rate limiting"),
                RequirementAnchors.anchorId("Rate limiting"));
    }

    @Test
    void distinctNamesYieldDistinctAnchors() {
        String a = RequirementAnchors.anchorId("Throttle inbound traffic");
        String b = RequirementAnchors.anchorId("Persist audit log");
        String c = RequirementAnchors.anchorId("Rate limiting");
        assertNotEquals(a, b);
        assertNotEquals(b, c);
        assertNotEquals(a, c);
    }

    @Test
    void injectAnchorsRoundTripsWithAnchorId() {
        String name = "Rate limiting the gateway";
        String html = MarkdownHtmlRenderer.render("### Requirement: " + name);
        String injected = RequirementAnchors.injectAnchors(html);

        // The invariant: the injected anchor's name is exactly anchorId(name).
        assertTrue(injected.contains("name=\"" + RequirementAnchors.anchorId(name) + "\""),
                "injected anchor must equal anchorId(name); got: " + injected);
        // The heading text must survive injection.
        assertTrue(injected.contains(name), "requirement name text must be preserved");
    }

    @Test
    void injectAnchorsHandlesMultipleRequirements() {
        String markdown = "### Requirement: First one\nbody\n\n### Requirement: Second one\nbody";
        String injected = RequirementAnchors.injectAnchors(MarkdownHtmlRenderer.render(markdown));
        assertTrue(injected.contains("name=\"" + RequirementAnchors.anchorId("First one") + "\""));
        assertTrue(injected.contains("name=\"" + RequirementAnchors.anchorId("Second one") + "\""));
    }

    @Test
    void nonRequirementHeadingsAreUntouched() {
        String injected = RequirementAnchors.injectAnchors(MarkdownHtmlRenderer.render("## Requirements"));
        assertFalse(injected.contains("<a name="), "a section heading is not a requirement heading");
    }

    @Test
    void nullInputIsReturnedUnchanged() {
        assertNull(RequirementAnchors.injectAnchors(null));
    }
}
