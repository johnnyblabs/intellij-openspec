package com.johnnyblabs.openspec.toolwindow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure round-trip test for {@link DeltaDiffAnchor}. The href encoding must survive a round trip, a
 * non-diff href must resolve to {@code null} (the guard that leaves real links alone), and a
 * capability name containing a hyphen/colon must NOT be mangled by a naive {@code split(":")}.
 */
class DeltaDiffAnchorTest {

    @Test
    void roundTripsASimpleCapability() {
        assertEquals("auth", DeltaDiffAnchor.capabilityFromHref(DeltaDiffAnchor.diffAnchorHref("auth")));
    }

    @Test
    void nonDiffHrefResolvesToNull() {
        assertNull(DeltaDiffAnchor.capabilityFromHref("http://example.com"),
                "a real http link must not be treated as a diff anchor");
        assertNull(DeltaDiffAnchor.capabilityFromHref(null));
        assertNull(DeltaDiffAnchor.capabilityFromHref("mailto:x@y.z"));
    }

    @Test
    void preservesHyphensAndColonsInCapabilityName() {
        String cap = "billing:v2-legacy-charges";
        String href = DeltaDiffAnchor.diffAnchorHref(cap);
        // A naive split(":") would yield "billing" — prefix-strip must return the whole name.
        assertEquals(cap, DeltaDiffAnchor.capabilityFromHref(href));
        assertNotEquals("billing", DeltaDiffAnchor.capabilityFromHref(href));
    }
}
