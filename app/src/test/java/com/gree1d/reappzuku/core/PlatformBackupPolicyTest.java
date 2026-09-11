package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

public class PlatformBackupPolicyTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Test
    public void manifestDisablesPlatformBackupAndPinsBothRuleFiles() throws Exception {
        Document manifest = parseXml(findRepoFile("app/src/main/AndroidManifest.xml"));
        Element application = (Element) manifest.getElementsByTagName("application").item(0);

        assertEquals("false", application.getAttributeNS(ANDROID_NS, "allowBackup"));
        assertEquals("@xml/backup_rules",
                application.getAttributeNS(ANDROID_NS, "fullBackupContent"));
        assertEquals("@xml/data_extraction_rules",
                application.getAttributeNS(ANDROID_NS, "dataExtractionRules"));
    }

    @Test
    public void legacyFullBackupExcludesTheEntireApplicationRoot() throws Exception {
        Document rules = parseXml(findRepoFile("app/src/main/res/xml/backup_rules.xml"));
        Element root = rules.getDocumentElement();
        assertEquals("full-backup-content", root.getTagName());
        assertTrue("Legacy full backup must exclude the entire app root",
                containsRootExclude(root));
        assertFalse("Legacy full backup must not contain include rules",
                root.getElementsByTagName("include").getLength() > 0);
    }

    @Test
    public void cloudBackupAndDeviceTransferBothExcludeTheEntireApplicationRoot() throws Exception {
        Document rules = parseXml(findRepoFile("app/src/main/res/xml/data_extraction_rules.xml"));
        Element root = rules.getDocumentElement();
        assertEquals("data-extraction-rules", root.getTagName());

        Element cloudBackup = singleChild(root, "cloud-backup");
        Element deviceTransfer = singleChild(root, "device-transfer");
        assertTrue("Cloud backup must exclude the entire app root",
                containsRootExclude(cloudBackup));
        assertTrue("Device transfer must exclude the entire app root",
                containsRootExclude(deviceTransfer));
        assertEquals("Cloud backup must not contain include rules", 0,
                cloudBackup.getElementsByTagName("include").getLength());
        assertEquals("Device transfer must not contain include rules", 0,
                deviceTransfer.getElementsByTagName("include").getLength());
    }

    private static boolean containsRootExclude(Element parent) {
        NodeList excludes = parent.getElementsByTagName("exclude");
        for (int i = 0; i < excludes.getLength(); i++) {
            Element exclude = (Element) excludes.item(i);
            if ("root".equals(exclude.getAttribute("domain"))
                    && ".".equals(exclude.getAttribute("path"))) {
                return true;
            }
        }
        return false;
    }

    private static Element singleChild(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        assertEquals("Expected exactly one <" + tagName + ">", 1, nodes.getLength());
        return (Element) nodes.item(0);
    }

    private static Document parseXml(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(file);
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
