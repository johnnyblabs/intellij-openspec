package com.johnnyblabs.openspec.services;

/** Test-only helpers for {@link CliDetectionService}, whose process-runner seam is package-private. */
public final class CliDetectionTestSupport {

    private CliDetectionTestSupport() {
    }

    /**
     * Makes {@code service} report an available CLI at {@code version} without spawning anything: every
     * {@code --version} probe answers {@code "openspec <version>"} and every other command finds nothing. A later
     * {@code detect()} lands on the same state, so a panel re-detecting in the background can't race it away.
     */
    public static void fakeAvailable(CliDetectionService service, String version) {
        service.setProcessRunnerForTest(cmd -> cmd.getParametersList().getList().contains("--version")
                ? "openspec " + version : null);
        service.tryPath("openspec");
    }
}
