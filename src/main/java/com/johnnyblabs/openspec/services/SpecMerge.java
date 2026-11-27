package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.model.DeltaSpecOperation;
import com.johnnyblabs.openspec.model.DeltaSpecOperation.OperationType;
import com.johnnyblabs.openspec.util.CodeFences;
import com.johnnyblabs.openspec.util.SpecPatterns;
import com.johnnyblabs.openspec.validation.ValidationRelease;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The merge {@code openspec archive} performs, ported from OpenSpec 1.14.3 ({@code core/specs-apply.js},
 * {@code core/parsers/requirement-blocks.js}) so the plugin's Sync and its own archive write exactly what the CLI
 * would, and refuse exactly what it refuses. Pure: text in, text out; the contract tests run it against captured
 * archive runs ({@code fixtures/cli/archive-1.14.3/}).
 *
 * <p>Not ported: upstream's warnings about delta text the merge ignores (requirements outside a delta section,
 * headings absorbed into a requirement) and its structural check of the main spec before merging.
 */
public final class SpecMerge {

    /** The requirement header the delta reader recognizes; the {@code Requirement} token in any casing. */
    private static final Pattern REQUIREMENT_HEADER =
            Pattern.compile("^###\\s*Requirement:\\s*(.+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOP_LEVEL_HEADER = Pattern.compile("^##\\s+");
    private static final Pattern SECTION = Pattern.compile("^(##)\\s+(.+)$");
    /** A CommonMark closing {@code #} run after a heading's title, which OpenSpec ignores. */
    private static final Pattern CLOSING_RUN = Pattern.compile("\\s+#+\\s*$");
    private static final Pattern REQUIREMENTS_HEADER = Pattern.compile("^##\\s+Requirements(?:\\s+#+)?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PURPOSE_HEADER = Pattern.compile("^##\\s+Purpose(?:\\s+#+)?\\s*$", Pattern.CASE_INSENSITIVE);

    /** A level-2 heading's title with any closing {@code #} run removed, so {@code ## Purpose ##} reads as Purpose. */
    static String headingTitle(String title) {
        return CLOSING_RUN.matcher(title).replaceFirst("").strip();
    }
    private static final Pattern REMOVED_BULLET = Pattern.compile("^\\s*[-*+]\\s*`?###\\s*Requirement:\\s*(.+?)`?\\s*$");
    private static final Pattern RENAMED_FROM = Pattern.compile("^\\s*[-*+]?\\s*FROM:\\s*`?###\\s*Requirement:\\s*(.+?)`?\\s*$");
    private static final Pattern RENAMED_TO = Pattern.compile("^\\s*[-*+]?\\s*TO:\\s*`?###\\s*Requirement:\\s*(.+?)`?\\s*$");
    private static final Pattern SCENARIO_HEADER = Pattern.compile("^####\\s+");
    private static final Pattern CLOSING_HASHES = Pattern.compile("[ \\t]+#+[ \\t]*$");
    private static final Pattern SCENARIO_PREFIX = Pattern.compile("^Scenario:\\s*", Pattern.CASE_INSENSITIVE);

    static final String PURPOSE_PLACEHOLDER_PREFIX = "TBD - created by archiving change ";
    static final String PURPOSE_PLACEHOLDER_SUFFIX = ". Update Purpose after archive.";
    static final int MIN_PURPOSE_LENGTH = 50;
    private static final int MAX_LISTED_ADDED_SCENARIOS = 3;

    private SpecMerge() {
    }

    /**
     * One capability's merge. {@code projected} is the main spec after the change, or {@code null} when refused;
     * {@code refusal} is upstream's message for the first rule the delta breaks.
     */
    public record Result(String capability,
                         @Nullable String projected,
                         List<DeltaSpecOperation> operations,
                         List<String> warnings,
                         @Nullable String refusal,
                         boolean retired) {
        public Result(String capability, @Nullable String projected, List<DeltaSpecOperation> operations,
                      List<String> warnings, @Nullable String refusal) {
            this(capability, projected, operations, warnings, refusal, false);
        }

        public boolean refused() {
            return refusal != null;
        }
    }

    /** A requirement block: its header line, normalized name, and text from the header to the next boundary. */
    record Block(String headerLine, String name, String raw) {
    }

    record RenamePair(String from, String to) {
    }

    /** A RENAMED {@code FROM:} with no {@code TO:} after it (or a {@code TO:} with no {@code FROM:}), 1-based line. */
    public record Unpaired(String side, String name, int line) {
    }

    /** A parsed delta spec, as upstream's {@code parseDeltaSpec} reads it. */
    record Plan(List<Block> added, List<Block> modified, List<String> removed, Map<String, String> removedBlocks,
                List<RenamePair> renamed, List<Unpaired> unpaired) {
        boolean hasAnyDelta() {
            return !added.isEmpty() || !modified.isEmpty() || !removed.isEmpty() || !renamed.isEmpty();
        }
    }

    private static final class Refusal extends RuntimeException {
        Refusal(String message) {
            super(message, null, false, false);
        }
    }

    // --- names ---

    /** An ATX closing {@code #} run is not part of the name; then trimmed. Case is kept: names match exactly. */
    public static String normalizeName(String name) {
        return CLOSING_HASHES.matcher(name).replaceFirst("").strip();
    }

    /** Case- and whitespace-folded name, used only to refuse near-misses, never to match. */
    public static String foldName(String name) {
        return normalizeName(name).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * What a delta holds, read as OpenSpec's reader reads it: the delta sections present (as {@code ## <TYPE>
     * Requirements}, in OpenSpec's order), how many entries they parse to, and the unpaired renames. Shared by the
     * built-in validator and the delta inspection so they recognise entries exactly as the merge does.
     */
    public record DeltaSummary(List<String> sections, int entries, List<Unpaired> unpaired) {
    }

    private static final String[] SECTION_TITLES = {"ADDED", "MODIFIED", "REMOVED", "RENAMED"};

    public static DeltaSummary summarize(String deltaContent) {
        Plan plan = parseDelta(deltaContent);
        List<String> present = new ArrayList<>();
        List<String> lines = lines(deltaContent);
        boolean[] mask = CodeFences.mask(lines);
        Set<String> titles = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            if (mask[i]) continue;
            Matcher m = SECTION.matcher(lines.get(i));
            if (m.find()) titles.add(headingTitle(m.group(2)).toLowerCase(Locale.ROOT));
        }
        for (String t : SECTION_TITLES) {
            if (titles.contains(t.toLowerCase(Locale.ROOT) + " requirements")) present.add("## " + t + " Requirements");
        }
        int entries = plan.added().size() + plan.modified().size() + plan.removed().size() + plan.renamed().size();
        return new DeltaSummary(List.copyOf(present), entries, plan.unpaired());
    }

    /**
     * A requirement under a level-2 section that OpenSpec does not read as a delta section, so it is ignored. It
     * {@code fails} when the section title ends in "Requirements" (OpenSpec refuses the change); a requirement under
     * prose such as {@code ## Notes} is only a warning, as upstream reports it.
     */
    public record Orphan(String name, String section, int line, boolean fails) {
    }

    private static final Set<String> DELTA_HEADINGS = Set.of("added requirements", "modified requirements",
            "removed requirements", "renamed requirements");

    /** The requirements a delta holds outside its four delta sections, in document order. Fence-masked. */
    public static List<Orphan> orphans(String deltaContent) {
        // The CLI ignores requirements inside HTML comments, so a commented-out draft is not an orphan.
        List<String> lines = lines(maskHtmlComments(deltaContent));
        boolean[] mask = CodeFences.mask(lines);
        List<Orphan> found = new ArrayList<>();
        String section = null;
        for (int i = 0; i < lines.size(); i++) {
            if (mask[i]) continue;
            String line = lines.get(i);
            Matcher heading = SECTION.matcher(line);
            if (heading.find()) {
                section = headingTitle(heading.group(2));
                continue;
            }
            Matcher requirement = SpecPatterns.DELTA_REQUIREMENT_HEADER.matcher(line);
            if (section == null || !requirement.find()) continue;
            String title = section.toLowerCase(Locale.ROOT);
            if (DELTA_HEADINGS.contains(title)) continue;
            found.add(new Orphan(normalizeName(requirement.group(1)), section, i + 1, title.endsWith("requirements")));
        }
        return List.copyOf(found);
    }

    /** The message for an orphan, in OpenSpec's wording, so the plugin and the CLI report the same text. */
    public static String orphanMessage(Orphan orphan) {
        return "Requirement \"" + orphan.name() + "\" is under \"## " + orphan.section() + "\", which is not a delta "
                + "section, so it is ignored. Move it under \"## ADDED Requirements\", \"## MODIFIED Requirements\", "
                + "\"## REMOVED Requirements\", or \"## RENAMED Requirements\".";
    }

    // --- delta parsing ---

    /** The operations a delta holds, in apply order (RENAMED, REMOVED, MODIFIED, ADDED). */
    public static List<DeltaSpecOperation> operations(String capability, String delta) {
        return operations(capability, parseDelta(delta));
    }

    private static List<DeltaSpecOperation> operations(String capability, Plan plan) {
        List<DeltaSpecOperation> ops = new ArrayList<>();
        for (RenamePair r : plan.renamed()) {
            ops.add(new DeltaSpecOperation(OperationType.RENAMED, capability, r.from(), null, r.from(), r.to()));
        }
        for (String name : plan.removed()) {
            ops.add(new DeltaSpecOperation(OperationType.REMOVED, capability, name, plan.removedBlocks().get(name), null, null));
        }
        for (Block b : plan.modified()) {
            ops.add(new DeltaSpecOperation(OperationType.MODIFIED, capability, b.name(), b.raw(), null, null));
        }
        for (Block b : plan.added()) {
            ops.add(new DeltaSpecOperation(OperationType.ADDED, capability, b.name(), b.raw(), null, null));
        }
        return ops;
    }

    private record Body(List<String> lines, boolean[] mask, int startLine) {
    }

    static Plan parseDelta(String content) {
        List<String> lines = lines(content);
        boolean[] mask = CodeFences.mask(lines);
        Map<String, List<Body>> sections = new HashMap<>();
        List<int[]> headers = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (mask[i]) continue;
            Matcher m = SECTION.matcher(lines.get(i));
            if (m.find()) {
                headers.add(new int[]{i});
                titles.add(headingTitle(m.group(2)));
            }
        }
        for (int h = 0; h < headers.size(); h++) {
            int index = headers.get(h)[0];
            int end = h + 1 < headers.size() ? headers.get(h + 1)[0] : lines.size();
            sections.computeIfAbsent(titles.get(h).toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                    .add(new Body(lines.subList(index + 1, end), Arrays.copyOfRange(mask, index + 1, end), index + 2));
        }
        List<Block> added = new ArrayList<>();
        List<Block> modified = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        Map<String, String> removedBlocks = new HashMap<>();
        List<RenamePair> renamed = new ArrayList<>();
        List<Unpaired> unpaired = new ArrayList<>();
        for (Body b : sections.getOrDefault("added requirements", List.of())) added.addAll(blocks(b));
        for (Body b : sections.getOrDefault("modified requirements", List.of())) modified.addAll(blocks(b));
        for (Body b : sections.getOrDefault("removed requirements", List.of())) {
            removed.addAll(removedNames(b));
            for (Block block : blocks(b)) removedBlocks.putIfAbsent(block.name(), block.raw());
        }
        for (Body b : sections.getOrDefault("renamed requirements", List.of())) renamed.addAll(renamePairs(b, unpaired));
        unpaired.sort(Comparator.comparingInt(Unpaired::line));
        return new Plan(added, modified, removed, removedBlocks, renamed, unpaired);
    }

    private static boolean isRequirementHeader(List<String> lines, boolean[] mask, int i) {
        return !mask[i] && REQUIREMENT_HEADER.matcher(lines.get(i)).find();
    }

    private static boolean isTopLevelHeader(List<String> lines, boolean[] mask, int i) {
        return !mask[i] && TOP_LEVEL_HEADER.matcher(lines.get(i)).find();
    }

    private static List<Block> blocks(Body body) {
        List<String> lines = body.lines();
        boolean[] mask = body.mask();
        List<Block> blocks = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            while (i < lines.size() && !isRequirementHeader(lines, mask, i)) i++;
            if (i >= lines.size()) break;
            String header = lines.get(i);
            Matcher m = REQUIREMENT_HEADER.matcher(header);
            m.find();
            String name = normalizeName(m.group(1));
            List<String> buf = new ArrayList<>(List.of(header));
            i++;
            while (i < lines.size() && !isRequirementHeader(lines, mask, i) && !isTopLevelHeader(lines, mask, i)) {
                buf.add(lines.get(i));
                i++;
            }
            blocks.add(new Block(header, name, String.join("\n", buf).stripTrailing()));
        }
        return blocks;
    }

