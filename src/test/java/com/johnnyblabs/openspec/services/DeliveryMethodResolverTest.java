package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryMethodResolverTest {

    @Mock Project project;
    @Mock OpenSpecSettings settings;
    @Mock AiToolDetectionService detection;

    private DeliveryMethodResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DeliveryMethodResolver(project);
    }

    @Nested
    class PriorityChain {

        @Test
        void savedPreference_takesHighestPriority() {
            try (MockedStatic<OpenSpecSettings> settingsStatic = mockStatic(OpenSpecSettings.class)) {
                settingsStatic.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("EDITOR_TAB");

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.EDITOR_TAB, result.mode());
            }
        }

        @Test
        void apiProvider_takesSecondPriority() {
            try (MockedStatic<OpenSpecSettings> settingsStatic = mockStatic(OpenSpecSettings.class)) {
                settingsStatic.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("");
                when(settings.getAiProvider()).thenReturn("Claude");

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.DIRECT_API, result.mode());
                assertEquals("Generate via Claude", result.label());
            }
        }

        @Test
        void detectedTools_takeThirdPriority() {
            try (MockedStatic<OpenSpecSettings> settingsStatic = mockStatic(OpenSpecSettings.class)) {
                settingsStatic.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("");
                when(settings.getAiProvider()).thenReturn("NONE");
                when(project.getService(AiToolDetectionService.class)).thenReturn(detection);
                when(detection.hasDetectedTools()).thenReturn(true);
                when(detection.getPrimaryToolLabel()).thenReturn("GitHub Copilot");

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.CLIPBOARD, result.mode());
                assertEquals("Copy for GitHub Copilot", result.label());
            }
        }

        @Test
        void bareFallback_takesLowestPriority() {
            try (MockedStatic<OpenSpecSettings> settingsStatic = mockStatic(OpenSpecSettings.class)) {
                settingsStatic.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("");
                when(settings.getAiProvider()).thenReturn("NONE");
                when(project.getService(AiToolDetectionService.class)).thenReturn(detection);
                when(detection.hasDetectedTools()).thenReturn(false);

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.CLIPBOARD, result.mode());
                assertEquals("Copy to Clipboard", result.label());
            }
        }

        @Test
        void invalidSavedPreference_fallsThrough() {
            try (MockedStatic<OpenSpecSettings> settingsStatic = mockStatic(OpenSpecSettings.class)) {
                settingsStatic.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("INVALID_MODE_VALUE");
                when(settings.getAiProvider()).thenReturn("Claude");

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                // Falls through to API provider (second priority)
                assertEquals(DeliveryMode.DIRECT_API, result.mode());
                assertEquals("Generate via Claude", result.label());
            }
        }
    }

    @Nested
    class SavedPreferenceOverride {

        @Test
        void clipboardPreference_overridesApiAvailability() {
            try (MockedStatic<OpenSpecSettings> settingsStatic = mockStatic(OpenSpecSettings.class)) {
                settingsStatic.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("CLIPBOARD");

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.CLIPBOARD, result.mode());
                assertEquals("Copy to Clipboard", result.label());
            }
        }
    }

    @Nested
    class NullDetectionService {

        @Test
        void nullDetectionService_fallsToClipboard() {
            try (MockedStatic<OpenSpecSettings> settingsStatic = mockStatic(OpenSpecSettings.class)) {
                settingsStatic.when(() -> OpenSpecSettings.getInstance(project)).thenReturn(settings);
                when(settings.getPreferredDeliveryMethod()).thenReturn("");
                when(settings.getAiProvider()).thenReturn("NONE");
                when(project.getService(AiToolDetectionService.class)).thenReturn(null);

                DeliveryMethodResolver.ResolvedMethod result = resolver.resolve();

                assertEquals(DeliveryMode.CLIPBOARD, result.mode());
                assertEquals("Copy to Clipboard", result.label());
            }
        }
    }
}
