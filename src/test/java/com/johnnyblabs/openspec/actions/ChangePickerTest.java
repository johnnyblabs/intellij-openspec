package com.johnnyblabs.openspec.actions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ChangePickerTest {

    @AfterEach
    void restoreChooser() {
        ChangePicker.setChooserForTest(null);
    }

    @Test
    void aSingleChangeIsUsedWithoutAsking() {
        List<String> asked = new ArrayList<>();
        ChangePicker.setChooserForTest((changes, where, title, onChosen) -> asked.add(title));
        AtomicReference<String> chosen = new AtomicReference<>();

        ChangePicker.pick(List.of("alpha"), null, "Continue", chosen::set);

        assertTrue(asked.isEmpty(), "one change must not show the chooser");
        assertEquals("alpha", chosen.get());
    }

    @Test
    void severalChangesShowTheChooserWithTheActionsTitle() {
        List<String> offered = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        ChangePicker.setChooserForTest((changes, where, title, onChosen) -> {
            offered.addAll(changes);
            titles.add(title);
            onChosen.accept("beta");
        });
        AtomicReference<String> chosen = new AtomicReference<>();

        ChangePicker.pick(List.of("alpha", "beta"), null, "Verify", chosen::set);

        assertEquals(List.of("alpha", "beta"), offered);
        assertEquals(List.of("Verify"), titles);
        assertEquals("beta", chosen.get(), "the chosen change is the one used");
    }
}
