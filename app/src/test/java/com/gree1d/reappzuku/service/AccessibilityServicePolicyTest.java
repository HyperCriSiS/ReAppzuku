package com.gree1d.reappzuku.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.nio.file.Files;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class AccessibilityServicePolicyTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Test
    public void accessibilityConfigRemainsMinimalForForegroundPackageTracking() throws Exception {
        File config = findRepoFile("app/src/main/res/xml/accessibility_service_config.xml");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(config);
        Element service = document.getDocumentElement();

        assertEquals("typeWindowStateChanged",
                service.getAttributeNS(ANDROID_NS, "accessibilityEventTypes"));
        assertEquals("feedbackGeneric",
                service.getAttributeNS(ANDROID_NS, "accessibilityFeedbackType"));
        assertEquals("com.gree1d.reappzuku.ui.SettingsActivity",
                service.getAttributeNS(ANDROID_NS, "settingsActivity"));

        assertFalse("Window-content retrieval is unnecessary for package/window-state tracking",
                service.hasAttributeNS(ANDROID_NS, "canRetrieveWindowContent"));
        assertFalse("Broad accessibility flags must not be reintroduced without a reviewed need",
                service.hasAttributeNS(ANDROID_NS, "accessibilityFlags"));
        assertFalse("Package filtering must remain absent because the feature tracks foreground apps globally",
                service.hasAttributeNS(ANDROID_NS, "packageNames"));
    }

    private static File findRepoFile(String relativePath) throws Exception {
        File cursor = new File(System.getProperty("user.dir")).getAbsoluteFile();
        for (int i = 0; i < 6 && cursor != null; i++, cursor = cursor.getParentFile()) {
            File candidate = new File(cursor, relativePath);
            if (Files.isRegularFile(candidate.toPath())) return candidate;
        }
        throw new AssertionError("Repository file not found: " + relativePath);
    }
}
