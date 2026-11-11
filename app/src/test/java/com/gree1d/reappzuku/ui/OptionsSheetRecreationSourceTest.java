package com.gree1d.reappzuku.ui;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class OptionsSheetRecreationSourceTest {

    @Test
    public void recreatedSheetsCloseBecauseTheirListenerIsGone() throws Exception {
        for (String name : new String[] {"AppOptionsBottomSheet", "LogAppOptionsBottomSheet",
                "StatsAppOptionsBottomSheet"}) {
            String source = read("app/src/main/java/com/gree1d/reappzuku/ui/" + name + ".java");
            assertTrue(name, source.contains(
                    "if (savedInstanceState != null) {\n            dismissAllowingStateLoss();"));
        }
    }

    private static String read(String relative) throws Exception {
        for (Path candidate : Arrays.asList(Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative)).replace("\r\n", "\n");
            }
        }
        throw new java.io.IOException("Could not locate repository root");
    }
}
