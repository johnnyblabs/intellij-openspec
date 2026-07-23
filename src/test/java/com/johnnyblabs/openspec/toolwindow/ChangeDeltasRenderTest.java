package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.model.ChangeDeltaModel;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Delta;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Rename;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Requirement;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Scenario;
import com.johnnyblabs.openspec.model.DeltaSpecOperation.OperationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure render test for {@link SpecPreviewRenderer#renderChangeDeltas} over HAND-BUILT models (the
 * legitimate direction — hand-building a model is fine; hand-building CLI JSON is not). Assertions
 * target structural markers, never exact markup, and go BOTH directions (a thing IS present / a
 * thing is NOT present) so a broken renderer can't pass.
 */
class ChangeDeltasRenderTest {

    private static Delta req(String cap, OperationType op, String text, String scenario) {
        Requirement r = new Requirement(text, scenario == null ? List.of() : List.of(new Scenario(scenario)));
        return new Delta(cap, op, "desc", r, List.of(r), null);
    }

    private static Delta rename(String cap, String from, String to) {
        return new Delta(cap, OperationType.RENAMED, "desc", null, List.of(), new Rename(from, to));
    }

    /** ADDED+MODIFIED in auth, REMOVED+RENAMED in billing — but inserted billing-first (reverse). */
    private static ChangeDeltaModel mixedReverseInserted() {
        return new ChangeDeltaModel("mixed-change", "mixed-change", 4, List.of(
                req("billing", OperationType.REMOVED, "The system SHALL retain legacy invoice records.", null),
                rename("billing", "Old charge name", "Card charge"),
                req("auth", OperationType.ADDED, "The system SHALL require a second factor.", "- **WHEN** x\n- **THEN** y"),
                req("auth", OperationType.MODIFIED, "Users SHALL log in with rate limiting.", "- **WHEN** a\n- **THEN** b")
        ));
    }

    @Test
    void capabilitiesRenderInAlphabeticalOrderNotInsertionOrder() {
        // Sort trap: billing is inserted before auth. If the plugin's alphabetical sort were removed,
        // billing would render first and this assertion would fail.
        String html = SpecPreviewRenderer.renderChangeDeltas(mixedReverseInserted());
        int auth = html.indexOf("<h2>auth</h2>");
        int billing = html.indexOf("<h2>billing</h2>");
        assertTrue(auth >= 0 && billing >= 0, "both capability headers must render");
        assertTrue(auth < billing, "capabilities must render alphabetically (auth before billing)");
    }

    @Test
    void requirementOperationsAreBadgedButHeaderSummaryAndRenamedAreNot() {
        String html = SpecPreviewRenderer.renderChangeDeltas(mixedReverseInserted());

        // Requirement-operation sections carry their badge span (both directions across ops).
        assertTrue(html.contains("openspec-op-added"), "ADDED section is badged");
        assertTrue(html.contains("openspec-op-modified"), "MODIFIED section is badged");
        assertTrue(html.contains("openspec-op-removed"), "REMOVED section is badged");

        // RENAMED is rendered as a from→to line WITHOUT an op-keyword header, so it is NOT badged.
        assertFalse(html.contains("openspec-op-renamed"), "RENAMED must not carry an operation badge");

        // The header and the summary line carry no badge — the literal summary survives verbatim,
        // which both proves it is unbadged AND pins the specific counts.
        assertTrue(html.contains("2 capabilities · 1 ADDED · 1 MODIFIED · 1 REMOVED · 1 RENAMED"),
                "summary counts render verbatim (and thus unbadged)");
        assertTrue(html.contains("<h1>Deltas — mixed-change</h1>"), "header names the change");
    }

    @Test
    void requirementBodyAndScenarioAreRendered() {
        String html = SpecPreviewRenderer.renderChangeDeltas(mixedReverseInserted());
        assertTrue(html.contains(SpecPreviewRenderer.DELTA_REQUIREMENT_CLASS),
                "requirement bodies are wrapped in the requirement marker");
        assertTrue(html.contains("second factor"), "requirement text is rendered");
        assertTrue(html.contains("<strong>WHEN</strong>") || html.contains("WHEN"),
                "scenario rawText markdown is rendered");
    }

    @Test
    void eachCapabilityEmitsADiffCrossLink() {
        String html = SpecPreviewRenderer.renderChangeDeltas(mixedReverseInserted());
        assertTrue(html.contains("href=\"" + DeltaDiffAnchor.diffAnchorHref("auth") + "\""),
                "auth section links to its diff");
        assertTrue(html.contains("href=\"" + DeltaDiffAnchor.diffAnchorHref("billing") + "\""),
                "billing section links to its diff");
    }

    @Test
    void renamedRendersFromToWithNoRequirementBody() {
        ChangeDeltaModel renameOnly = new ChangeDeltaModel("rename-only", "rename-only", 1, List.of(
                rename("billing", "Old charge name", "Card charge")));
        String html = SpecPreviewRenderer.renderChangeDeltas(renameOnly);

        assertTrue(html.contains("Old charge name"), "rename shows the FROM name");
        assertTrue(html.contains("Card charge"), "rename shows the TO name");
        assertTrue(html.contains("→"), "rename shows a from→to arrow");
        assertTrue(html.contains(SpecPreviewRenderer.DELTA_RENAME_CLASS), "rename uses its marker");
        // The null-safe branch: a rename-only capability produces NO requirement body and NO badge.
        assertFalse(html.contains(SpecPreviewRenderer.DELTA_REQUIREMENT_CLASS),
                "a RENAMED-only capability must not render a requirement body");
        assertFalse(html.contains(DeltaBadgeDecorator.BADGE_CLASS),
                "a RENAMED-only render carries no operation badge");
    }

    @Test
    void emptyModelYieldsEmptyStateMarker() {
        String html = SpecPreviewRenderer.renderChangeDeltas(
                new ChangeDeltaModel("c", "c", 0, List.of()));
        assertTrue(html.contains(SpecPreviewRenderer.EMPTY_STATE_MARKER),
                "a change with no deltas renders the empty-state marker");
        assertFalse(html.contains(DeltaBadgeDecorator.BADGE_CLASS), "empty state has no badges");
    }

    @Test
    void singularCapabilityCountIsGrammatical() {
        ChangeDeltaModel single = new ChangeDeltaModel("c", "c", 1, List.of(
                req("auth", OperationType.ADDED, "The system SHALL do a thing.", null)));
        String html = SpecPreviewRenderer.renderChangeDeltas(single);
        assertTrue(html.contains("1 capability · 1 ADDED"), "single capability is singular");
        assertFalse(html.contains("1 capabilities"), "must not read '1 capabilities'");
    }
}
