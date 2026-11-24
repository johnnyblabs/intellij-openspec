package com.johnnyblabs.openspec.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether a file is exactly one an OpenSpec release generated: a port of upstream's {@code isReleasedGeneratedContent}
 * ({@code dist/core/shared/generated-fingerprint.js}). From 2.0 the CLI reads a shared skills folder's wording only
 * from such a file, since only a release's own output says which tool wrote the folder.
 * <p>
 * The fingerprint set is upstream's {@code RELEASED_GENERATED_FINGERPRINTS}, shipped as
 * {@code openspec/released-skill-fingerprints.txt} and refreshed by {@code scripts/capture-released-fingerprints.py}.
 * The normalization and the set are contract-tested against vectors computed by upstream's own functions
 * ({@code fixtures/cli/<version>/fingerprints/vectors.json}). The set is read on first use; callers run off the EDT.
 */
public final class ReleasedSkillFingerprints {

    static final String RESOURCE = "/openspec/released-skill-fingerprints.txt";

    /** Upstream's {@code GENERATED_VERSION}: a full semantic version. */
    private static final Pattern GENERATED_VERSION = Pattern.compile(
            "^(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)"
                    + "(?:-(?:(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*))*))?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");

    /** The YAML front matter at the start of a file, shortest match, as upstream matches it. */
    private static final Pattern FRONTMATTER = Pattern.compile("^---\n[\\s\\S]*?\n---(?:\n|$)");

    /** The first {@code generatedBy:} line, with its value double-quoted, single-quoted or bare. */
    private static final Pattern VERSION_LINE = Pattern.compile(
            "^([ \\t]*generatedBy:[ \\t]*)(?:\"([^\"\\n]+)\"|'([^'\\n]+)'|([^\\s\"'#]+))[ \\t]*$", Pattern.MULTILINE);

    private ReleasedSkillFingerprints() {
    }

    private static final class Holder {
        static final Set<String> SET = load();
    }

    /** Whether {@code content}, after normalization, is a file some released OpenSpec version generated. */
    public static boolean isReleased(String content) {
        return Holder.SET.contains(fingerprint(content));
    }

    /** The number of fingerprints shipped, for tests. */
    static int size() {
        return Holder.SET.size();
    }

    /** Upstream's {@code normalizeGeneratedFile}: drop a leading BOM and turn CRLF into LF. */
    static String normalizeFile(String content) {
        String s = content.startsWith("\uFEFF") ? content.substring(1) : content;
        return s.replace("\r\n", "\n");
    }

    /**
     * Upstream's {@code normalizeGeneratedContent}: {@link #normalizeFile}, then a valid version in the front matter's
     * first {@code generatedBy:} line becomes {@code "<generated-version>"}. Other {@code generatedBy} text is kept.
     */
    static String normalize(String content) {
        String normalized = normalizeFile(content);
        Matcher fm = FRONTMATTER.matcher(normalized);
        if (!fm.find()) return normalized;
        String frontmatter = fm.group();
        Matcher line = VERSION_LINE.matcher(frontmatter);
        if (!line.find()) return normalized;
        String version = line.group(2) != null ? line.group(2) : line.group(3) != null ? line.group(3) : line.group(4);
        if (version == null || !GENERATED_VERSION.matcher(version).matches()) return normalized;
        String replaced = frontmatter.substring(0, line.start()) + line.group(1) + "\"<generated-version>\""
                + frontmatter.substring(line.end());
        return replaced + normalized.substring(frontmatter.length());
    }

    /** Upstream's {@code fingerprintGeneratedContent}: the first 16 hex digits of the normalized text's sha256. */
    static String fingerprint(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalize(content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    private static Set<String> load() {
        Set<String> set = new HashSet<>();
        try (InputStream in = ReleasedSkillFingerprints.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("missing resource " + RESOURCE);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                String s = line.strip();
                if (!s.isEmpty() && !s.startsWith("#")) set.add(s);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Set.copyOf(set);
    }
}
