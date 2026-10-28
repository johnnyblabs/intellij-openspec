package com.johnnyblabs.openspec.settings;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Settings' Detect button is the user asking to find the CLI again, so it reads the login shell's PATH afresh even
 * when a recent one would otherwise be reused. Opening the panel does not force it.
 */
public class OpenSpecSettingsPanelDetectTest extends BasePlatformTestCase {

    private final AtomicInteger shells = new AtomicInteger();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CliDetectionService detection = new CliDetectionService(getProject());
        detection.setIsWindowsForTest(false);
        detection.setEnvForTest(name -> name.equals("SHELL") ? "/bin/zsh" : null);
        detection.setUserHomeForTest(null);
        detection.setIdePathForTest(name -> null, () -> "");
        detection.setExecutableCheckForTest(path -> false);
        Instant fixed = Instant.parse("2026-10-27T10:00:00Z");
        detection.setClockForTest(() -> fixed);
        CliDetectionTestSupport.setRunner(detection, cmd -> {
            if (cmd.getParametersList().getList().contains(CliDetectionTestSupport.printPathCommand())) {
                shells.incrementAndGet();
                return CliDetectionTestSupport.markedPath("/usr/bin");
            }
            return null;
        });
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
    }

    private static List<JButton> buttons(Component c, String text, List<JButton> out) {
        if (c instanceof JButton b && text.equals(b.getText())) out.add(b);
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) buttons(child, text, out);
        }
        return out;
    }

    private void waitForShells(int expected) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (shells.get() < expected && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
    }

    public void testTheDetectButtonReadsTheShellPathAgain() throws Exception {
        getProject().getService(CliDetectionService.class).detect();
        assertEquals("the first detection resolves the shell PATH", 1, shells.get());
        getProject().getService(CliDetectionService.class).detect();
        assertEquals("an ordinary detection inside the window reuses it", 1, shells.get());

        OpenSpecSettingsPanel panel = new OpenSpecSettingsPanel(getProject());
        List<JButton> detect = buttons(panel.getPanel(), "Detect", new ArrayList<>());
        assertEquals(1, detect.size());
        detect.getFirst().doClick();
        waitForShells(2);

        assertEquals("Detect forces a fresh shell PATH", 2, shells.get());
    }
}
