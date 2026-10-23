package com.johnnyblabs.openspec.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Artifacts whose output path is a glob (the CLI declares {@code specs} as {@code specs/**}{@code /*.md}): one file per
 * capability rather than a single file. An API call can't write files or look anything up, so for Direct API the
 * prompt asks for every file to start with a marker line naming its path, and supplies the context an agent would
 * look up itself. The response is parsed and every path validated before anything is written.
 * <p>
 * Pure (no IntelliJ Platform API), so the format and the path rules are unit- and contract-testable.
 */
public final class MultiFileArtifact {

    private MultiFileArtifact() {
    }

    /** One file parsed from a response: its path relative to the change directory, and its content. */
    public record FileEntry(String path, String content) {
    }

    private static final Pattern MARKER =
            Pattern.compile("^[ \\t]*<!--\\s*file:\\s*(.*?)\\s*-->[ \\t]*$", Pattern.MULTILINE);
    private static final Pattern WRAPPING_FENCE =
            Pattern.compile("\\A```[^\\n]*\\n(.*)\\n```\\s*\\z", Pattern.DOTALL);

    /** True when the path contains a glob metacharacter, so it names a set of files, not one. */
    public static boolean isGlob(String path) {
        return path != null && (path.indexOf('*') >= 0 || path.indexOf('?') >= 0 || path.indexOf('[') >= 0);
    }

    /**
     * Whether a {@code /}-separated relative path matches the glob: {@code **} matches across directories, {@code *}
     * and {@code ?} within one segment. Done on strings, not {@code PathMatcher}, so it behaves the same on Windows.
     */
    public static boolean matchesGlob(String glob, String relativePath) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                regex.append(".*");
                i++;
            } else if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return relativePath.matches(regex.toString());
    }

    /**
     * Parses a marked response into files, validating every path against {@code glob}. All-or-nothing: any invalid,
     * duplicate or empty entry rejects the whole response, so a caller never writes part of a bad answer.
     *
     * @throws IOException with a user-facing reason when the response names no files or a path is rejected
     */
    public static List<FileEntry> parse(String response, String glob) throws IOException {
        Matcher m = MARKER.matcher(response == null ? "" : response);
        List<int[]> spans = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        while (m.find()) {
            paths.add(m.group(1));
            spans.add(new int[]{m.start(), m.end()});
        }
        if (paths.isEmpty()) {
            throw new IOException("The response didn't name any files (expected \"<!-- file: "
                    + glob + " -->\" markers), so nothing was written");
        }
        List<FileEntry> files = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            String reason = pathProblem(path, glob);
            if (reason != null) {
                throw new IOException("Rejected file path \"" + path + "\": " + reason + "; nothing was written");
            }
            if (!seen.add(path)) {
                throw new IOException("The response names \"" + path + "\" twice; nothing was written");
            }
            int from = spans.get(i)[1];
            int to = i + 1 < spans.size() ? spans.get(i + 1)[0] : response.length();
            String content = cleanContent(response.substring(from, to));
            if (content.isBlank()) {
                throw new IOException("The response gave \"" + path + "\" no content; nothing was written");
            }
            files.add(new FileEntry(path, content));
        }
        return files;
    }

    /** Why a path is unacceptable, or {@code null} when it's a safe relative path matching the glob. */
    static String pathProblem(String path, String glob) {
        if (path.isEmpty()) return "the path is empty";
        if (path.indexOf('\\') >= 0) return "use / as the separator";
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) return "the path must be relative to the change";
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return "the path has an empty, \".\" or \"..\" segment";
            }
        }
        if (isGlob(path)) return "the path contains a wildcard";
        if (!matchesGlob(glob, path)) return "it doesn't match " + glob;
        return null;
    }

    private static String cleanContent(String raw) {
        String content = raw.replace("\r\n", "\n").strip();
        Matcher fence = WRAPPING_FENCE.matcher(content);
        if (fence.matches()) {
            content = fence.group(1).strip();
        }
        return content + "\n";
    }

    /**
     * Files under {@code baseDir} matching {@code glob}, as sorted {@code /}-separated relative paths. Never resolves
     * the glob itself to a {@code Path}. An unreadable or missing directory yields an empty list.
     */
    public static List<String> matchingFiles(Path baseDir, String glob) {
        if (baseDir == null || !Files.isDirectory(baseDir)) return List.of();
        try (Stream<Path> walk = Files.walk(baseDir)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> baseDir.relativize(p).toString().replace('\\', '/'))
                    .filter(rel -> matchesGlob(glob, rel))
                    .sorted()
                    .toList();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /**
     * The extra prompt section for a Direct API call that generates a glob-output artifact: the marker format, plus
     * what the model can't look up itself (upstream's instructions tell an agent to run {@code openspec list --specs}
     * and to skip files that already exist). Never used for copied prompts, where an agent writes its own files.
     */
    public static String directApiPromptSection(Path changeDir, String glob) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n---\n\n## Output format (Direct API)\n\n")
                .append("Your response is not read by a person: the plugin writes it to files. You can't run commands ")
                .append("or write files yourself, so the context you would look up is given below. Ignore any ")
                .append("instruction above to run a command or to announce progress; write only the files.\n\n")
                .append("Start each file with a marker on a line of its own, exactly:\n\n")
                .append("<!-- file: <path> -->\n\n")
                .append("where `<path>` is relative to the change directory and matches `").append(glob)
                .append("` (for specs: `specs/<capability-path>/spec.md`). Put the file's full content after the marker. ")
                .append("Write nothing before the first marker or between files, and don't wrap files in code fences.\n");

        Path specsRoot = mainSpecsRoot(changeDir);
        List<String> capabilities = capabilityPaths(specsRoot);
        sb.append("\n### Existing capabilities (under `openspec/specs/`)\n\n");
        if (capabilities.isEmpty()) {
            sb.append("None yet: every capability in the proposal is new.\n");
        } else {
            capabilities.forEach(c -> sb.append("- `").append(c).append("`\n"));
        }

        String proposal = readOrNull(changeDir == null ? null : changeDir.resolve("proposal.md"));
        List<String> named = proposal == null ? List.of()
                : capabilities.stream().filter(c -> namesCapability(proposal, c)).toList();
        if (!named.isEmpty()) {
            sb.append("\n### Main specs the proposal names\n\n")
                    .append("Copy whole requirement blocks from these into MODIFIED deltas.\n");
            for (String c : named) {
                String text = readOrNull(specsRoot.resolve(c).resolve("spec.md"));
                if (text != null) {
                    sb.append("\n#### openspec/specs/").append(c).append("/spec.md\n\n```\n")
                            .append(text.strip()).append("\n```\n");
                }
            }
        }

        List<String> existing = matchingFiles(changeDir, glob);
        sb.append("\n### Files this change already has\n\n");
        if (existing.isEmpty()) {
            sb.append("None.\n");
        } else {
            existing.forEach(f -> sb.append("- `").append(f).append("`\n"));
            sb.append("\nWrite only the capabilities that have no file yet.\n");
        }
        return sb.toString();
    }

    /** {@code <root>/openspec/specs} for a change at {@code <root>/openspec/changes/<name>}, or {@code null}. */
    static Path mainSpecsRoot(Path changeDir) {
        if (changeDir == null) return null;
        Path changes = changeDir.toAbsolutePath().normalize().getParent();
        if (changes == null || changes.getFileName() == null || !"changes".equals(changes.getFileName().toString())) {
            return null;
        }
        Path openspec = changes.getParent();
        return openspec == null ? null : openspec.resolve("specs");
    }

    /** Capability paths (directories holding a {@code spec.md}) under the main specs root, sorted. */
    static List<String> capabilityPaths(Path specsRoot) {
        return matchingFiles(specsRoot, "**/spec.md").stream()
                .filter(rel -> rel.contains("/"))
                .map(rel -> rel.substring(0, rel.length() - "/spec.md".length()))
                .toList();
    }

    /** Whether the proposal names the capability path as a whole token (not as part of a longer name). */
    static boolean namesCapability(String proposal, String capability) {
        return Pattern.compile("(?<![\\w/-])" + Pattern.quote(capability) + "(?![\\w/-])").matcher(proposal).find();
    }

    private static String readOrNull(Path file) {
        try {
            return file != null && Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
