package com.johnnyblabs.openspec.actions;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.services.AiToolRegistry;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify's hand-off to the agent's verify skill, outside Direct API, against every release's captured invocations: the
 * button follows the delivery mode, and the hand-off is the tool's own verify invocation with the change name.
 */
class VerifyHandoffTest {

    private static WorkflowInvocation.Found claude() {
        return new WorkflowInvocation.Found(AiToolRegistry.byId("claude"), "/openspec-verify-change");
    }

    @Test
    void directApiOffersNoHandoff_theOtherModesDo() {
        assertNull(VerifyHandoff.label(DeliveryMode.DIRECT_API, claude()), "Direct API assesses correctness in the IDE");
        for (DeliveryMode mode : List.of(DeliveryMode.CLIPBOARD, DeliveryMode.EDITOR_TAB)) {
            assertEquals("Verify with " + AiToolRegistry.byId("claude").name() + "…", VerifyHandoff.label(mode, claude()));
            assertEquals("Verify with your AI tool…", VerifyHandoff.label(mode, null), "no verify skill: generic label");
        }
    }

    @Test
    void verifyIsUpstreamsVerifyChangeSkill() {
        assertEquals("verify", WorkflowInvocation.VERIFY);
        assertEquals("openspec-verify-change", WorkflowInvocation.VERIFY_SKILL);
    }

    /** Every captured verify invocation, of every release, reaches the agent with the change name. */
    @Test
    void theHandoffIsEachToolsCapturedVerifyInvocationForTheChange() throws IOException {
        int checked = 0;
        boolean slash = false, dollar = false, command = false, plain = false;
        for (String v : FixtureVersions.versionsWith("workflow-invocations.json", List.of("1.17.0", "2.2.0"))) {
            JsonObject capture = JsonParser.parseString(Files.readString(FixtureVersions.CLI.resolve(v)
                    .resolve("workflow-invocations.json"))).getAsJsonObject();
            for (JsonElement e : capture.getAsJsonArray("rows")) {
                JsonObject row = e.getAsJsonObject();
                if (!row.get("workflow").getAsString().equals("verify") || row.get("invocation").isJsonNull()) continue;
                assertEquals(WorkflowInvocation.VERIFY_SKILL, row.get("skill").getAsString(), v);
                String invocation = row.get("invocation").getAsString();
                String handoff = VerifyHandoff.handoff(new WorkflowInvocation.Found(null, invocation), "demo");
                if (invocation.equals("the openspec-verify-change skill")) {
                    assertEquals("Use the openspec-verify-change skill: demo", handoff, v + " " + row);
                    plain = true;
                } else {
                    assertEquals(invocation + " demo", handoff, v + " " + row);
                    slash |= invocation.startsWith("/openspec-verify-change");
                    dollar |= invocation.startsWith("$");
                    command |= invocation.contains("opsx");
                }
                checked++;
            }
        }
        assertTrue(checked > 50, "sanity: verify rows " + checked);
        assertTrue(slash && dollar && command && plain, "every invocation form is covered");
    }
}
