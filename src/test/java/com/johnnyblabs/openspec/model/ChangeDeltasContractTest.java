package com.johnnyblabs.openspec.model;

import com.johnnyblabs.openspec.model.ChangeDeltaModel.CapabilityGroup;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Delta;
import com.johnnyblabs.openspec.model.DeltaSpecOperation.OperationType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link ChangeDeltaModel#parse(String)} against REAL captured CLI output
 * ({@code openspec show <change> --type change --json}, CLI 1.6.0) — never hand-authored JSON. The
 * fixtures live under {@code fixtures/cli/1.6.0/change-deltas/}; if the CLI's shape changes,
 * re-capture them (stdout only, {@code root.path} sanitized) and fix the failures here.
 *
 * <p>Boundary discipline: this test parses the captured JSON into the model (the contract); the
 * model→HTML render is exercised separately by {@code ChangeDeltasRenderTest} with a hand-built
 * model. Hand-building a model is fine; hand-building the JSON would be the vacuous-fixture sin.
 */
class ChangeDeltasContractTest {

    private static String fixture(String name) {
        String path = "/fixtures/cli/1.6.0/change-deltas/" + name;
        try (InputStream is = ChangeDeltasContractTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    @Test
    void parsesMixedDeltasWithGroupingAndOperations() {
        ChangeDeltaModel model = ChangeDeltaModel.parse(fixture("mixed.show.json"));

        assertEquals("mixed-change", model.id());
        assertEquals(4, model.deltaCount(), "CLI-reported deltaCount");
        assertEquals(4, model.deltas().size());

        List<CapabilityGroup> groups = model.groupedByCapability();
        assertEquals(List.of("auth", "billing"),
                groups.stream().map(CapabilityGroup::capability).toList(),
                "capabilities are grouped and stably (alphabetically) ordered");
        assertEquals(2, model.capabilityCount());

        // auth: ADDED then MODIFIED (CLI operation order preserved within capability).
        List<Delta> auth = groups.get(0).deltas();
        assertEquals(List.of(OperationType.ADDED, OperationType.MODIFIED),
                auth.stream().map(Delta::operation).toList());
        // billing: REMOVED then RENAMED.
        List<Delta> billing = groups.get(1).deltas();
        assertEquals(List.of(OperationType.REMOVED, OperationType.RENAMED),
                billing.stream().map(Delta::operation).toList());

        // ADDED: requirement text + scenario rawText recovered.
        Delta added = auth.get(0);
        assertNotNull(added.requirement());
        assertTrue(added.requirement().text().contains("second authentication factor"),
                "ADDED requirement text recovered");
        assertEquals(1, added.requirement().scenarios().size());
        assertTrue(added.requirement().scenarios().get(0).rawText().contains("**WHEN**"),
                "scenario rawText carries the verbatim WHEN/THEN markdown");

        // MODIFIED: requirement text recovered.
        Delta modified = auth.get(1);
        assertTrue(modified.requirement().text().contains("rate limiting"));

        // REMOVED: has requirement.text but EMPTY scenarios (not requirement-less).
        Delta removed = billing.get(0);
        assertNotNull(removed.requirement(), "REMOVED still carries a requirement");
        assertTrue(removed.requirement().text().contains("legacy invoice"));
        assertTrue(removed.requirement().scenarios().isEmpty(), "REMOVED has empty scenarios");

        // RENAMED: from/to present, requirement NULL (the requirement-less branch).
        Delta renamed = billing.get(1);
        assertNull(renamed.requirement(), "RENAMED is requirement-less");
        assertNotNull(renamed.rename());
        assertEquals("Old charge name", renamed.rename().from());
        assertEquals("Card charge", renamed.rename().to());

        // Mirror invariant: every non-RENAMED delta's requirements[0] mirrors the singular requirement.
        for (Delta d : model.deltas()) {
            if (d.operation() == OperationType.RENAMED) {
                assertTrue(d.requirementsMirror().isEmpty(), "RENAMED has no requirements[] mirror");
                continue;
            }
            assertFalse(d.requirementsMirror().isEmpty(), "non-RENAMED delta carries requirements[]");
            assertEquals(d.requirement().text(), d.requirementsMirror().get(0).text(),
                    "requirements[0].text mirrors the singular requirement.text");
        }
    }

    @Test
    void parsesEmptyChange() {
        ChangeDeltaModel model = ChangeDeltaModel.parse(fixture("empty.show.json"));
        assertEquals("empty-change", model.id());
        assertEquals(0, model.deltaCount());
        assertTrue(model.deltas().isEmpty());
        assertEquals(0, model.capabilityCount());
    }

    @Test
    void parsesRenameOnlyWithoutError() {
        ChangeDeltaModel model = ChangeDeltaModel.parse(fixture("rename-only.show.json"));
        assertEquals(1, model.deltaCount());
        assertEquals(1, model.deltas().size());

        Delta only = model.deltas().get(0);
        assertEquals(OperationType.RENAMED, only.operation());
        assertNull(only.requirement(), "rename-only delta must not NPE on a null requirement");
        assertEquals("billing", only.spec());
        assertEquals("Old charge name", only.rename().from());
        assertEquals("Card charge", only.rename().to());
    }

    @Test
    void changeShowArgsAreTheExactContract() {
        assertArrayEquals(
                new String[] {"show", "my-change", "--type", "change", "--json"},
                ChangeDeltaModel.changeShowArgs("my-change"),
                "argv must be the verb form with --type change --json (no deltas-only/requirements-only)");
    }
}
