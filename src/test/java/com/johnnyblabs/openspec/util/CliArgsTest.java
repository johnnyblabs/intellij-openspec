package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link CliArgs} fits one invocation to a release using {@link CliFlags}. */
class CliArgsTest {

    @Test
    void aSupportedInvocationResolvesToItsArgumentsInOrder() throws Exception {
        CliArgs args = CliArgs.command("archive").arg("demo").flag("--yes").flag("--json").flag("--skip-specs");
        CliArgs.Resolved r = args.resolve("1.17.0");
        assertEquals(List.of("archive", "demo", "--yes", "--json", "--skip-specs"), r.argv());
        assertEquals(List.of(), r.dropped());
        assertEquals("1.17.0", r.version());
    }

    @Test
    void anOptionalFlagTheReleaseLacksIsDroppedWithItsValue() throws Exception {
        CliArgs args = CliArgs.command("init").optional("--scope", "project").flag("--tools", "none");
        assertEquals(List.of("init", "--tools", "none"), args.resolve("2.0.0").argv());
        assertEquals(List.of("--scope"), args.resolve("2.0.0").dropped());
        assertEquals(List.of("init", "--scope", "project", "--tools", "none"), args.resolve("2.1.0").argv());
    }

    @Test
    void aRequiredFlagTheReleaseLacksIsRefusedNamingWhatIsNeeded() {
        CliArgs args = CliArgs.command("archive").arg("demo").flag("--yes").flag("--json");
        UnsupportedCliArgumentException e = assertThrows(UnsupportedCliArgumentException.class,
                () -> args.resolve("1.4.1"));
        assertEquals("archive", e.command());
        assertEquals("--json", e.flag());
        assertTrue(e.getMessage().contains("openspec archive --json"), e.getMessage());
        assertTrue(e.getMessage().contains("1.5.0"), e.getMessage());
        assertTrue(e.getMessage().contains("1.4.1"), e.getMessage());
        assertInstanceOf(CliRunner.CliException.class, e, "existing CliException handlers report it");
    }

    @Test
    void aCommandTheReleaseLacksIsRefused() {
        UnsupportedCliArgumentException before = assertThrows(UnsupportedCliArgumentException.class,
                () -> CliArgs.command("doctor").flag("--json").resolve("1.4.1"));
        assertNull(before.flag());
        assertTrue(before.getMessage().contains("1.5.0 or newer"), before.getMessage());
        UnsupportedCliArgumentException after = assertThrows(UnsupportedCliArgumentException.class,
                () -> CliArgs.command("workspace", "list").flag("--json").resolve("1.5.0"));
        assertTrue(after.getMessage().contains("up to, but not including, 1.5.0"), after.getMessage());
    }

    @Test
    void aFlagOrCommandMissingFromTheTableIsAProgrammingError() {
        assertThrows(IllegalStateException.class, () -> CliArgs.command("bogus"));
        assertThrows(IllegalStateException.class, () -> CliArgs.command("archive").flag("--bogus"));
        assertThrows(IllegalStateException.class, () -> CliArgs.command("archive").flag("--json", "x"),
                "a switch given a value");
        assertThrows(IllegalStateException.class, () -> CliArgs.command("status").flag("--change"),
                "a valued flag given none");
    }

    @Test
    void aMissingOrUnreadableVersionResolvesAsTheTargetRelease() throws Exception {
        String target = PluginVersions.targetCliVersion();
        assertEquals(target, CliArgs.effectiveVersion(null));
        assertEquals(target, CliArgs.effectiveVersion("garbage"));
        assertEquals(target, CliArgs.effectiveVersion(""));
        assertEquals("1.4.1", CliArgs.effectiveVersion(" 1.4.1 "));
        assertEquals("2.1.0-beta.1", CliArgs.effectiveVersion("2.1.0-beta.1"));
        // The target (1.17.0 here) has archive --json, so an unknown version doesn't refuse it.
        assertEquals(target, CliArgs.command("archive").arg("x").flag("--json").resolve(null).version());
    }

    @Test
    void envAndDisplayCarryTheRequestedForm() {
        CliArgs args = CliArgs.command("update").flag("--dry-run").flag("--json").env("OPENSPEC_X", "1");
        assertEquals("openspec update --dry-run --json", args.display());
        assertEquals(List.of("update", "--dry-run", "--json"), args.requested());
        assertEquals("1", args.env().get("OPENSPEC_X"));
    }
}
