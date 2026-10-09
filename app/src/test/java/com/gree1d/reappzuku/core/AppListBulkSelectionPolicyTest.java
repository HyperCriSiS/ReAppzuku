package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.utils.AppModel;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class AppListBulkSelectionPolicyTest {
    private static AppModel app(String packageName, boolean protectedApp) {
        return new AppModel(packageName, packageName, "", 0L, null,
                false, false, protectedApp);
    }

    @Test
    public void bulkSelectDoesNotSelectFilteredOutRows() {
        AppModel visible = app("com.example.visible", false);
        AppModel hidden = app("com.example.hidden", false);
        AppListBulkSelectionPolicy.selectVisible(Collections.singletonList(visible));
        assertTrue(visible.isSelected());
        assertFalse(hidden.isSelected());
    }

    @Test
    public void protectedAndWhitelistedVisibleRowsStayUnselected() {
        AppModel protectedApp = app("com.example.protected", true);
        AppModel whitelisted = app("com.example.whitelisted", false);
        whitelisted.setWhitelisted(true);
        AppModel regular = app("com.example.regular", false);
        AppListBulkSelectionPolicy.selectVisible(
                Arrays.asList(protectedApp, whitelisted, regular));
        assertFalse(protectedApp.isSelected());
        assertFalse(whitelisted.isSelected());
        assertTrue(regular.isSelected());
    }

    @Test
    public void explicitEarlierSelectionIsNotSilentlyErasedByFilter() {
        AppModel previouslySelected = app("com.example.selected", false);
        previouslySelected.setSelected(true);
        AppModel currentlyVisible = app("com.example.visible", false);
        AppListBulkSelectionPolicy.selectVisible(Collections.singletonList(currentlyVisible));
        assertTrue(previouslySelected.isSelected());
        assertTrue(currentlyVisible.isSelected());
    }

    @Test
    public void emptyFilteredListCannotSelectHiddenPackages() {
        AppModel hidden = app("com.example.hidden", false);
        AppListBulkSelectionPolicy.selectVisible(Collections.emptyList());
        assertFalse(hidden.isSelected());
    }
}
