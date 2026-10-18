package com.johnnyblabs.openspec.coordination;

import org.jetbrains.annotations.Nullable;

/**
 * One entry of the uniform diagnostic envelope emitted by every OpenSpec 1.5 command —
 * {@code status: [{severity, code, message, target, fix}]}. Each field is nullable because
 * the CLI omits a key when it has no value (e.g. a diagnostic without a ready-made
 * {@code fix} suggestion).
 *
 * <p>The surface is read-only: the {@link #fix} string is retained purely for display as
 * guidance against the affected store or workset; it is never executed.
 *
 * @param severity the severity level (e.g. {@code "error"}, {@code "warning"}), or null
 * @param code     a stable machine code for the diagnostic (e.g. {@code "store_metadata_missing"})
 * @param message  the human-readable message, or null
 * @param target   the affected sub-target (e.g. {@code "store.metadata"}), or null
 * @param fix      a ready-made remediation suggestion, or null when none is offered
 */
public record Diagnostic(
        @Nullable String severity,
        @Nullable String code,
        @Nullable String message,
        @Nullable String target,
        @Nullable String fix) {
    /** The severity of {@link #unknown}: the plugin's own, never one the CLI emits. */
    public static final String UNKNOWN = "unknown";

    /** Health could not be determined: the doctor run failed or printed nothing readable. */
    public static Diagnostic unknown(String reason) {
        return new Diagnostic(UNKNOWN, null, reason, null, null);
    }

    /** Whether this is {@link #unknown} health rather than a diagnostic the CLI reported. */
    public boolean isUnknown() {
        return UNKNOWN.equals(severity);
    }
}
