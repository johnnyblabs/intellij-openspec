package com.johnnyblabs.openspec.services;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/** Every completed detection is announced once on the project message bus, found or not. */
public class CliDetectionTopicTest extends BasePlatformTestCase {

    public void testEachDetectionIsAnnouncedOnceWithItsResult() {
        List<String> events = new ArrayList<>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(CliDetectionService.TOPIC,
                (CliDetectionService.Listener) (available, version) -> events.add(available + " " + version));
        CliDetectionService svc = new CliDetectionService(getProject());
        svc.setIsWindowsForTest(false);

        svc.setProcessRunnerForTest(cmd -> cmd.getParametersList().getList().contains("--version") ? "openspec 1.14.3" : null);
        svc.detect();
        svc.setProcessRunnerForTest(cmd -> null);
        svc.detect();

        assertEquals(List.of("true 1.14.3", "false null"), events);
    }

    public void testASuccessfulTryPathIsAnnouncedToo() {
        List<String> events = new ArrayList<>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(CliDetectionService.TOPIC,
                (CliDetectionService.Listener) (available, version) -> events.add(available + " " + version));
        CliDetectionService svc = new CliDetectionService(getProject());
        svc.setIsWindowsForTest(false);
        svc.setProcessRunnerForTest(cmd -> "openspec 1.13.2");

        assertTrue(svc.tryPath("/custom/openspec"));
        assertEquals(List.of("true 1.13.2"), events);
    }
}
