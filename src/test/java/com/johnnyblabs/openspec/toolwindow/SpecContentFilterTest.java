package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.model.Requirement;
import com.johnnyblabs.openspec.model.Scenario;
import com.johnnyblabs.openspec.model.SpecFile;
import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full-text search over the Browse tree: a term occurring only inside a requirement's body or
 * scenario text must surface its spec + requirement nodes, while a non-matching term prunes them —
 * and the pre-existing label-only matching must not regress. Exercises the real
 * {@link SpecTreeModel#buildSpecsNode(List)} + {@link SpecTreeModel#filterNode} statics, so it is
 * the actual filter path, not a reimplementation.
 */
class SpecContentFilterTest {

    /** Domain/label deliberately share NO tokens with the body/scenario terms under test. */
    private static SpecFile sampleSpec() {
        SpecFile spec = new SpecFile("gateway", "/openspec/specs/gateway/spec.md");
        Requirement req = new Requirement("Throttle inbound traffic");
        req.setBody("The system SHALL enforce a quota of 100 tokens per client.");
        Scenario scenario = new Scenario("Burst handling");
        scenario.addClause("WHEN a client exceeds its quota");
        scenario.addClause("THEN surplus calls are rejected with backpressure");
        req.addScenario(scenario);
        spec.addRequirement(req);
        return spec;
    }

    private static DefaultMutableTreeNode filtered(String query) {
        DefaultMutableTreeNode specsNode = SpecTreeModel.buildSpecsNode(List.of(sampleSpec()));
        return SpecTreeModel.filterNode(specsNode, query);
    }

    private static boolean containsLabel(DefaultMutableTreeNode node, String label) {
        Object obj = node.getUserObject();
        if (obj instanceof SpecTreeModel.TreeNodeData data && data.label().equals(label)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsLabel((DefaultMutableTreeNode) node.getChildAt(i), label)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void termInRequirementBodySurfacesSpecAndRequirement() {
        // "tokens" appears only in the body — not in the domain label "gateway" nor the requirement
        // name "Throttle inbound traffic".
        DefaultMutableTreeNode result = filtered("tokens");
        assertNotNull(result, "spec with a body-only match must survive the filter");
        assertTrue(containsLabel(result, "gateway"), "the spec (domain) node must be present");
        assertTrue(containsLabel(result, "Requirement: Throttle inbound traffic"),
                "the matching requirement node must be present");
    }

    @Test
    void termInScenarioTextSurfacesRequirement() {
        // "backpressure" appears only in a scenario clause.
        DefaultMutableTreeNode result = filtered("backpressure");
        assertNotNull(result, "scenario-only match must survive the filter");
        assertTrue(containsLabel(result, "Requirement: Throttle inbound traffic"));
    }

    @Test
    void nonMatchingTermPrunesTheSpec() {
        assertNull(filtered("nonexistentxyz"), "a term matching nothing must prune the subtree");
    }

    @Test
    void contentMatchIsCaseInsensitive() {
        // Query is the already-normalized lowercase form the panel passes; body has mixed case.
        assertNotNull(filtered("tokens"));
        assertNotNull(filtered("quota"), "lowercase query must match the body regardless of case");
    }

    @Test
    void labelOnlyMatchStillWorks() {
        // Regression guard: matching the domain label alone (no content hit) still surfaces the spec.
        DefaultMutableTreeNode result = filtered("gateway");
        assertNotNull(result);
        assertTrue(containsLabel(result, "gateway"));
    }

    @Test
    void matcherMatchesAndNonMatchesDirectly() {
        Requirement req = sampleSpec().getRequirements().get(0);
        assertTrue(SpecContentMatcher.matches(req, "quota"), "body term must match");
        assertTrue(SpecContentMatcher.matches(req, "Throttle"), "name term must match");
        assertTrue(SpecContentMatcher.matches(req, "backpressure"), "scenario clause must match");
        assertFalse(SpecContentMatcher.matches(req, "nonexistentxyz"), "unrelated term must not match");
    }
}
