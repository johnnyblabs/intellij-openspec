package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.SpecFile;
import com.johnnyblabs.openspec.services.SpecParsingService;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Integration tests for SpecParsingService with project-level file access.
 * Verifies spec parsing from VirtualFile content in a real project context.
 */
public class SpecParsingIntegrationTest extends OpenSpecIntegrationTestBase {

    public void testParsesSpecFromProjectFiles() throws Exception {
        SpecParsingService service = getProject().getService(SpecParsingService.class);
        assertNotNull("SpecParsingService should be available", service);

        VirtualFile specFile = myFixture.findFileInTempDir("openspec/specs/actions/spec.md");
        assertNotNull("actions/spec.md should exist", specFile);

        String content = new String(specFile.contentsToByteArray(), StandardCharsets.UTF_8);
        SpecFile spec = service.parseSpecContent(content, "actions", specFile.getPath());

        assertEquals("Actions", spec.getTitle());
        assertEquals("actions", spec.getDomain());
        assertEquals(2, spec.getRequirements().size());
    }

    public void testParsesRequirementKeywords() throws Exception {
        SpecParsingService service = getProject().getService(SpecParsingService.class);
        VirtualFile specFile = myFixture.findFileInTempDir("openspec/specs/actions/spec.md");
        String content = new String(specFile.contentsToByteArray(), StandardCharsets.UTF_8);
        SpecFile spec = service.parseSpecContent(content, "actions", specFile.getPath());

        assertEquals("SHALL", spec.getRequirements().get(0).getKeyword());
        assertEquals("SHALL", spec.getRequirements().get(1).getKeyword());
    }

    public void testParsesScenarios() throws Exception {
        SpecParsingService service = getProject().getService(SpecParsingService.class);
        VirtualFile specFile = myFixture.findFileInTempDir("openspec/specs/actions/spec.md");
        String content = new String(specFile.contentsToByteArray(), StandardCharsets.UTF_8);
        SpecFile spec = service.parseSpecContent(content, "actions", specFile.getPath());

        assertEquals(1, spec.getRequirements().get(0).getScenarios().size());
        assertEquals("Init creates config",
                spec.getRequirements().get(0).getScenarios().get(0).getName());
    }

    /**
     * VFS-path parity (align-spec-parser-with-cli, task 4.5): materialize a CLI corpus spec into the
     * project, drive {@code parseAllSpecs}/{@code parseSpec(VirtualFile)} over the real VFS, and assert
     * both the parsed model and the {@link SpecTreeModel} render the CLI-correct structure. The chosen
     * corpus spec is {@code fenced-scenario}, whose only {@code #### Scenario:} sits inside a code fence:
     * the CLI (and the new parser) count 1 requirement with 0 scenarios, where the old fence-blind parser
     * counted a phantom scenario.
     */
    public void testCorpusSpecViaVfsRendersCliCorrectCounts() throws Exception {
        String corpus = readResource(
                "/fixtures/cli/1.6.0/parity-corpus/openspec/specs/fenced-scenario/spec.md");
        myFixture.addFileToProject("openspec/specs/fenced-scenario/spec.md", corpus);
        VirtualFile root = myFixture.findFileInTempDir("openspec");
        assertNotNull(root);
        VfsUtil.markDirtyAndRefresh(false, true, true, root);

        SpecParsingService service = getProject().getService(SpecParsingService.class);

        // parseAllSpecs (off-EDT VFS scan) discovers the materialized corpus spec.
        SpecFile viaAll = service.parseAllSpecs().stream()
                .filter(s -> "fenced-scenario".equals(s.getDomain()))
                .findFirst().orElse(null);
        assertNotNull("parseAllSpecs must discover the materialized corpus spec", viaAll);
        assertEquals("Fenced Scenario", viaAll.getTitle());
        assertEquals(1, viaAll.getRequirements().size());
        assertTrue("fenced #### Scenario must not be counted",
                viaAll.getRequirements().get(0).getScenarios().isEmpty());

        // parseSpec(VirtualFile) on the same file agrees.
        VirtualFile specFile = myFixture.findFileInTempDir("openspec/specs/fenced-scenario/spec.md");
        assertNotNull(specFile);
        SpecFile viaVfs = service.parseSpec(specFile, "fenced-scenario");
        assertEquals(viaAll, viaVfs);

        // SpecTreeModel renders the CLI-correct requirement count for the domain node.
        DefaultTreeModel tree = new SpecTreeModel(getProject()).buildModel();
        assertEquals(1, countRequirementsForDomain(tree, "fenced-scenario"));
    }

    private static String readResource(String path) throws Exception {
        try (InputStream is = SpecParsingIntegrationTest.class.getResourceAsStream(path)) {
            assertNotNull("missing test resource: " + path, is);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Number of REQUIREMENT child nodes under the Specs -> &lt;domain&gt; node in a built tree. */
    private static int countRequirementsForDomain(DefaultTreeModel model, String domain) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode top = (DefaultMutableTreeNode) root.getChildAt(i);
            if (!(top.getUserObject() instanceof SpecTreeModel.TreeNodeData d)
                    || d.type() != SpecTreeModel.TreeNodeType.SPECS) continue;
            for (int j = 0; j < top.getChildCount(); j++) {
                DefaultMutableTreeNode domainNode = (DefaultMutableTreeNode) top.getChildAt(j);
                if (domainNode.getUserObject() instanceof SpecTreeModel.TreeNodeData dd
                        && dd.type() == SpecTreeModel.TreeNodeType.SPEC_DOMAIN
                        && domain.equals(dd.label())) {
                    int count = 0;
                    for (int k = 0; k < domainNode.getChildCount(); k++) {
                        DefaultMutableTreeNode child = (DefaultMutableTreeNode) domainNode.getChildAt(k);
                        if (child.getUserObject() instanceof SpecTreeModel.TreeNodeData cd
                                && cd.type() == SpecTreeModel.TreeNodeType.REQUIREMENT) {
                            count++;
                        }
                    }
                    return count;
                }
            }
        }
        return -1;
    }

    public void testParsesDeltaSpec() throws Exception {
        SpecParsingService service = getProject().getService(SpecParsingService.class);
        VirtualFile deltaFile = myFixture.findFileInTempDir(
                "openspec/changes/test-change/specs/actions/spec.md");
        assertNotNull("Delta spec should exist", deltaFile);

        String content = new String(deltaFile.contentsToByteArray(), StandardCharsets.UTF_8);
        SpecFile spec = service.parseSpecContent(content, "actions", deltaFile.getPath());

        assertNotNull(spec);
        assertEquals(1, spec.getRequirements().size());
        assertEquals("Validate Action", spec.getRequirements().get(0).getName());
    }
}
