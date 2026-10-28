package com.johnnyblabs.openspec.services;

/** Test-only helpers for {@link CliDetectionService}, whose process-runner seam is package-private. */
public final class CliDetectionTestSupport {

    private CliDetectionTestSupport() {
    }

    /**
     * Makes {@code service} report an available CLI at {@code version} without spawning anything: every
     * {@code --version} probe answers {@code "openspec <version>"} and every other command finds nothing (the IDE-PATH
     * lookup included, so the machine's real CLI is never seen). A later
     * {@code detect()} lands on the same state, so a panel re-detecting in the background can't race it away.
     */
    public static void fakeAvailable(CliDetectionService service, String version) {
        service.setIdePathForTest(name -> null, () -> "");
        service.setProcessRunnerForTest(cmd -> cmd.getParametersList().getList().contains("--version")
                ? "openspec " + version : null);
        service.tryPath("openspec");
    }

    /** Makes {@code service} report no CLI, whatever is installed on the machine running the tests. */
    public static void fakeUnavailable(CliDetectionService service) {
        service.setIdePathForTest(name -> null, () -> "");
        service.setProcessRunnerForTest(cmd -> null);
        service.detect();
    }

    /** A process runner usable from other packages (the service's own runner type is package-private). */
    @FunctionalInterface
    public interface Runner {
        String run(com.intellij.execution.configurations.GeneralCommandLine cmd) throws Exception;
    }

    /** Installs {@code runner} as {@code service}'s process runner. */
    public static void setRunner(CliDetectionService service, Runner runner) {
        service.setProcessRunnerForTest(runner::run);
    }

    /** The command the login-shell PATH lookup runs, for tests outside this package that count shell calls. */
    public static String printPathCommand() {
        return CliDetectionService.PRINT_PATH;
    }

    /** Shell output carrying {@code path} between the lookup's markers. */
    public static String markedPath(String path) {
        return CliDetectionService.PATH_BEGIN + path + CliDetectionService.PATH_END;
    }
}
