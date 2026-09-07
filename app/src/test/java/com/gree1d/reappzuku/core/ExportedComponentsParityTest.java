package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

public class ExportedComponentsParityTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String APP_PACKAGE = "com.gree1d.reappzuku.";
    private static final Pattern DOCUMENTED_COMPONENT = Pattern.compile("^\\| `([^`]+)` \\|");

    @Test
    public void productionExportedComponentsExactlyMatchSecurityReview() throws Exception {
        File manifest = findRepoFile("app/src/main/AndroidManifest.xml");
        File review = findRepoFile("docs/EXPORTED_COMPONENTS.md");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(manifest);

        Set<String> manifestComponents = new LinkedHashSet<>();
        Map<String, String> permissions = new LinkedHashMap<>();
        for (String tag : new String[]{"activity", "service", "receiver", "provider"}) {
            NodeList nodes = document.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                if (!"true".equals(element.getAttributeNS(ANDROID_NS, "exported"))) continue;
                String rawName = element.getAttributeNS(ANDROID_NS, "name");
                String name = reviewName(rawName);
                manifestComponents.add(name);
                permissions.put(name, element.getAttributeNS(ANDROID_NS, "permission"));
            }
        }

        Set<String> documentedComponents = new LinkedHashSet<>();
        for (String line : Files.readAllLines(review.toPath(), StandardCharsets.UTF_8)) {
            Matcher matcher = DOCUMENTED_COMPONENT.matcher(line);
            if (matcher.find()) {
                documentedComponents.add(matcher.group(1));
            }
        }

        assertEquals("EXPORTED_COMPONENTS.md must exactly track production exported=true entries",
                manifestComponents, documentedComponents);
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",
                permissions.get("AppLaunchAccessibilityService"));
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE",
                permissions.get("ShappkyQuickTile"));
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE",
                permissions.get("ShappkyBackgroundKillTile"));
        assertEquals("android.permission.INTERACT_ACROSS_USERS_FULL",
                permissions.get("rikka.shizuku.ShizukuProvider"));
    }

    private static String reviewName(String manifestName) {
        if (manifestName.startsWith(APP_PACKAGE)) {
            return manifestName.substring(manifestName.lastIndexOf('.') + 1);
        }
        return manifestName;
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
