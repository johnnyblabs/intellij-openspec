package com.johnnyblabs.openspec.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigProfileDetailTest {

    @Nested
    class JsonParsing {

        @Test
        void parsesValidJson() {
            String json = """
                    {
                      "name": "spec-driven",
                      "description": "Full spec-driven workflow",
                      "workflows": ["propose", "design", "specs", "tasks"]
                    }
                    """;
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson(json);

            assertEquals("spec-driven", detail.getName());
            assertEquals("Full spec-driven workflow", detail.getDescription());
            assertEquals(List.of("propose", "design", "specs", "tasks"), detail.getWorkflows());
        }

        @Test
        void parsesJsonWithEmptyWorkflows() {
            String json = """
                    {
                      "name": "minimal",
                      "description": "Minimal profile",
                      "workflows": []
                    }
                    """;
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson(json);

            assertEquals("minimal", detail.getName());
            assertEquals("Minimal profile", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void parsesJsonWithMissingFields() {
            String json = """
                    {
                      "name": "partial"
                    }
                    """;
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson(json);

            assertEquals("partial", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void handlesEmptyString() {
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson("");
            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void handlesNull() {
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson(null);
            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void handlesMalformedJson() {
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson("{not valid json");
            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void handlesJsonWithExtraFields() {
            String json = """
                    {
                      "name": "tdd",
                      "description": "TDD workflow",
                      "workflows": ["specs", "tasks"],
                      "extraField": "ignored"
                    }
                    """;
            ConfigProfileDetail detail = ConfigProfileDetail.fromJson(json);

            assertEquals("tdd", detail.getName());
            assertEquals("TDD workflow", detail.getDescription());
            assertEquals(List.of("specs", "tasks"), detail.getWorkflows());
        }
    }

    @Nested
    class Fallback {

        @Test
        void fallbackWithName() {
            ConfigProfileDetail detail = ConfigProfileDetail.fallback("spec-driven");

            assertEquals("spec-driven", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void fallbackWithNull() {
            ConfigProfileDetail detail = ConfigProfileDetail.fallback(null);

            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void fallbackWithEmpty() {
            ConfigProfileDetail detail = ConfigProfileDetail.fallback("");

            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }
    }

    @Nested
    class Constructor {

        @Test
        void defaultConstructor() {
            ConfigProfileDetail detail = new ConfigProfileDetail();

            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }

        @Test
        void fullConstructor() {
            ConfigProfileDetail detail = new ConfigProfileDetail(
                    "rapid", "Rapid development", List.of("propose", "tasks"));

            assertEquals("rapid", detail.getName());
            assertEquals("Rapid development", detail.getDescription());
            assertEquals(List.of("propose", "tasks"), detail.getWorkflows());
        }

        @Test
        void fullConstructorNullSafe() {
            ConfigProfileDetail detail = new ConfigProfileDetail(null, null, null);

            assertEquals("", detail.getName());
            assertEquals("", detail.getDescription());
            assertTrue(detail.getWorkflows().isEmpty());
        }
    }
}
