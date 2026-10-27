package com.johnnyblabs.openspec.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-structure regression guards for the Direct-API-flow hardening. These lock the placement of
 * calls that plain behavioral tests cannot reach (they live on the EDT or inside Swing workers/tasks):
 * blocking credential I/O must sit off the EDT, per-chip Cancel must cancel the single generation
 * rather than the whole Generate-All flow, generation must honor cancellation, and tool-window
 * contents must own their disposable panels. Mirrors {@code WorkflowActionPanelTest.EdtThreadingSafety}.
 */
class DirectApiFlowStructuralGuardTest {

    private static String read(String relPath) throws IOException {
        return Files.readString(Path.of(relPath));
    }

    /** Extract a method body by its signature prefix, brace-matched from the first '{'. */
    private static String methodBody(String content, String signaturePrefix) {
        int sig = content.indexOf(signaturePrefix);
        assertTrue(sig >= 0, "expected to find method: " + signaturePrefix);
        int bodyStart = content.indexOf('{', sig);
        int depth = 0;
        for (int i = bodyStart; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return content.substring(bodyStart, i + 1);
            }
        }
        throw new AssertionError("unbalanced braces for: " + signaturePrefix);
    }

    private static final String SETTINGS =
            "src/main/java/com/johnnyblabs/openspec/settings/OpenSpecSettingsPanel.java";
    private static final String WORKFLOW =
            "src/main/java/com/johnnyblabs/openspec/toolwindow/WorkflowActionPanel.java";
    private static final String SERVICE =
            "src/main/java/com/johnnyblabs/openspec/ai/DirectApiService.java";
    private static final String FACTORY =
            "src/main/java/com/johnnyblabs/openspec/toolwindow/OpenSpecToolWindowFactory.java";
    private static final String TW_PANEL =
            "src/main/java/com/johnnyblabs/openspec/toolwindow/OpenSpecToolWindowPanel.java";
    private static final String CONFIGURABLE =
            "src/main/java/com/johnnyblabs/openspec/settings/OpenSpecConfigurable.java";
    private static final String WIZARD_MODEL =
            "src/main/java/com/johnnyblabs/openspec/dialogs/SetupWizardModel.java";
    private static final String WIZARD_DIALOG =
            "src/main/java/com/johnnyblabs/openspec/dialogs/SetupWizardDialog.java";

    @Test
    void onProviderChanged_readsHasApiKeyOffTheEdt() throws IOException {
        String body = methodBody(read(SETTINGS), "private void onProviderChanged()");
        int pooled = body.indexOf("executeOnPooledThread(");
        assertTrue(pooled >= 0, "onProviderChanged must hop to a pooled thread for the keystore read");
        // The blocking hasApiKey (PasswordSafe.get, @RequiresBackgroundThread) must live INSIDE the
        // pooled lambda — never before it on the EDT.
        assertFalse(body.substring(0, pooled).contains("AiCredentialStore.hasApiKey("),
                "hasApiKey must not be read on the EDT before the pooled hop");
        assertTrue(body.substring(pooled).contains("AiCredentialStore.hasApiKey("),
                "hasApiKey must be read inside the pooled lambda");
    }

    @Test
    void testApiConnection_doesCredentialIoInsideTheWorker_notOnTheEdt() throws IOException {
        String body = methodBody(read(SETTINGS), "private void testApiConnection()");
        int worker = body.indexOf("new SwingWorker");
        assertTrue(worker >= 0, "testApiConnection must run its blocking work in a SwingWorker");
        // Nothing touches AiCredentialStore on the EDT before the worker starts (the pre-worker
        // getApiKey() is the panel's own JPasswordField accessor, not the credential store).
        assertFalse(body.substring(0, worker).contains("AiCredentialStore."),
                "credential store I/O must not run on the EDT before the worker");
        String workerBody = body.substring(worker);
        assertTrue(workerBody.contains("AiCredentialStore.storeApiKey(")
                        && workerBody.contains("AiCredentialStore.getApiKey("),
                "store/get must happen inside doInBackground (off the EDT)");
    }

    @Test
    void perChipCancel_cancelsSingleGeneration_notGenerateAll() throws IOException {
        String content = read(WORKFLOW);
        assertTrue(content.contains("-> onCancelGeneration(artifact.id())"),
                "the per-chip context-menu Cancel must route to onCancelGeneration(<that chip's id>)");
        String body = methodBody(content, "private void onCancelGeneration(String artifactId)");
        assertTrue(body.contains(".cancel()"),
                "onCancelGeneration must cancel that artifact's own indicator");
        assertTrue(body.contains("activeGenerations.get(artifactId)"),
                "cancel must look up the indicator by artifact id — not a single shared field that mis-targets");
        assertFalse(body.contains("onCancelGenerateAll"),
                "per-chip cancel must NOT fall through to the Generate-All cancel");
    }

    @Test
    void generation_honorsCancellation() throws IOException {
        String content = read(WORKFLOW);
        assertTrue(content.contains("activeGenerations.put(artifactId, indicator)"),
                "the running task must register its indicator keyed by artifact id so its own cancel reaches it");
        assertTrue(content.contains("activeGenerations.remove(artifactId, indicator)"),
                "the run must deregister its indicator when it ends (conditional remove, so a newer run survives)");
        assertTrue(content.contains("if (indicator.isCanceled()) return;"),
                "generation must check isCanceled() after the blocking generate() call");
        assertTrue(content.contains("catch (com.intellij.openapi.progress.ProcessCanceledException"),
                "a cancellation must propagate as ProcessCanceledException, not be reported as an error");
    }

    @Test
    void configurableApply_persistsKeyOffTheEdt() throws IOException {
        String body = methodBody(read(CONFIGURABLE), "public void apply()");
        int pooled = body.indexOf("executeOnPooledThread(");
        assertTrue(pooled >= 0, "apply() must persist the key off the EDT");
        // The blocking storeApiKey must live inside the pooled hop, never before it on the EDT.
        assertFalse(body.substring(0, pooled).contains("AiCredentialStore.storeApiKey("),
                "storeApiKey must not run on the EDT before the pooled hop");
        assertTrue(body.substring(pooled).contains("AiCredentialStore.storeApiKey("),
                "storeApiKey must run inside the pooled lambda");
    }

    @Test
    void wizardPersist_writesKeyOffTheEdt() throws IOException {
        String body = methodBody(read(WIZARD_MODEL), "public void persist(");
        int pooled = body.indexOf("executeOnPooledThread(");
        assertTrue(pooled >= 0, "persist() (called on the EDT via wizard OK) must write the key off the EDT");
        assertFalse(body.substring(0, pooled).contains("AiCredentialStore.storeApiKey("),
                "the blocking storeApiKey must not run on the EDT before the pooled hop");
        assertTrue(body.substring(pooled).contains("AiCredentialStore.storeApiKey("),
                "storeApiKey must run inside the pooled lambda");
    }

    @Test
    void settingsTest_validatesByListingModelsOffTheEdt_notATokenCostingPrompt() throws IOException {
        String content = read(SETTINGS);
        String body = methodBody(content, "private void testApiConnection()");
        int worker = body.indexOf("new SwingWorker");
        assertTrue(worker >= 0, "testApiConnection must run its work in a SwingWorker");
        // Test now validates the key by listing models — and the fetch runs in the worker, not on the
        // EDT before it. The old token-costing test-prompt (testConnection) must be gone.
        assertTrue(body.contains("apiService.listModels("), "Test must validate by listing models");
        // The Workspace ID is read from the form (on the EDT, before the worker), validated so an invalid
        // value is never sent, and passed into the worker's listModels call.
        int workspaceRead = body.indexOf("getAiWorkspaceId()");
        int workspaceCheck = body.indexOf("validateWorkspaceId(");
        assertTrue(workspaceRead >= 0 && workspaceRead < worker, "Test must read the form's Workspace ID before the worker");
        assertTrue(workspaceCheck >= 0 && workspaceCheck < worker, "Test must validate the Workspace ID before sending it");
        // The OpenAI Organization and Project IDs are read from the form before the worker too, and the
        // provider's settings as typed are what the worker sends.
        int formRead = body.indexOf("formRequestSettings(provider)");
        assertTrue(formRead >= 0 && formRead < worker, "Test must read the form's provider settings before the worker");
        assertTrue(body.substring(worker).contains("listModels(provider, key, requestSettings)"),
                "the worker must pass the form's provider settings to listModels");
        assertFalse(body.substring(0, worker).contains("listModels("),
                "the models fetch must not run on the EDT before the worker");
        assertFalse(body.contains(".testConnection("),
                "the token-costing test-prompt must be replaced by the free list-models GET");
        // Combo population is factored into applyModelOptions (called from done(), on the EDT); the
        // combo is never mutated inline inside testApiConnection/doInBackground.
        assertTrue(body.contains("applyModelOptions("), "done() must populate via applyModelOptions");
        assertFalse(body.contains("aiModelCombo"),
                "testApiConnection must not mutate the combo inline — that lives in applyModelOptions");
        String apply = methodBody(content, "private void applyModelOptions(");
        assertTrue(apply.contains("aiModelCombo.removeAllItems()") && apply.contains("aiModelCombo.addItem("),
                "applyModelOptions owns the combo mutation");
    }

    @Test
    void wizardTestConnection_writesKeyInsideTheWorker() throws IOException {
        String body = methodBody(read(WIZARD_DIALOG), "private void testApiConnection()");
        int worker = body.indexOf("new SwingWorker");
        assertTrue(worker >= 0, "testApiConnection must run its blocking work in a SwingWorker");
        assertFalse(body.substring(0, worker).contains("AiCredentialStore.storeApiKey("),
                "the key must not be stored on the EDT before the worker");
        assertTrue(body.substring(worker).contains("AiCredentialStore.storeApiKey("),
                "the key must be stored inside doInBackground (off the EDT)");
    }

    @Test
    void toolWindowContents_ownTheirDisposablePanels() throws IOException {
        String body = methodBody(read(FACTORY), "static void createNormalContent(");
        assertTrue(body.contains("browseContent.setDisposer(browsePanel)"),
                "the browse content must dispose its panel (Alarms/subscriptions are parented to it)");
        assertTrue(body.contains("consoleContent.setDisposer(consolePanel)"),
                "the console content must dispose its panel (it owns a ConsoleView)");
    }

    @Test
    void toolWindowPanel_parentsAlarmsToItself_notTheProject() throws IOException {
        String content = read(TW_PANEL);
        assertTrue(content.contains("implements DataProvider, Disposable"),
                "the panel must be Disposable so its content can own it");
        // EVERY Alarm must be parented to the panel (this), not just one — a partial regression that
        // reverted a single alarm's parent to `project` would leak it for the project's lifetime.
        int alarms = countOccurrences(content, "new Alarm(");
        int parentedToThis = countOccurrences(content, "new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)");
        assertTrue(alarms >= 2, "the panel constructs its refresh and preview alarms");
        assertEquals(alarms, parentedToThis,
                "every Alarm must be parented to the panel (this), so they die with the tab — not leak on the project");
        assertTrue(content.contains("project.getMessageBus().connect(this)"),
                "the VFS subscription must be parented to the panel (this), not the bare project connection");
    }

    @Test
    void toolWindowFactory_detectsStateStatically_withoutConstructingAThrowawayPanel() throws IOException {
        // GettingStartedPanel is a Disposable that parents an Alarm to itself; constructing one only to
        // read detectState() and then discarding it self-registers in the Disposer and leaks. The factory
        // must use the STATIC detectState(project) on its detection paths and construct a panel only when
        // it actually shows one.
        String content = read(FACTORY);
        assertTrue(content.contains("GettingStartedPanel.detectState(project)"),
                "the factory must detect state via the static GettingStartedPanel.detectState(project)");
        assertFalse(content.contains(".detectState()"),
                "the factory must NOT call the no-arg instance detectState() — that means it built a throwaway panel");
    }

    // --- operation-time model selector guards ---

    @Test
    void modelSelectorFetch_runsOffTheEdt_warmFirst() throws IOException {
        String body = methodBody(read(WORKFLOW), "private void populateModelSelector()");
        int pooled = body.indexOf("executeOnPooledThread(");
        assertTrue(pooled >= 0, "populateModelSelector must hop to a pooled thread for the network fetch");
        // Warm-first: the per-session cache is consulted before any fetch (one fetch per session).
        int cacheRead = body.indexOf("getDiscoveredModels(");
        assertTrue(cacheRead >= 0 && cacheRead < pooled,
                "the session cache must be read before the pooled fetch (warm-first)");
        // The fetch is deduped per provider per session so a failed/empty fetch isn't retried every refresh.
        assertTrue(body.substring(0, pooled).contains("modelFetchTriggered.add("),
                "the fetch must be gated on a once-per-session attempt guard, not just a non-empty cache");
        // The blocking listModels runs INSIDE the pooled lambda, never before it on the EDT.
        assertFalse(body.substring(0, pooled).contains("listModels("),
                "listModels must not run on the EDT before the pooled hop");
        assertTrue(body.substring(pooled).contains("listModels("),
                "listModels must run inside the pooled lambda");
        assertTrue(body.substring(pooled).contains("ProviderRequestSettings.from(settings)")
                        && body.substring(pooled).contains("listModels(provider, key, requestSettings)"),
                "the tool-window fetch must send the saved provider settings (Claude workspace, OpenAI scope)");
        // The post-fetch repopulate is posted back to the EDT via invokeLater with an explicit ModalityState.
        String afterPooled = body.substring(pooled);
        int later = afterPooled.indexOf("invokeLater(");
        assertTrue(later >= 0, "the fetch result must repopulate via invokeLater");
        assertTrue(afterPooled.substring(later).contains("applyModelSelectorOptions("),
                "the repopulate must run inside invokeLater");
        // The repopulate must re-check the provider — a switch while the fetch was in flight must not
        // repopulate the selector with the stale provider's models.
        assertTrue(afterPooled.substring(later).contains("getAiProvider()"),
                "the invokeLater repopulate must re-check the current provider (in-flight switch guard)");
        // The explicit ModalityState is computed for this component and passed into the invokeLater
        // (so the repopulate is not deferred behind a modal dialog).
        assertTrue(body.contains("ModalityState.stateForComponent("),
                "an explicit ModalityState must be computed for the repopulate");
        assertTrue(afterPooled.substring(later).contains("modality"),
                "the invokeLater repopulate must be posted with the explicit ModalityState");
    }

    @Test
    void modelSelection_persistsToAiModel_andOperationsReadIt() throws IOException {
        String changed = methodBody(read(WORKFLOW), "private void onModelSelectionChanged()");
        assertTrue(changed.contains("setAiModel(") && changed.contains("getSelectedModel()"),
                "selecting a model must persist getSelectedModel() via settings.setAiModel(...)");
        // The funnel the persistence relies on: operations still resolve the model from the setting,
        // which is why no service signature needed to change.
        String getModel = methodBody(read(SERVICE), "private String getModel(");
        assertTrue(getModel.contains("getAiModel()"),
                "operations must resolve the model via settings.getAiModel() (the funnel the combo writes)");
    }

    @Test
    void modelSelector_gatedOnDirectApi() throws IOException {
        String body = methodBody(read(WORKFLOW), "private void updateModelSelectorVisibility()");
        assertTrue(body.contains("DeliveryMode.DIRECT_API"),
                "the model selector's visibility must be gated on Direct API");
        assertTrue(body.contains("isConfigured()"),
                "visibility must also require a configured provider, not Direct API alone");
        assertTrue(body.contains("modelSelector.setVisible(") || body.contains("modelSelector.setEnabled("),
                "the visibility method must toggle the model selector");
    }

    @Test
    void toolWindowGenerate_writesThroughTheSharedWriter() throws IOException {
        // The tool window's Direct API Generate wrote its own file and used the instruction's output path as a file
        // name, which fails for the specs glob. It must use the one writer every Direct API path shares, and a
        // regeneration must plan through the same parser before its review.
        String content = read(WORKFLOW);
        String call = "apiService.generateRaw(instruction.buildDirectApiPrompt(note))";
        // Anchor on the Generate branch (an earlier DIRECT_API case belongs to Apply).
        int branch = content.lastIndexOf("case DIRECT_API -> {", content.indexOf(call));
        assertTrue(branch >= 0, "expected the Generate DIRECT_API branch");
        String body = content.substring(branch, content.indexOf("} catch (AiApiException", branch));
        assertTrue(body.contains(call), "anti-vacuity: this is the generate branch, sending the noted Direct API prompt");
        assertTrue(body.contains("orchestration.writeArtifactResult(instruction, result)"),
                "Generate must write through ArtifactOrchestrationService.writeArtifactResult");
        assertTrue(body.contains("orchestration.planArtifactResult(instruction, result)"),
                "a regeneration must plan through the same parser, so refusals match");
        assertFalse(content.contains("createChildData(this, instruction.outputPath())"),
                "the output path (possibly a glob) must never be used as a file name");
        // The write runs on the background thread, before the EDT hop for the notification.
        int write = body.indexOf("writeArtifactResult(");
        int edt = body.indexOf("invokeLater(", write);
        assertTrue(write >= 0 && edt > write && !body.substring(0, write).contains("WriteAction"),
                "write off the EDT first, then post only the notification and refresh");
    }

    @Test
    void directApiGeneration_usesTheDirectApiPrompt() throws IOException {
        String body = methodBody(read(SERVICE), "public String generate(ArtifactInstruction instruction)");
        assertTrue(body.contains("instruction.buildDirectApiPrompt()"),
                "Direct API generation must add the multi-file format for glob outputs");
    }

    @Test
    void listModels_warmsSessionCacheOnSuccess() throws IOException {
        String body = methodBody(read(SERVICE), "public java.util.List<String> listModels(");
        int okCheck = body.indexOf("statusCode() != 200");
        int cacheWrite = body.indexOf("discoveredModels.put(");
        assertTrue(cacheWrite >= 0, "listModels must warm the session cache");
        assertTrue(okCheck >= 0 && cacheWrite > okCheck,
                "the cache write must be on the success path (after the non-200 throw)");
    }

    // --- mid-session AI-config refresh guards (the delivery selector re-offers Direct API without a restart) ---

    @Test
    void configurableApply_publishesAiConfigChanged() throws IOException {
        String body = methodBody(read(CONFIGURABLE), "public void apply()");
        assertTrue(body.contains("syncPublisher(AiConfigListener.TOPIC)") && body.contains("aiConfigChanged()"),
                "apply() must publish AiConfigListener so the tool window re-offers Direct API immediately after Apply");
    }

    @Test
    void wizardPersist_publishesAiConfigChanged() throws IOException {
        String body = methodBody(read(WIZARD_MODEL), "public void persist(");
        assertTrue(body.contains("syncPublisher(AiConfigListener.TOPIC)") && body.contains("aiConfigChanged()"),
                "the Setup Wizard's persist() must publish AiConfigListener so Direct API surfaces when the wizard finishes");
    }

    @Test
    void toolWindowPanel_subscribesToAiConfig_andRefreshesDeliveryOptions() throws IOException {
        String content = read(TW_PANEL);
        int sub = content.indexOf("subscribe(AiConfigListener.TOPIC");
        assertTrue(sub >= 0, "the panel must subscribe to AiConfigListener.TOPIC");
        // Parented to the panel's disposable connection so it dies with the tab, not the project.
        assertTrue(content.contains("connect(this).subscribe(AiConfigListener.TOPIC"),
                "the AiConfig subscription must be parented to the panel (connect(this))");
        assertTrue(content.substring(sub).contains("refreshDeliveryOptions"),
                "the AiConfig handler must call workflowPanel.refreshDeliveryOptions()");
    }

    @Test
    void deliverySelector_rebuildsOnMidSessionApiTransition() throws IOException {
        String content = read(WORKFLOW);
        // populateToolSelector runs only in the constructor; the refresh sink must re-run it when the
        // configured state transitions, so a key added mid-session adds the Direct API delivery item.
        String sink = methodBody(content, "private void updatePipelineAndButton(");
        int guard = sink.indexOf("hasApiNow != lastHasApi");
        assertTrue(guard >= 0,
                "updatePipelineAndButton must re-offer Direct API only on an isConfigured() transition (one-time, not per refresh)");
        assertTrue(sink.substring(guard).contains("populateToolSelector()"),
                "the transition guard must re-run populateToolSelector so a mid-session key surfaces Direct API");
        // The public entry point the AiConfigListener subscriber calls must rebuild the selector too.
        String refreshDelivery = methodBody(content, "public void refreshDeliveryOptions()");
        assertTrue(refreshDelivery.contains("populateToolSelector()") && refreshDelivery.contains("updateModelSelectorVisibility()"),
                "refreshDeliveryOptions must rebuild the delivery selector and re-evaluate the model selector");
    }

    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    @Test
    void everyErrorResponsePassesItsHeadersToTheErrorBuilder() throws IOException {
        // The request ID lives in the response headers; a request path that drops them silently loses it.
        String service = read(SERVICE);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("throw buildApiError\\((.*?)\\);",
                java.util.regex.Pattern.DOTALL).matcher(service);
        int sites = 0;
        while (m.find()) {
            sites++;
            assertTrue(m.group(1).trim().endsWith("response.headers()"),
                    "buildApiError call without the response headers: " + m.group(0));
        }
        // Claude, OpenAI and Gemini generation, and listModels. A new request path must be added here deliberately.
        assertEquals(4, sites, "expected exactly four request paths that throw buildApiError");
    }
}
