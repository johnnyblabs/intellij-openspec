package com.johnnyb.openspec.tracking;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service(Service.Level.PROJECT)
public final class PlaneService {

    private static final Logger LOG = Logger.getInstance(PlaneService.class);
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
    private final Project project;

    public PlaneService(@NotNull Project project) {
        this.project = project;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public record WorkItemResult(String workItemId, String workItemUrl) {}

    public WorkItemResult createWorkItem(String title, String descriptionHtml) throws IOException {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String baseUrl = settings.getPlaneUrl().replaceAll("/+$", "");
        String workspace = settings.getPlaneWorkspace();
        String projectId = settings.getPlaneProject();
        String apiKey = TrackerCredentialStore.getToken(TrackerType.PLANE);

        JsonObject payload = new JsonObject();
        payload.addProperty("name", title);
        payload.addProperty("description_html", descriptionHtml != null ? descriptionHtml : "");

        String url = baseUrl + "/api/v1/workspaces/" + workspace + "/projects/" + projectId + "/work-items/";
        String response = apiCall("POST", url, apiKey, GSON.toJson(payload));

        JsonObject result = GSON.fromJson(response, JsonObject.class);
        String id = result.get("id").getAsString();
        String workItemUrl = baseUrl + "/" + workspace + "/projects/" + projectId + "/work-items/" + id;
        return new WorkItemResult(id, workItemUrl);
    }

    public void updateWorkItemState(String workItemId, String stateName) throws IOException {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String baseUrl = settings.getPlaneUrl().replaceAll("/+$", "");
        String workspace = settings.getPlaneWorkspace();
        String projectId = settings.getPlaneProject();
        String apiKey = TrackerCredentialStore.getToken(TrackerType.PLANE);

        // Resolve state name to ID
        String stateId = resolveStateId(baseUrl, workspace, projectId, apiKey, stateName);
        if (stateId == null) {
            LOG.warn("Could not resolve Plane state '" + stateName + "', skipping update");
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("state", stateId);

        String url = baseUrl + "/api/v1/workspaces/" + workspace + "/projects/" + projectId + "/work-items/" + workItemId + "/";
        apiCall("PATCH", url, apiKey, GSON.toJson(payload));
    }

    /**
     * Checks if a work item is already in the given state (idempotency guard).
     */
    public boolean isWorkItemInState(String workItemId, String stateName) throws IOException {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String baseUrl = settings.getPlaneUrl().replaceAll("/+$", "");
        String workspace = settings.getPlaneWorkspace();
        String projectId = settings.getPlaneProject();
        String apiKey = TrackerCredentialStore.getToken(TrackerType.PLANE);

        // Get work item current state
        String url = baseUrl + "/api/v1/workspaces/" + workspace + "/projects/" + projectId + "/work-items/" + workItemId + "/";
        String response = apiCall("GET", url, apiKey, null);
        JsonObject workItem = GSON.fromJson(response, JsonObject.class);
        if (!workItem.has("state")) return false;

        String currentStateId = workItem.get("state").getAsString();

        // Resolve target state name to ID and compare
        String targetStateId = resolveStateId(baseUrl, workspace, projectId, apiKey, stateName);
        return currentStateId.equals(targetStateId);
    }

    public String testConnection(String url, String workspace, String projectId) throws IOException {
        String baseUrl = url.replaceAll("/+$", "");
        String apiKey = TrackerCredentialStore.getToken(TrackerType.PLANE);
        String apiUrl = baseUrl + "/api/v1/workspaces/" + workspace + "/projects/" + projectId + "/";
        String response = apiCall("GET", apiUrl, apiKey, null);
        JsonObject result = GSON.fromJson(response, JsonObject.class);
        String name = result.has("name") ? result.get("name").getAsString() : projectId;
        return "Connected to " + name;
    }

    private String resolveStateId(String baseUrl, String workspace, String projectId, String apiKey, String stateName) throws IOException {
        String url = baseUrl + "/api/v1/workspaces/" + workspace + "/projects/" + projectId + "/states/";
        String response = apiCall("GET", url, apiKey, null);
        JsonObject wrapper = GSON.fromJson(response, JsonObject.class);

        JsonArray results;
        if (wrapper.has("results")) {
            results = wrapper.getAsJsonArray("results");
        } else {
            // Try parsing as direct array
            results = GSON.fromJson(response, JsonArray.class);
        }

        for (JsonElement element : results) {
            JsonObject state = element.getAsJsonObject();
            if (state.has("name") && state.get("name").getAsString().equalsIgnoreCase(stateName)) {
                return state.get("id").getAsString();
            }
        }
        return null;
    }

    private String apiCall(String method, String url, String apiKey, String body) throws IOException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", apiKey)
                    .timeout(Duration.ofSeconds(30));

            switch (method) {
                case "GET" -> builder.GET();
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                default -> throw new IOException("Unsupported HTTP method: " + method);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("Plane API error " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Plane API call interrupted", e);
        }
    }

    /**
     * Converts basic markdown to HTML for Plane's description_html field.
     * Handles headings, bold, italic, unordered lists, and paragraphs.
     */
    public static String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";

        StringBuilder html = new StringBuilder();
        String[] lines = markdown.split("\n");
        boolean inList = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // Close list if we've left a list block
            if (inList && !trimmed.startsWith("- ") && !trimmed.startsWith("* ")) {
                html.append("</ul>\n");
                inList = false;
            }

            if (trimmed.isEmpty()) {
                continue;
            } else if (trimmed.startsWith("## ")) {
                html.append("<h2>").append(inlineMarkdown(trimmed.substring(3))).append("</h2>\n");
            } else if (trimmed.startsWith("# ")) {
                html.append("<h1>").append(inlineMarkdown(trimmed.substring(2))).append("</h1>\n");
            } else if (trimmed.startsWith("### ")) {
                html.append("<h3>").append(inlineMarkdown(trimmed.substring(4))).append("</h3>\n");
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inList) {
                    html.append("<ul>\n");
                    inList = true;
                }
                html.append("<li>").append(inlineMarkdown(trimmed.substring(2))).append("</li>\n");
            } else {
                html.append("<p>").append(inlineMarkdown(trimmed)).append("</p>\n");
            }
        }

        if (inList) {
            html.append("</ul>\n");
        }

        return html.toString();
    }

    private static String inlineMarkdown(String text) {
        // Bold: **text** → <strong>text</strong>
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        // Italic: *text* → <em>text</em>
        text = text.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        // Inline code: `text` → <code>text</code>
        text = text.replaceAll("`(.+?)`", "<code>$1</code>");
        return text;
    }
}
