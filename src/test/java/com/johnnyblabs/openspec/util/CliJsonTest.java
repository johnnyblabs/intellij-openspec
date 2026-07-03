package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CliJsonTest {

    /** The exact one-time notice the OpenSpec CLI prepends to stdout when telemetry is not opted out. */
    private static final String TELEMETRY_NOTE =
            "Note: OpenSpec collects anonymous usage stats. Opt out: OPENSPEC_TELEMETRY=0\n";

    @Test
    void stripsTelemetryNoticePrefixBeforeJsonObject() {
        String raw = TELEMETRY_NOTE + "{\"stores\":[],\"status\":[]}";
        assertEquals("{\"stores\":[],\"status\":[]}", CliJson.extractJsonPayload(raw));
    }

    @Test
    void stripsPrefixBeforeJsonArray() {
        String raw = TELEMETRY_NOTE + "[{\"id\":\"a\"}]";
        assertEquals("[{\"id\":\"a\"}]", CliJson.extractJsonPayload(raw));
    }

    @Test
    void picksEarliestOfBraceOrBracket() {
        assertEquals("[1,{}]", CliJson.extractJsonPayload("noise [1,{}]"));
        assertEquals("{\"a\":[1]}", CliJson.extractJsonPayload("noise {\"a\":[1]}"));
    }

    @Test
    void cleanJsonIsReturnedUnchanged() {
        String clean = "{\"stores\":[],\"status\":[]}";
        assertEquals(clean, CliJson.extractJsonPayload(clean));
    }

    @Test
    void noJsonOpenerReturnsTrimmedOriginal() {
        assertEquals("not json at all", CliJson.extractJsonPayload("  not json at all  "));
    }

    @Test
    void nullIsPassedThrough() {
        assertNull(CliJson.extractJsonPayload(null));
    }
}
