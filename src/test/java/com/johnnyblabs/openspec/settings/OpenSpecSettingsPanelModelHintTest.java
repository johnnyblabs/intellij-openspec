package com.johnnyblabs.openspec.settings;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.ai.AiCredentialStore;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.ai.DirectApiTestSupport;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Settings hint for a selected model that the provider's own model list doesn't hold. Test runs for real
 * through the panel: the service's HTTP client is stubbed to answer with a captured provider list
 * ({@code fixtures/ai/}), so the real request, parser and page check run.
 */
public class OpenSpecSettingsPanelModelHintTest extends BasePlatformTestCase {

    private static final String RETIRED = "gemini-2.5-pro";

    private OpenSpecSettingsPanel panel;
    private HttpClient client;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CliDetectionService detection = new CliDetectionService(getProject());
        CliDetectionTestSupport.fakeAvailable(detection, "1.14.3");
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
        client = mock(HttpClient.class);
        DirectApiService service = DirectApiTestSupport.serviceSendingTo(getProject(), client);
        ServiceContainerUtil.replaceService(getProject(), DirectApiService.class, service, getTestRootDisposable());
        panel = new OpenSpecSettingsPanel(getProject());
        panel.setAiProvider("GEMINI");
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        panel.setApiKey("test-key");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            for (AiProvider p : AiProvider.values()) {
                if (p != AiProvider.NONE) AiCredentialStore.removeApiKey(p);
            }
        } finally {
            super.tearDown();
        }
    }

    private static String fixture(String path) throws IOException {
        try (InputStream in = OpenSpecSettingsPanelModelHintTest.class.getResourceAsStream("/fixtures/ai/" + path)) {
            assertNotNull("fixture not found: " + path, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private void answer(String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        doReturn(response).when(client).send(any(), any());
    }

    private static void pumpUntil(BooleanSupplier done) throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            if (done.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("timed out waiting for Test to finish");
    }

    private void clickTestAndWait() throws Exception {
        panel.clickTestForTest();
        pumpUntil(() -> !panel.testResultTextForTest().equals("Testing..."));
    }

    public void testAModelTheListDoesNotHoldGetsTheHint() throws Exception {
        String list = fixture("gemini/models-list.json");
        assertFalse("anti-vacuity: the captured list lacks the model", list.contains("models/" + RETIRED + "\""));
        answer(list);
        panel.setAiModel(RETIRED);
        clickTestAndWait();
        assertEquals(OpenSpecSettingsPanel.modelNotListedHint(RETIRED, AiProvider.GEMINI), panel.modelHintTextForTest());
        assertEquals("gemini-2.5-pro isn't in Gemini's current model list for this key. It may have been retired, or "
                + "the provider may route its requests to another model. Choose a model from the list.",
                panel.modelHintTextForTest());
    }

    public void testAListedModelGetsNoHintAndChoosingOneClearsIt() throws Exception {
        answer(fixture("gemini/models-list.json"));
        panel.setAiModel(RETIRED);
        clickTestAndWait();
        assertNotNull("sanity: the hint shows first", panel.modelHintTextForTest());
        panel.setAiModel("gemini-3.8-flash");
        assertNull("choosing a listed model clears the hint", panel.modelHintTextForTest());
    }

    public void testTheLiveListHoldsTheRetiringModelSoNoHint() throws Exception {
        answer(fixture("gemini/live/models-list.json"));
        panel.setAiModel(RETIRED);
        clickTestAndWait();
        assertNull("Google still lists it a week before shutdown", panel.modelHintTextForTest());
    }

    public void testNoHintBeforeAnyFetch() {
        panel.setAiModel(RETIRED);
        assertNull(panel.modelHintTextForTest());
    }

    public void testNoHintAfterAFailedFetch() throws Exception {
        doThrow(new IOException("offline")).when(client).send(any(), any());
        panel.setAiModel(RETIRED);
        clickTestAndWait();
        assertTrue("sanity: Test failed", panel.testResultTextForTest().startsWith("Failed"));
        assertNull("the static fallback never judges a model", panel.modelHintTextForTest());
    }

    public void testSwitchingProviderAfterAFetchClearsIt() throws Exception {
        answer(fixture("gemini/models-list.json"));
        panel.setAiModel(RETIRED);
        clickTestAndWait();
        assertNotNull("sanity", panel.modelHintTextForTest());
        panel.setAiProvider("CLAUDE");
        panel.setAiModel(RETIRED);
        assertNull(panel.modelHintTextForTest());
        panel.setAiProvider("GEMINI");
        panel.setAiModel(RETIRED);
        assertNull("switching back doesn't revive the old list", panel.modelHintTextForTest());
    }

    public void testEditingTheKeyOrWorkspaceClearsIt() throws Exception {
        answer(fixture("gemini/models-list.json"));
        panel.setAiModel(RETIRED);
        clickTestAndWait();
        assertNotNull("sanity", panel.modelHintTextForTest());
        panel.setApiKey("another-key");
        assertNull("a key edit", panel.modelHintTextForTest());

        clickTestAndWait();
        assertNotNull("sanity: shown again after a new Test", panel.modelHintTextForTest());
        panel.setAiWorkspaceId("wrkspc_other");
        assertNull("a Workspace edit", panel.modelHintTextForTest());
    }

    @SuppressWarnings("unchecked")
    public void testAFetchFinishingAfterAProviderSwitchDoesNotApply() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch sent = new CountDownLatch(1);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(fixture("gemini/models-list.json"));
        doAnswer(inv -> {
            sent.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return response;
        }).when(client).send(any(), any());
        panel.setAiModel(RETIRED);
        panel.clickTestForTest();
        assertTrue("sanity: the fetch started", sent.await(10, TimeUnit.SECONDS));
        panel.setAiProvider("CLAUDE");
        panel.setAiModel(RETIRED);
        release.countDown();
        pumpUntil(() -> !panel.testResultTextForTest().equals("Testing..."));
        assertNull(panel.modelHintTextForTest());
        assertEquals("the selector keeps Claude's models, not Gemini's list", AiProvider.CLAUDE.getModels().get(0),
                firstModel());
    }

    @SuppressWarnings("unchecked")
    public void testAFetchFinishingAfterAKeyEditDoesNotApply() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch sent = new CountDownLatch(1);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(fixture("gemini/models-list.json"));
        doAnswer(inv -> {
            sent.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return response;
        }).when(client).send(any(), any());
        panel.setAiModel(RETIRED);
        panel.clickTestForTest();
        assertTrue("sanity: the fetch started", sent.await(10, TimeUnit.SECONDS));
        panel.setApiKey("another-key");
        release.countDown();
        pumpUntil(() -> !panel.testResultTextForTest().equals("Testing..."));
        assertNull("the list was fetched with the old key", panel.modelHintTextForTest());
    }

    private String firstModel() {
        return panel.firstModelForTest();
    }
}
