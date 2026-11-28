package com.johnnyblabs.openspec.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The metadata edit, the text gate's edges and the confirmation text. */
class RetireMarkerFixTest {

    private static final RetirementMarkers.SchemaCheck BUILT_IN =
            n -> "spec-driven".equals(n) ? RetirementMarkers.SchemaVerdict.LOADS : RetirementMarkers.SchemaVerdict.UNKNOWN;

    @Test
    void appendingKeepsEveryOtherLine() {
        String yaml = "# the change\nschema: spec-driven   # default\ncreated: 2026-11-27\n\n";
        assertEquals(yaml + "retire_capabilities: true\n", RetireMarkerFix.withMarker(yaml));
        assertEquals("schema: spec-driven\nretire_capabilities: true\n", RetireMarkerFix.withMarker("schema: spec-driven"),
                "a missing final newline is added first");
        assertEquals("schema: spec-driven\r\nretire_capabilities: true\r\n",
                RetireMarkerFix.withMarker("schema: spec-driven\r\n"), "CRLF files stay CRLF");
    }

    @Test
    void aBadValueIsReplacedInPlaceWithItsContinuationLines() {
        assertEquals("schema: spec-driven\nretire_capabilities: true\ncreated: 2026-11-27\n",
                RetireMarkerFix.withMarker("schema: spec-driven\nretire_capabilities: yes\ncreated: 2026-11-27\n"));
        assertEquals("schema: spec-driven\nretire_capabilities: true\n\n# kept\n",
                RetireMarkerFix.withMarker("schema: spec-driven\nretire_capabilities:\n  - true\n  - x\n\n# kept\n"));
        assertEquals("schema: spec-driven\nretire_capabilities: true\n",
                RetireMarkerFix.withMarker("schema: spec-driven\n'retire_capabilities': 1\n"), "a quoted key");
        assertEquals("schema: spec-driven\nretire_capabilities: true  # not yet\n",
                RetireMarkerFix.withMarker("schema: spec-driven\nretire_capabilities: false  # not yet\n"), "a comment is kept");
        assertEquals("schema: spec-driven\nretire_capabilities: true\n",
                RetireMarkerFix.withMarker("schema: spec-driven\n\"retire_capabilities\": null\n"));
        for (String bad : List.of("retire_capabilities: yes", "retire_capabilities: 1", "retire_capabilities: null",
                "retire_capabilities: \"yes\"", "retire_capabilities:\n  - true")) {
            String edited = RetireMarkerFix.withMarker("schema: spec-driven\n" + bad + "\n");
            assertTrue(RetirementMarkers.parse(edited, BUILT_IN).declared(), bad + " -> " + edited);
        }
    }

    @Test
    void anEditThatWouldNotCountIsNotOffered() {
        String hint = "This change removes the last requirement 'foo' has. To retire the capability and delete its spec, "
                + "add `retire_capabilities: true` to the change's .openspec.yaml, then rerun.";
        java.util.function.Supplier<List<String>> foo = () -> List.of("foo");
        assertNotNull(RetireMarkerFix.decide("c", List.of(hint), "schema: spec-driven\n", BUILT_IN, foo));
        // A duplicate key stays a duplicate; no schema stays no schema; an unknown schema stays unknown.
        assertNull(RetireMarkerFix.decide("c", List.of(hint),
                "schema: spec-driven\nretire_capabilities: x\nretire_capabilities: y\n", BUILT_IN, foo));
        assertNull(RetireMarkerFix.decide("c", List.of(hint), "created: 2026-11-27\n", BUILT_IN, foo));
        assertNull(RetireMarkerFix.decide("c", List.of(hint), "schema: nope\n", BUILT_IN, foo));
        assertNull(RetireMarkerFix.decide("c", List.of(hint), null, BUILT_IN, foo), "no metadata file");
        assertNull(RetireMarkerFix.decide("c", List.of(hint), "schema: spec-driven\n", BUILT_IN, List::of),
                "nothing the marker would retire");
    }

    @Test
    void theGateWantsTheHintAndAMarkerOnlyReason() {
        String hint = "This change removes the last requirement 'foo' has. To retire the capability and delete its spec, "
                + "add `retire_capabilities: true` to the change's .openspec.yaml, then rerun.";
        assertTrue(RetireMarkerFix.asksForMarker(hint));
        assertTrue(RetireMarkerFix.asksForMarker(hint + " The marker present now cannot be honored "
                + "(retire_capabilities: Invalid input: expected boolean, received number)."));
        assertFalse(RetireMarkerFix.asksForMarker(hint + " The marker present now cannot be honored (the file is not valid YAML)."));
        assertFalse(RetireMarkerFix.asksForMarker("This change removes the last requirement 'foo' has, but the spec holds "
                + "content ... Move it into `## Purpose` or a canonical requirement, or delete the spec by hand, then rerun."));
        assertFalse(RetireMarkerFix.asksForMarker("Run openspec validate foo after fixing the change deltas."));
    }

    @Test
    void theConfirmationSaysEverythingTheMarkerDoes() {
        String one = RetireMarkerFix.confirmation(new RetireMarkerFix.Offer("c", List.of("foo")));
        assertTrue(one.startsWith("Retire capability 'foo'?\n\nSetting retire_capabilities: true in c/.openspec.yaml lets "
                + "Sync Specs, archive and OpenSpec's agent skills delete specs/foo/spec.md.\n\n"), one);
        assertTrue(one.contains("covers the whole change and stays"), one);
        assertTrue(one.contains("cancelling there leaves the marker in place"), one);
        assertTrue(one.endsWith("restored from git only if it was committed."), one);
        assertFalse(one.contains("replaces retire_capabilities: false"), one);
        assertTrue(RetireMarkerFix.confirmation(new RetireMarkerFix.Offer("c", List.of("foo"), true))
                .contains("It replaces retire_capabilities: false."));
        assertTrue(RetireMarkerFix.replacesFalse("schema: spec-driven\nretire_capabilities: False # no\n"));
        assertFalse(RetireMarkerFix.replacesFalse("schema: spec-driven\nretire_capabilities: yes\n"));
        String two = RetireMarkerFix.confirmation(new RetireMarkerFix.Offer("c", List.of("foo", "group/zoo")));
        assertTrue(two.startsWith("Retire capabilities 'foo', 'group/zoo'?"), two);
        assertTrue(two.contains("delete specs/foo/spec.md, specs/group/zoo/spec.md."), two);
    }

    @Test
    void theSurfacesShowTheOfferOnlyWhereThereIsOne() {
        String hint = "This change removes the last requirement 'foo' has. To retire it, add `retire_capabilities: true`, then rerun.";
        var offer = new RetireMarkerFix.Offer("c", List.of("foo"));
        assertEquals("Add retire_capabilities: true…",
                com.johnnyblabs.openspec.dialogs.SyncPreviewDialogAccess.markerLinkText(offer, List.of(hint)));
        assertNull(com.johnnyblabs.openspec.dialogs.SyncPreviewDialogAccess.markerLinkText(null, List.of(hint)));
        assertTrue(com.johnnyblabs.openspec.dialogs.SyncPreviewDialogAccess.problemText(hint)
                .endsWith(" Use Sync Specs on this change to add the marker."));
        assertEquals("Run openspec validate foo after fixing the change deltas.",
                com.johnnyblabs.openspec.dialogs.SyncPreviewDialogAccess.problemText(
                        "Run openspec validate foo after fixing the change deltas."));
    }
}
