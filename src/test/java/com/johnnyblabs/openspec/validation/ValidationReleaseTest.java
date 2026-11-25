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
}
