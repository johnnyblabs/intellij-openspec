package com.johnnyblabs.openspec.root;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The wording every store surface shows, per source and for a root inside or outside the project. */
class RootProvenanceTest {

    @TempDir
    Path tmp;
    Path project;
    Path store;

    @BeforeEach
    void setUp() throws IOException {
        Path w = tmp.toRealPath();
        project = Files.createDirectories(w.resolve("app"));
        store = Files.createDirectories(w.resolve("stores/demo"));
    }

    @Test
    void aDeclaredStoreIsNamedWithItsSource() {
        RootResolution r = new RootResolution(store, RootResolution.Source.DECLARED, "demo", null, null);

        assertEquals("OpenSpec · store demo (declared)", RootProvenance.treeLabel(r, project));
        assertEquals("Specs live in " + store.resolve("openspec") + ", outside this project. "
                + "Commit changes from the store's own repository.", RootProvenance.treeTooltip(r, project));
        assertEquals("This file is in OpenSpec store 'demo', outside this project. "
                + "Commit changes from the store's own repository.", RootProvenance.bannerText(r));
    }

    @Test
    void theGlobalDefaultReadsGlobalDefault() {
        RootResolution r = new RootResolution(store, RootResolution.Source.GLOBAL_DEFAULT, "demo", null, null);

        assertEquals("OpenSpec · store demo (global default)", RootProvenance.treeLabel(r, project));
    }

    @Test
    void aParentFolderIsNamedWithoutAStore() {
        Path parent = project.getParent();
        RootResolution r = new RootResolution(parent, RootResolution.Source.NEAREST, null, null, null);

        assertEquals("OpenSpec · parent folder", RootProvenance.treeLabel(r, project));
        assertEquals("Specs live in " + parent.resolve("openspec") + ", outside this project. "
                + "Commit changes from that folder's own repository.", RootProvenance.treeTooltip(r, project));
        assertEquals("This file is in the OpenSpec folder " + parent.resolve("openspec") + ", outside this project. "
                + "Commit changes from that folder's own repository.", RootProvenance.bannerText(r));
    }

    @Test
    void aProjectsOwnRootHasNoProvenance() {
        RootResolution r = new RootResolution(project, RootResolution.Source.NEAREST, null, null, null);

        assertEquals("OpenSpec", RootProvenance.treeLabel(r, project));
        assertNull(RootProvenance.treeTooltip(r, project));
        assertTrue(RootProvenance.isLocal(r, project));
    }

    @Test
    void noRootHasNoProvenance() {
        assertEquals("OpenSpec", RootProvenance.treeLabel(RootResolution.none(), project));
        assertNull(RootProvenance.treeTooltip(RootResolution.none(), project));
    }

    @Test
    void anIgnoredPointerNamesTheStore() {
        RootResolution r = new RootResolution(project, RootResolution.Source.NEAREST, null, null, "warn", "demo");

        assertEquals("config.yaml names store 'demo', but this project has its own openspec/ tree, "
                + "so the store is ignored.", RootProvenance.ignoredPointerText(r));
        assertNull(RootProvenance.ignoredPointerText(new RootResolution(project, RootResolution.Source.NEAREST,
                null, null, null)));
    }

    @Test
    void aProblemIsTitledWithItsStoreWhenKnown() {
        assertEquals("The OpenSpec store 'sick' can't be used", RootProvenance.problemTitle(
                new RootResolution.Problem("unhealthy_store_root", "m", "f", "sick")));
        assertEquals("The OpenSpec store can't be used", RootProvenance.problemTitle(
                new RootResolution.Problem("invalid_store_pointer", "m", "f")));
    }
}
