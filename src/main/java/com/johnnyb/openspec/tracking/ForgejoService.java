package com.johnnyb.openspec.tracking;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import java.util.List;

@Service(Service.Level.PROJECT)
public final class ForgejoService {

    private static final Logger LOG = Logger.getInstance(ForgejoService.class);
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
    private final Project project;

    public ForgejoService(@NotNull Project project) {
        this.project = project;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public record IssueResult(int issueNumber, String issueUrl) {}

    public IssueResult createIssue(String title, String body, List<String> labels) throws IOException {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String baseUrl = settings.getForgejoUrl().replaceAll("/+$", "");
        String owner = settings.getForgejoOwner();
        String repo = settings.getForgejoRepo();
        String token = TrackerCredentialStore.getToken(TrackerType.FORGEJO);

        // Resolve label names to IDs
        JsonArray labelIds = new JsonArray();
        if (labels != null && !labels.isEmpty()) {
            try {
                JsonArray allLabels = getLabels(baseUrl, owner, repo, token);
                for (var element : allLabels) {
                    JsonObject label = element.getAsJsonObject();
                    String name = label.get("name").getAsString();
                    if (labels.contains(name)) {
                        labelIds.add(label.get("id").getAsLong());
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to resolve Forgejo labels, creating issue without labels", e);
            }
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("title", title);
        payload.addProperty("body", body);
        if (!labelIds.isEmpty()) {
            payload.add("labels", labelIds);
        }

        String url = baseUrl + "/api/v1/repos/" + owner + "/" + repo + "/issues";
        String response = apiCall("POST", url, token, GSON.toJson(payload));

        JsonObject result = GSON.fromJson(response, JsonObject.class);
        int number = result.get("number").getAsInt();
        String issueUrl = result.get("html_url").getAsString();
        return new IssueResult(number, issueUrl);
    }

    public void addComment(int issueNumber, String body) throws IOException {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String baseUrl = settings.getForgejoUrl().replaceAll("/+$", "");
        String owner = settings.getForgejoOwner();
        String repo = settings.getForgejoRepo();
        String token = TrackerCredentialStore.getToken(TrackerType.FORGEJO);

        JsonObject payload = new JsonObject();
        payload.addProperty("body", body);

        String url = baseUrl + "/api/v1/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/comments";
        apiCall("POST", url, token, GSON.toJson(payload));
    }

    public void updateIssue(int issueNumber, String state, List<String> addLabels) throws IOException {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String baseUrl = settings.getForgejoUrl().replaceAll("/+$", "");
        String owner = settings.getForgejoOwner();
        String repo = settings.getForgejoRepo();
        String token = TrackerCredentialStore.getToken(TrackerType.FORGEJO);

        // Add labels if specified
        if (addLabels != null && !addLabels.isEmpty()) {
            try {
                JsonArray allLabels = getLabels(baseUrl, owner, repo, token);
                JsonObject labelPayload = new JsonObject();
                JsonArray labelIds = new JsonArray();
                for (var element : allLabels) {
                    JsonObject label = element.getAsJsonObject();
                    if (addLabels.contains(label.get("name").getAsString())) {
                        labelIds.add(label.get("id").getAsLong());
                    }
                }
                if (!labelIds.isEmpty()) {
                    labelPayload.add("labels", labelIds);
                    String labelUrl = baseUrl + "/api/v1/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/labels";
                    apiCall("POST", labelUrl, token, GSON.toJson(labelPayload));
                }
            } catch (Exception e) {
                LOG.warn("Failed to add labels to Forgejo issue #" + issueNumber, e);
            }
        }

        // Update state if specified
        if (state != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("state", state);
            String url = baseUrl + "/api/v1/repos/" + owner + "/" + repo + "/issues/" + issueNumber;
            apiCall("PATCH", url, token, GSON.toJson(payload));
        }
    }

    public String testConnection(String url, String owner, String repo) throws IOException {
        String baseUrl = url.replaceAll("/+$", "");
        String token = TrackerCredentialStore.getToken(TrackerType.FORGEJO);
        String apiUrl = baseUrl + "/api/v1/repos/" + owner + "/" + repo;
        String response = apiCall("GET", apiUrl, token, null);
        JsonObject result = GSON.fromJson(response, JsonObject.class);
        String fullName = result.has("full_name") ? result.get("full_name").getAsString() : owner + "/" + repo;
        return "Connected to " + fullName;
    }

    private JsonArray getLabels(String baseUrl, String owner, String repo, String token) throws IOException {
        String url = baseUrl + "/api/v1/repos/" + owner + "/" + repo + "/labels?limit=50";
        String response = apiCall("GET", url, token, null);
        return GSON.fromJson(response, JsonArray.class);
    }

    private String apiCall(String method, String url, String token, String body) throws IOException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "token " + token)
                    .timeout(Duration.ofSeconds(30));

            switch (method) {
                case "GET" -> builder.GET();
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                default -> throw new IOException("Unsupported HTTP method: " + method);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("Forgejo API error " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Forgejo API call interrupted", e);
        }
    }
}
