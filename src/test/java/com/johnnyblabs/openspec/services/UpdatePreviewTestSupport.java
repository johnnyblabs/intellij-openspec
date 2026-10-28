package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.util.CliRunner;

import java.util.function.Predicate;

/** Drives {@link UpdatePreviewService} from tests in other packages; its seam types are package-private. */
public final class UpdatePreviewTestSupport {

    private UpdatePreviewTestSupport() {
    }

    /**
     * Makes {@code service} answer the dry run with {@code dryRun} and an apply with {@code apply}, run its background
     * and UI hops synchronously, and decide Apply or Cancel with {@code decider}, which is called while the preview is
     * shown.
     */
    public static void answer(UpdatePreviewService service, CliRunner.CliResult dryRun, CliRunner.CliResult apply,
                              Predicate<String> decider) {
        service.setSeamsForTest((project, env, args) -> args.length > 1 && "--dry-run".equals(args[1]) ? dryRun : apply,
                model -> decider.test(model.summary()));
    }
}
