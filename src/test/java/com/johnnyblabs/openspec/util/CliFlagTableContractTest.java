package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CliFlags} agrees with the CLI's own record: for every captured release, a command or flag is in the table's
 * range exactly when that release's {@code --help} lists it, and a flag takes a value exactly when its help shows one.
 * Both directions count: a table that allows a flag the release lacks would send "unknown option", and one that
 * refuses a flag the release has would turn a working action off.
 */
class CliFlagTableContractTest {

    /** Every supported line's sweep; a missing one fails instead of shrinking the check. */
    static final Set<String> FLOOR = HelpSweepContractTest.FLOOR;

    static List<String> releases() {
        return FixtureVersions.versionsWith("help/index.json", FLOOR);
    }

    @TestFactory
    Stream<DynamicTest> theTableMatchesEachReleasesHelp() {
        return releases().stream().map(v -> DynamicTest.dynamicTest(v, () -> {
            List<String> wrong = disagreements(CliHelp.of(v));
            assertEquals(List.of(), wrong, v + ": CliFlags disagrees with the captured --help");
        }));
    }

    static List<String> disagreements(CliHelp help) {
        String v = help.version();
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, CliFlags.Range> c : CliFlags.commands().entrySet()) {
            String command = c.getKey();
            if (!help.records(command)) {
                wrong.add(command + ": the help sweep doesn't record it (add it to capture-help-sweep.py)");
                continue;
            }
            boolean has = help.hasCommand(command);
            if (c.getValue().includes(v) != has) {
                wrong.add(command + ": table says " + (has ? "absent" : "present") + ", help says "
                        + (has ? "present" : "absent"));
            }
            if (!has) continue;
            for (Map.Entry<String, CliFlags.Flag> f : CliFlags.flags(command).entrySet()) {
                boolean listed = help.hasFlag(command, f.getKey());
                if (f.getValue().range().includes(v) != listed) {
                    wrong.add(command + " " + f.getKey() + ": table says " + (listed ? "unsupported" : "supported")
                            + ", help says " + (listed ? "supported" : "unsupported"));
                } else if (listed && help.takesValue(command, f.getKey()) != f.getValue().takesValue()) {
                    wrong.add(command + " " + f.getKey() + ": table says "
                            + (f.getValue().takesValue() ? "takes a value" : "is a switch") + ", help disagrees");
                }
            }
        }
        return wrong;
    }

    @Test
    void theTableCoversTheGatesTheMatrixReliesOn() {
        // Sanity: the boundaries this change exists for are in the table, so the per-release check exercises them.
        assertEquals("1.5.0", CliFlags.flags("archive").get("--json").range().since());
        assertEquals("1.15.0", CliFlags.flags("update").get("--dry-run").range().since());
        assertEquals("2.1.0", CliFlags.flags("init").get("--scope").range().since());
        assertEquals("1.5.0", CliFlags.commands().get("workspace setup").until());
        assertTrue(CliFlags.commands().size() >= 30, "sanity: the table lists the plugin's commands");
    }
}
