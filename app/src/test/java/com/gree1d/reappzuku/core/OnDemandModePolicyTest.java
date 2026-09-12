\
    package com.gree1d.reappzuku.core;

    import static org.junit.Assert.assertFalse;
    import static org.junit.Assert.assertTrue;

    import java.io.IOException;
    import java.nio.file.Files;
    import java.nio.file.Path;
    import java.nio.file.Paths;
    import java.util.Arrays;
    import java.util.List;

    import org.junit.Test;

    public class OnDemandModePolicyTest {
        @Test
        public void masterModeIsDerivedFromExistingAppBehaviorPreferences() throws Exception {
            String source = readRepositoryFile(
                    "app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivity.java");

            assertTrue(source.contains("boolean onDemandMode = preventAutoStart && exitOnBack;"));
            assertTrue(source.contains(".putBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, isChecked)"));
            assertTrue(source.contains(".putBoolean(KEY_EXIT_ON_BACK, isChecked)"));
            assertTrue(source.contains("updatingOnDemandModeSwitch"));
            assertFalse(source.contains("KEY_ON_DEMAND_MODE"));
        }

        @Test
        public void appBehaviorScreenExposesMasterAndExistingFineControls() throws Exception {
            String layout = readRepositoryFile("app/src/main/res/layout/activity_settings.xml");

            assertTrue(layout.contains("@+id/layout_on_demand_mode"));
            assertTrue(layout.contains("@+id/switch_on_demand_mode"));
            assertTrue(layout.contains("@+id/layout_prevent_shizuku_autostart"));
            assertTrue(layout.contains("@+id/layout_exit_on_back"));
        }

        private static String readRepositoryFile(String relative) throws IOException {
            List<Path> candidates = Arrays.asList(
                    Paths.get(relative),
                    Paths.get("..", relative),
                    Paths.get("..", "..", relative));
            for (Path candidate : candidates) {
                if (Files.isRegularFile(candidate)) {
                    return Files.readString(candidate);
                }
            }
            throw new IOException("Could not locate repository file: " + relative
                    + " from " + Paths.get("").toAbsolutePath());
        }
    }
