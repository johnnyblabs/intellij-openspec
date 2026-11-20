package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.function.Consumer;

/** Asks which of several active changes an action should use. A single change is used without asking. */
final class ChangePicker {

    /** Shows the choices; replaced in tests, which can't show a popup. */
    interface Chooser {
        void choose(List<String> changes, @Nullable DataContext where, String title, Consumer<String> onChosen);
    }

    private static final Chooser POPUP_CHOOSER = (changes, where, title, onChosen) ->
            JBPopupFactory.getInstance().createPopupChooserBuilder(changes)
                    .setTitle(title)
                    .setItemChosenCallback(onChosen::accept)
                    .createPopup()
                    .showInBestPositionFor(where);

    private static volatile Chooser chooser = POPUP_CHOOSER;

    private ChangePicker() {
    }

    /** Calls {@code onChosen} with the change to use: the only one, or the one the user picks. Call on the EDT. */
    static void pick(List<String> changes, @Nullable DataContext where, String title, Consumer<String> onChosen) {
        if (changes.size() == 1) {
            onChosen.accept(changes.getFirst());
            return;
        }
        chooser.choose(changes, where, title, onChosen);
    }

    @TestOnly
    static void setChooserForTest(@Nullable Chooser testChooser) {
        chooser = testChooser == null ? POPUP_CHOOSER : testChooser;
    }
}
