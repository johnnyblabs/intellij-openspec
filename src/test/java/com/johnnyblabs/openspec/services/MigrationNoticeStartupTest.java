package com.johnnyblabs.openspec.services;

import com.google.gson.JsonParser;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.util.FixtureLayout;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The migration notice in a real project: shown once, with an Update… button that runs the registered Update action. */
public class MigrationNoticeStartupTest extends BasePlatformTestCase {

    private final List<Notification> raised = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        raised.clear();
        PropertiesComponent.getInstance(getProject()).unsetValue(MigrationNotice.SHOWN_KEY);
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                raised.add(notification);
            }
        });
    }

    private Path projectFrom(String version, String name, String layout) throws IOException {
        Path dir = FixtureVersions.CLI.resolve(version + "/update-migration");
        Path root = Files.createTempDirectory("migration-notice");
        Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(root.toFile()));
        FixtureLayout.materialize(JsonParser.parseString(Files.readString(dir.resolve(name + "/" + layout)))
                        .getAsJsonObject(), root.resolve("project"), root.resolve("home"),
                FixtureLayout.LinkMode.SYMLINK_OR_COPY,
                JsonParser.parseString(Files.readString(dir.resolve("texts.json"))).getAsJsonObject());
        return root.resolve("project");
    }

    private List<Notification> notices() {
        return raised.stream().filter(n -> MigrationNotice.TITLE.equals(n.getTitle())).toList();
    }

    public void testAOneXProjectIsToldOnceWithAnUpdateButton() throws IOException {
        Path project = projectFrom("2.1.1", "unedited", "before.json");

        assertTrue(MigrationNoticeStartup.notifyOnce(getProject(), "2.1.1", project));
        assertFalse("never twice", MigrationNoticeStartup.notifyOnce(getProject(), "2.1.1", project));

        assertEquals(1, notices().size());
        Notification n = notices().get(0);
        assertEquals(MigrationNotice.MESSAGE, n.getContent());
        assertEquals(List.of(MigrationNoticeStartup.UPDATE_ACTION),
                n.getActions().stream().map(a -> a.getTemplatePresentation().getText()).toList());
        assertNotNull("the button runs a registered action",
                ActionManager.getInstance().getAction(MigrationNoticeStartup.UPDATE_ACTION_ID));
        assertTrue(PropertiesComponent.getInstance(getProject()).getBoolean(MigrationNotice.SHOWN_KEY, false));
    }

    public void testAMigratedProjectOrAOneXCliIsNotTold() throws IOException {
        assertFalse(MigrationNoticeStartup.notifyOnce(getProject(), "2.1.1", projectFrom("2.1.1", "unedited", "after.json")));
        Path before = projectFrom("2.1.1", "unedited", "before.json");
        assertFalse(MigrationNoticeStartup.notifyOnce(getProject(), "1.17.0", before));
        assertFalse(MigrationNoticeStartup.notifyOnce(getProject(), null, before));
        assertEquals(List.of(), notices());
        assertFalse("not shown, so not recorded",
                PropertiesComponent.getInstance(getProject()).getBoolean(MigrationNotice.SHOWN_KEY, false));
    }
}
