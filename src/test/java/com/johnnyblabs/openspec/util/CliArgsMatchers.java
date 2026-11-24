package com.johnnyblabs.openspec.util;

import org.mockito.ArgumentMatchers;

import java.util.List;

/**
 * Mockito matchers for a stubbed {@code CliRunner.run(project, CliArgs)}: an invocation matches only when its
 * arguments, as built, are exactly the literal list given, so a stub never loosens to "any command".
 */
public final class CliArgsMatchers {

    private CliArgsMatchers() {
    }

    /** Matches a {@link CliArgs} whose requested arguments are exactly {@code expected}. */
    public static CliArgs argv(String... expected) {
        List<String> want = List.of(expected);
        return ArgumentMatchers.argThat(a -> a != null && a.requested().equals(want));
    }
}
