package com.johnnyblabs.openspec.validation;

import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.util.PluginVersions;

/** Which release the built-in validator follows: the detected CLI's, the target with none, and nothing gated when unreadable. */
public class ValidationReleaseTest extends BasePlatformTestCase {

    private CliDetectionService detection;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        detection = new CliDetectionService(getProject());
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
    }

    public void testADetectedCliDecides() {
        CliDetectionTestSupport.fakeAvailable(detection, "1.15.0");
        assertEquals("1.15.0", ValidationRelease.followed(getProject()));
        assertFalse(ValidationRelease.has("1.15.0", ValidationRelease.SCENARIO_STEPS));
        assertTrue(ValidationRelease.has("1.15.0", ValidationRelease.TOO_LONG));
    }

    public void testNoCliFollowsTheTarget() {
        assertEquals(PluginVersions.targetCliVersion(), ValidationRelease.followed(getProject()));
    }

    public void testAnUnreadableVersionAppliesNoGatedRule() {
        for (String version : new String[]{"garbage", "v2.1.1", "", null}) {
            assertFalse(String.valueOf(version), ValidationRelease.has(version, ValidationRelease.TOO_LONG));
            assertFalse(String.valueOf(version), ValidationRelease.has(version, ValidationRelease.SCENARIO_STEPS));
            assertFalse(String.valueOf(version), ValidationRelease.has(version, ValidationRelease.MODIFIED_GROWTH));
        }
    }

    public void testTheBuiltInValidatorFollowsTheDetectedRelease() {
        CliDetectionTestSupport.fakeAvailable(detection, "2.1.1");
        BuiltInValidator validator = getProject().getService(BuiltInValidator.class);
        validator.clearReleaseForTest();
        assertEquals("2.1.1", validator.release());
    }

    /** One run follows one release: inside {@code following}, every read sees it, whatever detection says meanwhile. */
    public void testARunFollowsTheReleaseItStartedWith() {
        CliDetectionTestSupport.fakeAvailable(detection, "2.1.1");
        BuiltInValidator validator = getProject().getService(BuiltInValidator.class);
        validator.clearReleaseForTest();
        String seen = validator.following("1.15.0", () -> {
            CliDetectionTestSupport.fakeAvailable(detection, "2.0.0");
            return validator.release();
        });
        assertEquals("1.15.0", seen);
        assertEquals("after the run, the detected release again", "2.0.0", validator.release());
        assertEquals("a nested run restores the outer one", "1.16.0",
                validator.following("1.16.0", () -> {
                    validator.following("2.1.1", validator::release);
                    return validator.release();
                }));
    }
}
