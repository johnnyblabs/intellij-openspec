package com.johnnyblabs.openspec.docs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Documentation-maintenance-framework hygiene checks. These enforce the framework's
 * three structural guarantees on the real docs in the repository (not a fixture):
 *
 * <ol>
 *   <li>Every tracked doc declares a recognized {@code Maintenance:} class.</li>
 *   <li>The {@code docs/README.md} index references every doc under {@code docs/}, and
 *       every index link points to a doc that exists.</li>
 *   <li>No doc restates the plugin version in a way that has drifted from the single
 *       canonical source (the Version-support block in {@code openspec-support.md}).</li>
 * </ol>
 *
 * The check runs against the working tree so a doc added without a label / index entry,
 * or a re-introduced stale plugin-version restatement, fails the build.
 */
class DocumentationHygieneTest {

    /** The four recognized maintenance classes. */
    private static final Set<String> CLASSES = Set.of("Living", "Snapshot", "Reference", "Retired");

    /** A {@code Maintenance:} label naming exactly one class. */
    private static final Pattern LABEL =
            Pattern.compile("Maintenance:\\s*\\*{0,2}\\s*(Living|Snapshot|Reference|Retired)\\b");

    /** "plugin v1.2.3"-style restatement of *our* plugin's version. */
    private static final Pattern PLUGIN_VERSION =
            Pattern.compile("plugin v(\\d+\\.\\d+\\.\\d+)");

    /** The canonical version fact: "Current plugin version: 1.2.3". */
    private static final Pattern CANONICAL_VERSION =
            Pattern.compile("Current plugin version:\\s*(\\d+\\.\\d+\\.\\d+)");

    /** Top-level docs that also carry a maintenance label. */
    private static final List<String> ROOT_DOCS = List.of("README.md", "CHANGELOG.md", "CONTRIBUTING.md");

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("docs/README.md"))
                    && Files.exists(dir.resolve("CHANGELOG.md"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repo root from " + Paths.get("").toAbsolutePath());
    }

    private static List<Path> docsMarkdown(Path root) {
        Path docs = root.resolve("docs");
        try {
            return Files.list(docs)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- 7.1 every doc carries a recognized maintenance label ----------------

    @Test
    void everyDocDeclaresARecognizedMaintenanceClass() {
        Path root = repoRoot();
        List<Path> tracked = new ArrayList<>(docsMarkdown(root));
        ROOT_DOCS.forEach(name -> tracked.add(root.resolve(name)));

        List<String> unlabeled = new ArrayList<>();
        for (Path doc : tracked) {
            Matcher m = LABEL.matcher(read(doc));
            if (!m.find()) {
                unlabeled.add(root.relativize(doc).toString());
            } else {
                assertTrue(CLASSES.contains(m.group(1)),
                        doc + " uses an unrecognized maintenance class: " + m.group(1));
            }
        }
        assertTrue(unlabeled.isEmpty(),
                "These docs are missing a `Maintenance: <class>` label: " + unlabeled);
    }

    // ---- 7.2 the index references every doc, and every entry exists ----------

    @Test
    void indexReferencesEveryDocAndEveryEntryExists() {
        Path root = repoRoot();
        Path indexPath = root.resolve("docs/README.md");
        String index = read(indexPath);

        // (a) every doc under docs/ has an entry in the index (by filename).
        List<String> missing = new ArrayList<>();
        for (Path doc : docsMarkdown(root)) {
            String name = doc.getFileName().toString();
            if (!index.contains(name)) {
                missing.add(name);
            }
        }
        assertTrue(missing.isEmpty(),
                "docs/README.md index is missing entries for: " + missing);

        // (b) every same-directory *.md link in the index points to a real file.
        Matcher links = Pattern.compile("\\]\\(([A-Za-z0-9._-]+\\.md)").matcher(index);
        List<String> dangling = new ArrayList<>();
        while (links.find()) {
            String target = links.group(1);
            if (!Files.exists(root.resolve("docs").resolve(target))) {
                dangling.add(target);
            }
        }
        assertTrue(dangling.isEmpty(),
                "docs/README.md index links to non-existent docs: " + dangling);
    }

    // ---- 7.3 no doc restates a drifted plugin version -----------------------

    @Test
    void noDocRestatesADriftedPluginVersion() {
        Path root = repoRoot();
        String support = read(root.resolve("docs/openspec-support.md"));
        Matcher canon = CANONICAL_VERSION.matcher(support);
        assertTrue(canon.find(),
                "openspec-support.md must state the canonical `Current plugin version: X.Y.Z`");
        String canonical = canon.group(1);

        List<Path> tracked = new ArrayList<>(docsMarkdown(root));
        ROOT_DOCS.forEach(name -> tracked.add(root.resolve(name)));

        List<String> drift = new ArrayList<>();
        for (Path doc : tracked) {
            // CHANGELOG headings legitimately name historical versions.
            if (doc.getFileName().toString().equals("CHANGELOG.md")) {
                continue;
            }
            Matcher m = PLUGIN_VERSION.matcher(read(doc));
            while (m.find()) {
                if (!m.group(1).equals(canonical)) {
                    drift.add(root.relativize(doc) + " says plugin v" + m.group(1)
                            + " but canonical is v" + canonical);
                }
            }
        }
        assertTrue(drift.isEmpty(),
                "Plugin-version restatements have drifted from the canonical source: " + drift);
    }

    // ---- guard the test's own assumptions -----------------------------------

    @Test
    void anUnlabeledOrUnknownClassIsDetectableAsAFailure() {
        // The label matcher must reject a doc with no Maintenance label...
        assertFalse(LABEL.matcher("# A doc\n\nBody with no label.").find());
        // ...and reject an unknown class name...
        assertFalse(LABEL.matcher("> **Maintenance: Evergreen**").find());
        // ...while accepting each recognized class.
        for (String cls : CLASSES) {
            Matcher m = LABEL.matcher("> **Maintenance: " + cls + "** — note");
            assertTrue(m.find(), "should recognize class " + cls);
            assertEquals(cls, m.group(1));
        }
    }
}
