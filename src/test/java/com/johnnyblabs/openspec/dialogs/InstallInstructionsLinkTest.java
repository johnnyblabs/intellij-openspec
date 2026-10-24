package com.johnnyblabs.openspec.dialogs;

import com.intellij.ide.BrowserUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.HyperlinkLabel;
import com.johnnyblabs.openspec.util.CliInstallHints;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

/** The link under the wizard's and Manage AI Tools' install hint opens upstream's installation page. */
public class InstallInstructionsLinkTest extends BasePlatformTestCase {

    public void testClickingTheLinkOpensTheInstallationPage() {
        HyperlinkLabel link = SetupWizardDialog.installInstructionsLink();
        assertEquals(CliInstallHints.INSTALL_DOCS_LABEL, link.getText());

        try (MockedStatic<BrowserUtil> browser = mockStatic(BrowserUtil.class)) {
            link.doClick();
            browser.verify(() -> BrowserUtil.browse(CliInstallHints.INSTALL_DOCS_URL));
        }
    }
}
