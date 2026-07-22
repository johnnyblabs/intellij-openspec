package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.Requirement;
import com.johnnyblabs.openspec.model.Scenario;
import com.johnnyblabs.openspec.model.SpecFile;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.util.SpecPatterns;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses OpenSpec spec markdown into the {@link SpecFile} model consumed by the tree, list,
 * and project surfaces.
 *
 * <p>This is a line-oriented scanner that mirrors the OpenSpec CLI's own parsing algorithm
 * rather than a general markdown/AST parser. The CLI recovers all spec structure with
 * anchored per-line matches over a hand-built code-fence mask and ships no markdown library;
 * a general CommonMark parser is <em>more</em> permissive than that dialect (setext headings,
 * indented-code headings, trailing-hash forms), so matching the CLI means scanning lines, not
 * building an AST. The recognition rules kept in lockstep with the CLI:
 * <ul>
 *   <li>Code fences are masked first; requirement headers, scenario headers, and normative
 *       keywords on fenced lines are not recognized.</li>
 *   <li>A requirement is a non-fenced ATX level-3 {@code ### Requirement:} header
 *       (case-insensitive token; see {@link SpecPatterns}).</li>
 *   <li>A scenario is <em>any</em> non-fenced ATX level-4 ({@code ####}) header — not only a
 *       {@code Scenario:}-labelled one, and the bold {@code **Scenario:**} form is not a
 *       scenario.</li>
 *   <li>The normative keyword is the whole-word, case-sensitive token {@code SHALL} or
 *       {@code MUST}, evaluated on the requirement body only (not the header or scenarios).
 *       {@code SHOULD} and {@code MAY} are not normative.</li>
 * </ul>
 *
 * <p>Parsing a main {@code spec.md} is a pure function of its content, so it runs entirely in
 * the IDE with no CLI round-trip. This service is deliberately separate from the validator's
 * own parse path ({@code BuiltInValidator}); unifying the two is tracked as a follow-up.
 */
@Service(Service.Level.PROJECT)
public final class SpecParsingService {
    private static final Logger LOG = Logger.getInstance(SpecParsingService.class);

    /** Level-1 ATX title: a single {@code #} then whitespace. {@code ## Purpose} does not match. */
    private static final Pattern TITLE_PATTERN = Pattern.compile("^#\\s+(.+)$");
    /** Any non-fenced level-4 ATX header is a scenario, regardless of its text. */
    private static final Pattern SCENARIO_HEADER = Pattern.compile("^####\\s+(.+)$");
    /** Upstream's normative test: the whole word SHALL or MUST, case-sensitive. */
    private static final Pattern NORMATIVE_KEYWORD = Pattern.compile("\\b(SHALL|MUST)\\b");
    /** A {@code **metadata**:} line inside a requirement body (excluded from body/keyword scope). */
    private static final Pattern METADATA_LINE = Pattern.compile("\\*\\*[^*]+\\*\\*:.*");
    /** A GIVEN/WHEN/THEN/AND clause line under a scenario. */
    private static final Pattern CLAUSE =
            Pattern.compile("^-\\s+\\*{0,2}(GIVEN|WHEN|THEN|AND)\\*{0,2}\\s+(.+)$");

    private final Project project;

    public SpecParsingService(Project project) {
        this.project = project;
    }

    public List<SpecFile> parseAllSpecs() {
        List<SpecFile> specs = new ArrayList<>();
        VirtualFile specsDir = OpenSpecFileUtil.getSpecsDir(project);
        if (specsDir == null || !specsDir.exists()) return specs;

        for (VirtualFile domainDir : specsDir.getChildren()) {
            if (!domainDir.isDirectory()) continue;
            VirtualFile specMd = domainDir.findChild("spec.md");
            if (specMd != null) {
                SpecFile parsed = parseSpec(specMd, domainDir.getName());
                if (parsed != null) {
                    specs.add(parsed);
                }
            }
        }
        return specs;
    }

    public SpecFile parseSpec(VirtualFile file, String domain) {
        try {
            String content;
            if (ApplicationManager.getApplication() == null
                    || ApplicationManager.getApplication().isDispatchThread()) {
                content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            } else {
                content = ReadAction.computeCancellable(() -> new String(file.contentsToByteArray(), StandardCharsets.UTF_8));
            }
            return parseSpecContent(content, domain, file.getPath());
        } catch (IOException e) {
            LOG.error("Failed to read spec file: " + file.getPath(), e);
            return null;
        }
    }

    public SpecFile parseSpecContent(String content, String domain, String filePath) {
        SpecFile spec = new SpecFile(domain, filePath);
        if (content == null || content.isEmpty()) return spec;

        // Normalize line endings so LF, CRLF, and lone-CR all parse identically.
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        boolean[] fenced = computeFenceMask(lines);

        // Title: the first non-fenced level-1 ATX header.
        for (int i = 0; i < lines.length; i++) {
            if (fenced[i]) continue;
            Matcher tm = TITLE_PATTERN.matcher(lines[i]);
            if (tm.matches()) {
                spec.setTitle(tm.group(1).trim());
                break;
            }
        }

        // Requirement header line indices (non-fenced, ATX level-3, case-insensitive token).
        List<Integer> reqLines = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (!fenced[i] && SpecPatterns.requirementName(lines[i]) != null) {
                reqLines.add(i);
            }
        }

        for (int r = 0; r < reqLines.size(); r++) {
            int headerIdx = reqLines.get(r);
            int sectionEnd = (r + 1 < reqLines.size()) ? reqLines.get(r + 1) : lines.length;
            Requirement req = new Requirement(SpecPatterns.requirementName(lines[headerIdx]));

            // Scenario headers within this requirement: any non-fenced level-4 header.
            List<Integer> scenarioLines = new ArrayList<>();
            for (int i = headerIdx + 1; i < sectionEnd; i++) {
                if (!fenced[i] && SCENARIO_HEADER.matcher(lines[i]).matches()) {
                    scenarioLines.add(i);
                }
            }

            // Body: non-fenced, non-blank, non-metadata lines from after the header up to the
            // first scenario (or the next requirement). The normative keyword is tested here only.
            int bodyEnd = scenarioLines.isEmpty() ? sectionEnd : scenarioLines.get(0);
            StringBuilder body = new StringBuilder();
            for (int i = headerIdx + 1; i < bodyEnd; i++) {
                if (fenced[i] || lines[i].isBlank()) continue;
                if (METADATA_LINE.matcher(lines[i].stripLeading()).matches()) continue;
                if (body.length() > 0) body.append('\n');
                body.append(lines[i]);
            }
            String bodyText = body.toString().trim();
            req.setBody(bodyText);

            Matcher km = NORMATIVE_KEYWORD.matcher(bodyText);
            if (km.find()) {
                String kw = km.group(1);
                // Preserve the negative-form display when the keyword is immediately negated.
                if (bodyText.substring(km.end()).matches("(?s)\\s+NOT\\b.*")) {
                    kw = kw + " NOT";
                }
                req.setKeyword(kw);
            }

            for (int s = 0; s < scenarioLines.size(); s++) {
                int scHeader = scenarioLines.get(s);
                int scEnd = (s + 1 < scenarioLines.size()) ? scenarioLines.get(s + 1) : sectionEnd;
                Matcher scm = SCENARIO_HEADER.matcher(lines[scHeader]);
                String rawName = scm.matches() ? scm.group(1).trim() : lines[scHeader].trim();
                // Drop a leading "Scenario:" label for display; keep the header text otherwise.
                String scName = rawName.replaceFirst("(?i)^scenario:\\s*", "").trim();
                if (scName.isEmpty()) scName = rawName;
                Scenario scenario = new Scenario(scName);

                for (int i = scHeader + 1; i < scEnd; i++) {
                    if (fenced[i]) continue;
                    Matcher cm = CLAUSE.matcher(lines[i]);
                    if (cm.matches()) {
                        scenario.addClause(cm.group(1) + " " + cm.group(2).trim());
                    }
                }
                req.addScenario(scenario);
            }

            spec.addRequirement(req);
        }

        return spec;
    }

    /**
     * Per-line code-fence mask, mirroring {@code BuiltInValidator.maskFences}: a fence opens on a
     * line whose leading-whitespace-stripped text starts with {@code ```} or {@code ~~~}, and
     * closes on a later line starting with the same marker. Fence delimiter lines are masked too.
     * Only leading whitespace is stripped, so a {@code > ```} inside a blockquote is not a fence —
     * matching the CLI's flat line scan.
     */
    private static boolean[] computeFenceMask(String[] lines) {
        boolean[] fenced = new boolean[lines.length];
        String openMarker = null;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].stripLeading();
            boolean fenceLine = trimmed.startsWith("```") || trimmed.startsWith("~~~");
            if (openMarker == null) {
                if (fenceLine) {
                    openMarker = trimmed.startsWith("```") ? "```" : "~~~";
                    fenced[i] = true;
                }
            } else {
                fenced[i] = true;
                if (fenceLine && trimmed.startsWith(openMarker)) {
                    openMarker = null;
                }
            }
        }
        return fenced;
    }
}