    private static List<String> removedNames(Body body) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < body.lines().size(); i++) {
            if (body.mask()[i]) continue;
            String line = body.lines().get(i);
            Matcher header = REQUIREMENT_HEADER.matcher(line);
            if (header.find()) {
                names.add(normalizeName(header.group(1)));
                continue;
            }
            Matcher bullet = REMOVED_BULLET.matcher(line);
            if (bullet.find()) names.add(normalizeName(bullet.group(1)));
        }
        return names;
    }

    private static List<RenamePair> renamePairs(Body body, List<Unpaired> unpaired) {
        List<RenamePair> pairs = new ArrayList<>();
        String pending = null;
        int pendingLine = 0;
        for (int i = 0; i < body.lines().size(); i++) {
            if (body.mask()[i]) continue;
            String line = body.lines().get(i);
            Matcher from = RENAMED_FROM.matcher(line);
            Matcher to = RENAMED_TO.matcher(line);
            if (from.find()) {
                if (pending != null) unpaired.add(new Unpaired("FROM", pending, pendingLine));
                pending = normalizeName(from.group(1));
                pendingLine = body.startLine() + i;
            } else if (to.find()) {
                String target = normalizeName(to.group(1));
                if (pending == null) {
                    unpaired.add(new Unpaired("TO", target, body.startLine() + i));
                    continue;
                }
                pairs.add(new RenamePair(pending, target));
                pending = null;
            }
        }
        if (pending != null) unpaired.add(new Unpaired("FROM", pending, pendingLine));
        return pairs;
    }

    // --- merge ---

    /**
     * Merges one capability's delta into its main spec ({@code null} when the capability is new), as
     * {@code openspec archive} would. Never throws for a delta problem: a refusal comes back in the result.
     */
    public static Result merge(String capability, String changeName, String delta, @Nullable String main) {
        return merge(capability, changeName, delta, main, "openspec/specs/" + capability + "/spec.md");
    }

    /** {@link #merge(String, String, String, String)} naming the main spec's path in warnings, as upstream does. */
    public static Result merge(String capability, String changeName, String delta, @Nullable String main, String mainSpecPath) {
        return merge(capability, changeName, delta, main, mainSpecPath, null);
    }

    /**
     * {@link #merge(String, String, String, String, String)} as {@code release}'s archive merges: from
     * {@link ValidationRelease#NEW_SPEC_LAYOUT} the delta's front matter stays with the change (with a warning), and a
     * new spec gets that release's layout and Purpose. {@code null}, or a release that can't be read, merges as the
     * releases before it do.
     */
    public static Result merge(String capability, String changeName, String delta, @Nullable String main, String mainSpecPath,
                               @Nullable String release) {
        return merge(capability, changeName, delta, main, mainSpecPath, release, RetirementMarker.NONE);
    }

    /**
     * {@link #merge(String, String, String, String, String, String)} with the change's {@code retire_capabilities}
     * marker: a run that removes the last requirement of an existing spec retires the capability (deletes its spec) or
     * refuses, as {@code release}'s {@code openspec archive} does, and never writes a spec with no requirements.
     */
    public static Result merge(String capability, String changeName, String delta, @Nullable String main, String mainSpecPath,
                               @Nullable String release, RetirementMarker marker) {
        boolean layout211 = ValidationRelease.has(release, ValidationRelease.NEW_SPEC_LAYOUT);
        List<String> warnings = new ArrayList<>();
        if (layout211) {
            if (com.johnnyblabs.openspec.util.FrontMatter.present(delta)) {
                warnings.add(capability + " - frontmatter (line 1) is not carried into the main spec. It stays with the change.");
            }
            delta = com.johnnyblabs.openspec.util.FrontMatter.blank(delta);
        }
        Plan plan = parseDelta(delta);
        List<DeltaSpecOperation> ops = operations(capability, plan);
        for (Orphan orphan : orphans(delta)) {
            if (orphan.fails()) {
                return new Result(capability, null, ops, List.copyOf(warnings),
                        capability + " validation failed - " + orphanMessage(orphan));
            }
        }
        try {
            String projected = apply(capability, changeName, delta, main, mainSpecPath, plan, warnings, layout211);
            if (main != null && !plan.removed().isEmpty() && requirementsSection(projected).blocks().isEmpty()) {
                return retirement(capability, main, release, marker, ops, List.copyOf(warnings));
            }
            if (main == null && requirementsSection(projected).blocks().isEmpty()) {
                return alreadyRetired(capability, release, marker, ops, List.copyOf(warnings));
            }
            return new Result(capability, projected, ops, List.copyOf(warnings), null);
        } catch (Refusal r) {
            return new Result(capability, null, ops, List.copyOf(warnings), r.getMessage());
        }
    }

    /**
     * A change's {@code retire_capabilities} marker as upstream's {@code readRetireCapabilitiesMarker} reads it:
     * declared, or not, with the reason when a marker is present but can't be honored.
     */
    public record RetirementMarker(boolean declared, @Nullable String invalidReason) {
        public static final RetirementMarker NONE = new RetirementMarker(false, null);
    }

    /**
     * A new spec the change would leave with no requirements (a removal from a capability whose spec is gone): the
     * release never writes it. From 1.8 a marked change archives without it, keeping the "ignored for new spec"
     * warning; otherwise it is refused.
     */
    private static Result alreadyRetired(String capability, @Nullable String release, RetirementMarker marker,
                                         List<DeltaSpecOperation> ops, List<String> warnings) {
        if (RetirementAudit.variantFor(release) != null && marker.declared()) {
            return new Result(capability, null, ops, warnings, null);
        }
        return new Result(capability, null, ops, warnings, "Rebuilt spec for '" + capability
                + "' failed validation. No files were changed. Run openspec validate " + capability
                + " after fixing the change deltas.");
    }

    /**
     * The outcome when the run removed an existing spec's last requirement: retired, or refused with
     * {@code release}'s archive message and fix ({@code archive_spec_validation_failed}).
     */
    private static Result retirement(String capability, String main, @Nullable String release, RetirementMarker marker,
                                     List<DeltaSpecOperation> ops, List<String> warnings) {
        String message = "Rebuilt spec for '" + capability + "' failed validation. No files were changed.";
        String generic = "Run openspec validate " + capability + " after fixing the change deltas.";
        RetirementAudit.Variant variant = RetirementAudit.variantFor(release);
        if (variant == null) return new Result(capability, null, ops, warnings, message + " " + generic);
        List<String> leftovers = RetirementAudit.leftovers(requirementsSection(main), variant);
        String invalid = marker.invalidReason() != null
                ? " The marker present now cannot be honored (" + marker.invalidReason() + ")." : "";
        if (marker.declared()) {
            if (leftovers.isEmpty()) return new Result(capability, "", ops, warnings, null, true);
            return new Result(capability, null, ops, warnings, message + " '" + capability + "' declares "
                    + "retire_capabilities, but the spec holds content the merge cannot safely account for and deleting "
                    + "the file would take with it: " + RetirementAudit.describe(leftovers, release) + ". Move it into "
                    + "`## Purpose` or a canonical requirement, or delete the spec by hand.");
        }
        if (leftovers.isEmpty()) {
            return new Result(capability, null, ops, warnings, message + " This change removes the last requirement '"
                    + capability + "' has. To retire the capability and delete its spec, add `retire_capabilities: true` "
                    + "to the change's .openspec.yaml (alongside its `schema:`, which that file requires), then rerun."
                    + invalid);
        }
        if (release == null || com.johnnyblabs.openspec.util.CliVersion.atLeast(release, "1.11.0")) {
            return new Result(capability, null, ops, warnings, message + " This change removes the last requirement '"
                    + capability + "' has, so the rebuilt spec has none left and cannot be written. Retiring the "
                    + "capability is what archive does instead, and it is refused while the spec holds content the merge "
                    + "cannot safely account for and deleting the file would take with it: "
                    + RetirementAudit.describe(leftovers, release) + ". Move it into `## Purpose` or a canonical "
                    + "requirement, or delete the spec by hand, then rerun." + invalid);
        }
        return new Result(capability, null, ops, warnings, message + " " + generic);
    }

    /** A MODIFIED requirement whose text grew past the limit; {@code line} is its header's 1-based line in the delta. */
    public record Growth(String name, int line) {
    }

    /**
     * CLI 2.1.1's MODIFIED growth check ({@code findMainSpecModifiedIssues}): each MODIFIED requirement whose text
     * ({@link com.johnnyblabs.openspec.util.RequirementText}) is over the limit and longer than the main spec's current
     * text for it. The current requirement is the one of that name, or the one the delta renamed into it, following
     * rename chains; a MODIFIED naming a name the delta renames away, or with no current requirement, is not checked.
     */
    public static List<Growth> modifiedGrowth(String delta, String main) {
        String blanked = com.johnnyblabs.openspec.util.FrontMatter.blank(delta);
        Plan plan = parseDelta(blanked);
        Map<String, Block> current = new HashMap<>();
        for (Block b : requirementsSection(com.johnnyblabs.openspec.util.FrontMatter.blank(main)).blocks()) {
            current.putIfAbsent(b.name(), b);
        }
        Map<String, String> renamedFrom = new HashMap<>();
        Set<String> renamedAway = new HashSet<>();
        for (RenamePair r : plan.renamed()) {
            renamedFrom.put(normalizeName(r.to()), normalizeName(r.from()));
            renamedAway.add(normalizeName(r.from()));
        }
        List<String> lines = lines(blanked);
        List<Growth> grown = new ArrayList<>();
        for (Block block : plan.modified()) {
            String name = normalizeName(block.name());
            if (renamedAway.contains(name)) continue;
            Block was = null;
            Set<String> visited = new HashSet<>();
            for (String key = name; key != null && visited.add(key); key = renamedFrom.get(key)) {
                was = current.get(key);
                if (was != null) break;
            }
            if (was == null) continue;
            String next = com.johnnyblabs.openspec.util.RequirementText.ofBlock(block.raw());
            int currentLength = com.johnnyblabs.openspec.util.RequirementText.ofBlock(was.raw()).length();
            if (!next.isEmpty() && com.johnnyblabs.openspec.util.RequirementText.tooLong(next)
                    && next.length() > currentLength) {
                grown.add(new Growth(block.name(), Math.max(1, lines.indexOf(block.headerLine()) + 1)));
            }
        }
        return grown;
    }

    private static String apply(String spec, String changeName, String delta, @Nullable String main, String mainSpecPath,
                                Plan plan, List<String> warnings, boolean layout211) {
        if (!plan.unpaired().isEmpty()) {
            Unpaired first = plan.unpaired().get(0);
            String missing = first.side().equals("FROM") ? "TO" : "FROM";
            throw new Refusal(spec + " validation failed - RENAMED entry on line " + first.line() + " has no matching "
                    + missing + ": for header \"### Requirement: " + first.name() + "\". "
                    + "Write each rename as a FROM: line followed immediately by its TO: line.");
        }

        Set<String> addedNames = new LinkedHashSet<>();
        for (Block add : plan.added()) {
            if (!addedNames.add(normalizeName(add.name()))) {
                throw new Refusal(spec + " validation failed - duplicate requirement in ADDED for header \"### Requirement: " + add.name() + "\"");
            }
        }
        Set<String> modifiedNames = new LinkedHashSet<>();
        for (Block mod : plan.modified()) {
            if (!modifiedNames.add(normalizeName(mod.name()))) {
                throw new Refusal(spec + " validation failed - duplicate requirement in MODIFIED for header \"### Requirement: " + mod.name() + "\"");
            }
        }
        Set<String> removedNames = new LinkedHashSet<>();
        for (String rem : plan.removed()) {
            if (!removedNames.add(normalizeName(rem))) {
                throw new Refusal(spec + " validation failed - duplicate requirement in REMOVED for header \"### Requirement: " + rem + "\"");
            }
        }
        Set<String> renamedFrom = new HashSet<>();
        Set<String> renamedTo = new HashSet<>();
        for (RenamePair r : plan.renamed()) {
            if (!renamedFrom.add(normalizeName(r.from()))) {
                throw new Refusal(spec + " validation failed - duplicate FROM in RENAMED for header \"### Requirement: " + r.from() + "\"");
            }
            if (!renamedTo.add(normalizeName(r.to()))) {
                throw new Refusal(spec + " validation failed - duplicate TO in RENAMED for header \"### Requirement: " + r.to() + "\"");
            }
        }

        List<String[]> conflicts = new ArrayList<>();
        for (String n : modifiedNames) {
            if (removedNames.contains(n)) conflicts.add(new String[]{n, "MODIFIED", "REMOVED"});
            if (addedNames.contains(n)) conflicts.add(new String[]{n, "MODIFIED", "ADDED"});
        }
        for (String n : addedNames) {
            if (removedNames.contains(n)) conflicts.add(new String[]{n, "ADDED", "REMOVED"});
        }
        for (RenamePair r : plan.renamed()) {
            String fromNorm = normalizeName(r.from());
            String toNorm = normalizeName(r.to());
            String removedFold = removedNames.stream().filter(x -> foldName(x).equals(foldName(fromNorm))).findFirst().orElse(null);
            if (removedFold != null) {
                throw new Refusal(spec + " validation failed - requirement present in multiple sections (RENAMED and REMOVED) for header \"### Requirement: "
                        + r.from() + "\"" + (removedFold.equals(fromNorm) ? "" : " (REMOVED spells it \"" + removedFold + "\")"));
            }
            if (modifiedNames.contains(fromNorm)) {
                throw new Refusal(spec + " validation failed - when a rename exists, MODIFIED must reference the NEW header \"### Requirement: " + r.to() + "\"");
            }
            if (addedNames.contains(toNorm)) {
                throw new Refusal(spec + " validation failed - RENAMED TO header collides with ADDED for \"### Requirement: " + r.to() + "\"");
            }
        }
        if (!conflicts.isEmpty()) {
            String[] c = conflicts.get(0);
            throw new Refusal(spec + " validation failed - requirement present in multiple sections (" + c[1] + " and " + c[2]
                    + ") for header \"### Requirement: " + c[0] + "\"");
        }
        if (!plan.hasAnyDelta()) {
            throw new Refusal("Delta parsing found no operations for " + spec + ". "
                    + "Provide ADDED/MODIFIED/REMOVED/RENAMED sections in change spec.");
        }

        String deltaPurpose = purposeSection(delta, layout211);
        String target;
        boolean isNewSpec = main == null;
        if (!isNewSpec) {
            target = main;
            if (deltaPurpose != null) {
                String existing = purposeSection(main, layout211);
                if (existing != null && !existing.equals(deltaPurpose)) {
                    warnings.add(spec + " - delta Purpose ignored; " + spec + " already has one. "
                            + "Edit " + mainSpecPath + " directly to change it.");
                }
            }
        } else {
            if (!plan.modified().isEmpty() || !plan.renamed().isEmpty()) {
                throw new Refusal(spec + ": target spec does not exist; only ADDED requirements are allowed for new specs. "
                        + "MODIFIED and RENAMED operations require an existing spec.");
            }
            if (!plan.removed().isEmpty()) {
                warnings.add(spec + " - " + plan.removed().size() + " REMOVED requirement(s) ignored for new spec (nothing to remove).");
            }
            target = skeleton(spec, changeName, deltaPurpose, layout211);
            if (deltaPurpose != null && !readablePurpose(deltaPurpose)) {
                target = skeleton(spec, changeName, null, layout211);
                warnings.add(spec + " - delta Purpose ignored (it would leave the new spec unreadable); wrote the placeholder Purpose instead.");
            } else if (deltaPurpose != null && deltaPurpose.strip().length() < MIN_PURPOSE_LENGTH) {
                warnings.add(spec + " - carried Purpose is under " + MIN_PURPOSE_LENGTH + " characters; "
                        + "openspec validate --strict reports it as too brief.");
            }
        }

        Parts parts = requirementsSection(target);
        LinkedHashMap<String, Block> byName = new LinkedHashMap<>();
        for (Block b : parts.blocks()) byName.put(normalizeName(b.name()), b);
        List<String> orderedKeys = new ArrayList<>();
        for (Block b : parts.blocks()) orderedKeys.add(normalizeName(b.name()));

        for (RenamePair r : plan.renamed()) {
            String from = normalizeName(r.from());
            String to = normalizeName(r.to());
            if (!byName.containsKey(from)) {
                if (byName.containsKey(to)) {
                    String nearMiss = byName.keySet().stream()
                            .filter(k -> !k.equals(to) && foldName(k).equals(foldName(from))).findFirst().orElse(null);
                    if (nearMiss != null) {
                        throw new Refusal(spec + " RENAMED failed for header \"### Requirement: " + r.from() + "\" - source not found, but \"### Requirement: "
                                + byName.get(nearMiss).name() + "\" exists; fix the header to match it exactly");
                    }
                    continue;
                }
                throw new Refusal(spec + " RENAMED failed for header \"### Requirement: " + r.from() + "\" - source not found");
            }
            if (byName.containsKey(to)) {
                throw new Refusal(spec + " RENAMED failed for header \"### Requirement: " + r.to() + "\" - target already exists");
            }
            String targetNearMiss = byName.keySet().stream()
                    .filter(k -> !k.equals(from) && foldName(k).equals(foldName(to))).findFirst().orElse(null);
            if (targetNearMiss != null) {
                throw new Refusal(spec + " RENAMED failed for header \"### Requirement: " + r.to() + "\" - \"### Requirement: "
                        + byName.get(targetNearMiss).name() + "\" already exists and differs only in case or spacing; choose a distinct name");
            }
            Block block = byName.get(from);
            String newHeader = "### Requirement: " + to;
            String[] rawLines = block.raw().split("\n", -1);
            rawLines[0] = newHeader;
            byName.remove(from);
            byName.put(to, new Block(newHeader, to, String.join("\n", rawLines)));
            int orderIndex = orderedKeys.indexOf(from);
            if (orderIndex >= 0) orderedKeys.set(orderIndex, to);
        }

        for (String name : plan.removed()) {
            String key = normalizeName(name);
            if (!byName.containsKey(key)) {
                if (!isNewSpec) {
                    String nearMiss = byName.keySet().stream()
                            .filter(k -> foldName(k).equals(foldName(key))).findFirst().orElse(null);
                    if (nearMiss != null) {
                        throw new Refusal(spec + " REMOVED failed for header \"### Requirement: " + name + "\" - not found, but \"### Requirement: "
                                + byName.get(nearMiss).name() + "\" exists; fix the header to match it exactly");
                    }
                    warnings.add(spec + " - REMOVED requirement \"" + name + "\" is not in the current spec; treating it as already removed.");
                }
                continue;
            }
            byName.remove(key);
        }

        for (Block mod : plan.modified()) {
            String key = normalizeName(mod.name());
            Block current = byName.get(key);
            if (current == null) {
                throw new Refusal(spec + " MODIFIED failed for header \"### Requirement: " + mod.name() + "\" - not found");
            }
            Matcher header = REQUIREMENT_HEADER.matcher(mod.raw().split("\n", -1)[0]);
            if (!header.find() || !normalizeName(header.group(1)).equals(key)) {
                throw new Refusal(spec + " MODIFIED failed for header \"### Requirement: " + mod.name() + "\" - header mismatch in content");
            }
            ScenarioDiff diff = diffScenarioNames(current.raw(), mod.raw());
            if (!diff.missing().isEmpty()) {
                throw new Refusal(spec + " MODIFIED failed for header \"### Requirement: " + mod.name()
                        + "\" - current spec contains scenario(s) not present in the modified block: " + quoted(diff.missing())
                        + ". " + describeScenarioBalance(diff)
                        + " Refresh the change spec before archiving to avoid dropping scenarios.");
            }
            byName.put(key, mod);
        }

        for (Block add : plan.added()) {
            String key = normalizeName(add.name());
            Block existing = byName.get(key);
            if (existing != null) {
                if (normalizeBlockRaw(existing.raw()).equals(normalizeBlockRaw(add.raw()))) continue;
                throw new Refusal(spec + " ADDED failed for header \"### Requirement: " + add.name() + "\" - already exists");
            }
            String nearMiss = byName.keySet().stream()
                    .filter(k -> foldName(k).equals(foldName(key))).findFirst().orElse(null);
            if (nearMiss != null) {
                throw new Refusal(spec + " ADDED failed for header \"### Requirement: " + add.name() + "\" - \"### Requirement: "
                        + byName.get(nearMiss).name() + "\" already exists and differs only in case or spacing; "
                        + "use MODIFIED with that exact header to change it, or choose a distinct name");
            }
            byName.put(key, add);
        }

        List<Block> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String key : orderedKeys) {
            Block replacement = byName.get(key);
            if (replacement != null) {
                kept.add(replacement);
                seen.add(key);
            }
        }
        for (Map.Entry<String, Block> e : byName.entrySet()) {
            if (!seen.contains(e.getKey())) kept.add(e.getValue());
        }
        List<String> bodyParts = new ArrayList<>();
        if (!parts.preamble().isBlank()) bodyParts.add(parts.preamble().stripTrailing());
        for (Block b : kept) bodyParts.add(b.raw());
        String reqBody = String.join("\n\n", bodyParts).stripTrailing();

        List<String> slices = new ArrayList<>();
        for (String s : new String[]{parts.before().stripTrailing(), parts.headerLine(), reqBody, parts.after().strip()}) {
            if (!s.isEmpty()) slices.add(s);
        }
        return collapseBlankRunsOutsideFences(String.join("\n\n", slices)).stripTrailing() + "\n";
    }

    // --- main spec parsing and rebuild helpers ---

    record Parts(String before, String headerLine, String preamble, List<Block> blocks, String after) {
    }

    static Parts requirementsSection(String content) {
        List<String> lines = lines(content);
        boolean[] mask = CodeFences.mask(lines);
        int header = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (!mask[i] && REQUIREMENTS_HEADER.matcher(lines.get(i)).find()) {
                header = i;
                break;
            }
        }
        if (header == -1) {
            String before = content.stripTrailing();
            return new Parts(before.isEmpty() ? "" : before + "\n\n", "## Requirements", "", List.of(), "\n");
        }
        int end = lines.size();
        for (int i = header + 1; i < lines.size(); i++) {
            if (!mask[i] && TOP_LEVEL_HEADER.matcher(lines.get(i)).find()) {
                end = i;
                break;
            }
        }
        List<String> body = lines.subList(header + 1, end);
        boolean[] bodyMask = Arrays.copyOfRange(mask, header + 1, end);
        int cursor = 0;
        List<String> preamble = new ArrayList<>();
        while (cursor < body.size() && !isRequirementHeader(body, bodyMask, cursor)) {
            preamble.add(body.get(cursor));
            cursor++;
        }
        List<Block> blocks = new ArrayList<>();
        while (cursor < body.size()) {
            if (!isRequirementHeader(body, bodyMask, cursor)) {
                cursor++;
                continue;
            }
            String headerLine = body.get(cursor);
            Matcher m = REQUIREMENT_HEADER.matcher(headerLine);
            m.find();
            String name = normalizeName(m.group(1));
            cursor++;
            List<String> buf = new ArrayList<>(List.of(headerLine));
            while (cursor < body.size() && !isRequirementHeader(body, bodyMask, cursor) && !isTopLevelHeader(body, bodyMask, cursor)) {
                buf.add(body.get(cursor));
                cursor++;
            }
            blocks.add(new Block(headerLine, name, String.join("\n", buf).stripTrailing()));
        }
        String before = String.join("\n", lines.subList(0, header));
        String after = String.join("\n", lines.subList(end, lines.size()));
        return new Parts(before.stripTrailing().isEmpty() ? before : before + "\n", lines.get(header),
                String.join("\n", preamble).stripTrailing(), blocks, after.startsWith("\n") ? after : "\n" + after);
    }

    /** A new main spec as upstream's {@code buildSpecSkeleton} writes it; {@code layout211}: a blank line after {@code ## Purpose}. */
    static String skeleton(String capability, String changeName, @Nullable String purpose, boolean layout211) {
        String body = purpose != null && !purpose.isBlank() ? purpose.strip()
                : PURPOSE_PLACEHOLDER_PREFIX + changeName + PURPOSE_PLACEHOLDER_SUFFIX;
        return "# " + capability + " Specification\n\n## Purpose\n" + (layout211 ? "\n" : "") + body + "\n\n## Requirements\n";
    }

    /**
     * The body of a {@code ## Purpose} section, ignoring fenced and commented-out headers; null when absent or empty.
     * {@code layout211}: upstream 2.1.1's reader, which first drops a byte-order mark and blanks the front matter
     * ({@link com.johnnyblabs.openspec.util.FrontMatter#blank} does both, in that order).
     */
    static @Nullable String purposeSection(String content, boolean layout211) {
        if (layout211) content = com.johnnyblabs.openspec.util.FrontMatter.blank(content);
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        List<String> original = List.of(normalized.split("\n", -1));
        List<String> masked = List.of(maskHtmlComments(normalized).split("\n", -1));
        boolean[] fence = CodeFences.mask(masked);
        int start = -1;
        for (int i = 0; i < masked.size(); i++) {
            if (!fence[i] && PURPOSE_HEADER.matcher(masked.get(i)).find()) {
                start = i;
                break;
            }
        }
        if (start == -1) return null;
        int end = masked.size();
        for (int i = start + 1; i < masked.size(); i++) {
            if (!fence[i] && TOP_LEVEL_HEADER.matcher(masked.get(i)).find()) {
                end = i;
                break;
            }
        }
        StringBuilder prose = new StringBuilder();
        for (int i = start + 1; i < end; i++) {
            if (!fence[i]) prose.append(masked.get(i)).append('\n');
        }
        if (prose.toString().isBlank()) return null;
        String body = String.join("\n", original.subList(start + 1, end)).strip();
        return body.isEmpty() ? null : body;
    }

    /**
     * Whether a carried Purpose leaves the new spec readable. Of the cases upstream's re-parse rejects, the one a
     * well-formed delta can hit is an HTML comment in the body (captured: {@code new-cap-unreadable-purpose}); headings
     * of any level are carried as written ({@code new-cap-purpose-h1}, {@code new-cap-purpose-heading}).
     */
    private static boolean readablePurpose(String purpose) {
        return !purpose.contains("<!--");
    }

    private static String maskHtmlComments(String content) {
        StringBuilder out = new StringBuilder(content.length());
        int index = 0;
        while (true) {
            int open = content.indexOf("<!--", index);
            if (open == -1) return out.append(content, index, content.length()).toString();
            out.append(content, index, open);
            int close = content.indexOf("-->", open + 4);
            int stop = close == -1 ? content.length() : close + 3;
            for (int i = open; i < stop; i++) out.append(content.charAt(i) == '\n' ? '\n' : ' ');
            if (close == -1) return out.toString();
            index = stop;
        }
    }

    record ScenarioDiff(List<String> missing, List<String> added, int currentCount, int incomingCount) {
    }

    static ScenarioDiff diffScenarioNames(String currentRaw, String incomingRaw) {
        List<String> current = scenarioNames(currentRaw);
        List<String> incoming = scenarioNames(incomingRaw);
        return new ScenarioDiff(unmatched(current, incoming), unmatched(incoming, current), current.size(), incoming.size());
    }

    private static List<String> unmatched(List<String> names, List<String> against) {
        Map<String, Integer> remaining = new HashMap<>();
        for (String n : against) remaining.merge(n, 1, Integer::sum);
        List<String> out = new ArrayList<>();
        for (String n : names) {
            int left = remaining.getOrDefault(n, 0);
            if (left > 0) remaining.put(n, left - 1);
            else out.add(n);
        }
        return out;
    }

    private static List<String> scenarioNames(String raw) {
        List<String> lines = lines(raw);
        boolean[] mask = CodeFences.mask(lines);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (mask[i] || !SCENARIO_HEADER.matcher(lines.get(i)).find()) continue;
            String text = SCENARIO_HEADER.matcher(lines.get(i)).replaceFirst("");
            text = CLOSING_HASHES.matcher(text).replaceFirst("");
            names.add(SCENARIO_PREFIX.matcher(text).replaceFirst("").strip());
        }
        return names;
    }

    static String describeScenarioBalance(ScenarioDiff diff) {
        String scale = "The modified block has " + count(diff.incomingCount()) + "; the current spec has " + count(diff.currentCount()) + ".";
        if (diff.added().isEmpty()) return scale + " It adds none.";
        List<String> listed = diff.added().subList(0, Math.min(MAX_LISTED_ADDED_SCENARIOS, diff.added().size()));
        int rest = diff.added().size() - MAX_LISTED_ADDED_SCENARIOS;
        String names = quoted(listed) + (rest > 0 ? " and " + rest + " more" : "");
        return scale + " It adds " + count(diff.added().size()) + " not in the current spec: " + names + ".";
    }

    private static String count(int n) {
        return n + " " + (n == 1 ? "scenario" : "scenarios");
    }

    private static String quoted(List<String> names) {
        StringJoiner j = new StringJoiner(", ");
        for (String n : names) j.add("\"" + n + "\"");
        return j.toString();
    }

    private static String normalizeBlockRaw(String raw) {
        return raw.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    static String collapseBlankRunsOutsideFences(String content) {
        List<String> lines = List.of(content.split("\n", -1));
        boolean[] mask = CodeFences.mask(lines);
        List<String> kept = new ArrayList<>();
        int blankRun = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (mask[i]) {
                blankRun = 0;
                kept.add(line);
                continue;
            }
            if (line.isEmpty()) {
                if (++blankRun > 1) continue;
                kept.add(line);
                continue;
            }
            blankRun = 0;
            kept.add(line);
        }
        return String.join("\n", kept);
    }

    /** Lines after dropping a UTF-8 BOM and normalizing line endings, as upstream reads them. */
    private static List<String> lines(String content) {
        String normalized = content.replaceFirst("^﻿", "").replace("\r\n", "\n").replace('\r', '\n');
        return List.of(normalized.split("\n", -1));
    }
}
