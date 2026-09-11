package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class NavigationManifestPolicyTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Test
    public void detailAndTopLevelActivitiesHaveNonRecursiveParents() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder()
                .parse(findRepoFile("app/src/main/AndroidManifest.xml"));

        assertParent(document,
                ".ui.LogDetailActivity",
                "com.gree1d.reappzuku.ui.StatisticsActivity");
        assertParent(document,
                "com.gree1d.reappzuku.ui.AppResourceDetailActivity",
                "com.gree1d.reappzuku.ui.StatisticsActivity");
        assertParent(document,
                "com.gree1d.reappzuku.ui.SettingsActivity",
                "com.gree1d.reappzuku.ui.MainActivity");
        assertParent(document,
                "com.gree1d.reappzuku.ui.StatisticsActivity",
                "com.gree1d.reappzuku.ui.MainActivity");
    }

    private static void assertParent(Document document, String activityName, String expectedParent) {
        NodeList activities = document.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (!activityName.equals(activity.getAttributeNS(ANDROID_NS, "name"))) continue;
            assertEquals("Unexpected parentActivityName for " + activityName,
                    expectedParent,
                    activity.getAttributeNS(ANDROID_NS, "parentActivityName"));
            return;
        }
        throw new AssertionError("Activity not found in manifest: " + activityName);
    }

    private static File findRepoFile(String relativePath) {
        File cursor = new File(System.getProperty("user.dir")).getAbsoluteFile();
        for (int i = 0; i < 6 && cursor != null; i++, cursor = cursor.getParentFile()) {
            File candidate = new File(cursor, relativePath);
            if (candidate.isFile()) return candidate;
        }
        throw new AssertionError("Repository file not found: " + relativePath);
    }
}
