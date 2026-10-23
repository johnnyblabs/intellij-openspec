package com.johnnyblabs.openspec.services;

/** Test-only bridge to the package-private Claude parser, so service tests can read the live fixtures' text. */
final class DirectApiServiceTestAccess {
    private DirectApiServiceTestAccess() {
    }

    static String parseClaude(String body) throws Exception {
        var m = com.johnnyblabs.openspec.ai.DirectApiService.class.getDeclaredMethod("parseClaudeResponse", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, body);
    }
}
