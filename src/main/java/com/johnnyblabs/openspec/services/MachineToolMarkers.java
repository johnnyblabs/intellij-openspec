package com.johnnyblabs.openspec.services;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The paths the OpenSpec CLI's skills installer checks to call a tool installed on this machine ({@code ~/.claude},
 * {@code ~/.cursor}, {@code $XDG_CONFIG_HOME/zed}), with the tools each one marks. The CLI's own tool picker ticks
 * those tools when it moves a machine-wide project into the project, and so does the plugin's.
 * <p>
 * Shipped as {@code openspec/machine-tool-markers.txt}, written by {@code scripts/capture-skills-scope.py} from the
 * newest captured release. Only markers under the home folder or the config home are kept; the CLI's other checks
 * (fixed system paths, project files) are not machine evidence the plugin reads.
 */
public final class MachineToolMarkers {

    static final String RESOURCE = "/openspec/machine-tool-markers.txt";

    /** Where a marker's path starts. */
    public enum Base { HOME, CONFIG_HOME }

    /** One marker: a path under {@code base}, and the tool ids it marks installed. */
    public record Marker(Base base, String path, List<String> tools) {
    }

    private MachineToolMarkers() {
    }

    private static final class Holder {
        static final List<Marker> ALL = load();
    }

    public static List<Marker> all() {
        return Holder.ALL;
    }

    static List<Marker> load() {
        try (InputStream in = MachineToolMarkers.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("missing resource " + RESOURCE);
            List<Marker> out = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t");
                if (f.length != 3) throw new IllegalStateException("bad marker line: " + line);
                Base base = switch (f[0]) {
                    case "home" -> Base.HOME;
                    case "configHome" -> Base.CONFIG_HOME;
                    default -> throw new IllegalStateException("bad marker base: " + line);
                };
                out.add(new Marker(base, f[1], List.of(f[2].split(","))));
            }
            return List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The config home the installer reads: {@code $XDG_CONFIG_HOME} when set, otherwise {@code ~/.config}.
     *
     * @param xdgConfigHome the variable's value, or {@code null}
     */
    public static @Nullable Path configHome(@Nullable Path home, @Nullable String xdgConfigHome) {
        if (xdgConfigHome != null && !xdgConfigHome.isBlank()) return Path.of(xdgConfigHome);
        return home == null ? null : home.resolve(".config");
    }

    /** The tool ids whose marker exists, in marker order, each once. Reads the disk: never on the EDT. */
    public static Set<String> installed(@Nullable Path home, @Nullable String xdgConfigHome) {
        Set<String> tools = new LinkedHashSet<>();
        Path config = configHome(home, xdgConfigHome);
        for (Marker m : all()) {
            Path root = m.base() == Base.HOME ? home : config;
            if (root != null && Files.exists(root.resolve(m.path()))) tools.addAll(m.tools());
        }
        return tools;
    }
}
