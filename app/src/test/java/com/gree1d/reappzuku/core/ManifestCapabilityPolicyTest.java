package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ManifestCapabilityPolicyTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS_NS = "http://schemas.android.com/tools";

    @Test
    public void packageVisibilityIsExplicitAndUsageStatsIsNotRequested() throws Exception {
        Document document = parseManifest();

        Element queryAll = findUsesPermission(document, "android.permission.QUERY_ALL_PACKAGES");
        assertNotNull("QUERY_ALL_PACKAGES is required for the installed-app manager surface", queryAll);
        assertTrue("QUERY_ALL_PACKAGES must carry its reviewed lint justification",
                queryAll.getAttributeNS(TOOLS_NS, "ignore").contains("QueryAllPackagesPermission"));

        assertFalse("PACKAGE_USAGE_STATS must not silently enter the normal-app permission surface",
                hasUsesPermission(document, "android.permission.PACKAGE_USAGE_STATS"));
    }

    @Test
    public void leanbackLauncherKeepsCompleteOptionalTvContract() throws Exception {
        Document document = parseManifest();
        Element mainActivity = findActivity(document, "com.gree1d.reappzuku.ui.MainActivity");
        assertNotNull(mainActivity);
        assertTrue("MainActivity is expected to expose the Leanback launcher",
                hasCategory(mainActivity, "android.intent.category.LEANBACK_LAUNCHER"));

        Element application = (Element) document.getElementsByTagName("application").item(0);
        assertEquals("@drawable/tv_banner", application.getAttributeNS(ANDROID_NS, "banner"));

        assertFeatureOptional(document, "android.software.leanback");
        assertFeatureOptional(document, "android.hardware.touchscreen");
    }

    private static Document parseManifest() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(findRepoFile("app/src/main/AndroidManifest.xml"));
    }

    private static Element findUsesPermission(Document document, String permission) {
        NodeList nodes = document.getElementsByTagName("uses-permission");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (permission.equals(element.getAttributeNS(ANDROID_NS, "name"))) return element;
        }
        return null;
    }

    private static boolean hasUsesPermission(Document document, String permission) {
        return findUsesPermission(document, permission) != null;
    }

    private static Element findActivity(Document document, String activityName) {
        NodeList nodes = document.getElementsByTagName("activity");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (activityName.equals(element.getAttributeNS(ANDROID_NS, "name"))) return element;
        }
        return null;
    }

    private static boolean hasCategory(Element activity, String categoryName) {
        NodeList filters = activity.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Node filter = filters.item(i);
            NodeList children = filter.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (!(child instanceof Element) || !"category".equals(child.getNodeName())) continue;
                Element category = (Element) child;
                if (categoryName.equals(category.getAttributeNS(ANDROID_NS, "name"))) return true;
            }
        }
        return false;
    }

    private static void assertFeatureOptional(Document document, String featureName) {
        NodeList nodes = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (!featureName.equals(element.getAttributeNS(ANDROID_NS, "name"))) continue;
            assertEquals("false", element.getAttributeNS(ANDROID_NS, "required"));
            return;
        }
        throw new AssertionError("Missing uses-feature: " + featureName);
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
