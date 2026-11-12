package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.core.SuggestionEngine.AppFacts;
import com.gree1d.reappzuku.core.SuggestionEngine.Suggestion;
import com.gree1d.reappzuku.db.AppPolicy;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SavingLevelsTest {
    private static final int UNMANAGED = AppPolicyListState.STATUS_UNMANAGED;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private static AppFacts app(String pkg, int status, int daysSinceUsed, double minutes, boolean sensor) {
        return new AppFacts(pkg, status, 0, 0, 0, daysSinceUsed, minutes, sensor);
    }

    // ---- what the system counted ----

    // Lines from an Android 16 device; the job name contains a comma in the last one.
    private static String checkin(long windowMs) {
        return String.join("\n",
                "9,0,l,bt,1," + windowMs + ",4527425,429289047,14222724,1794443706427,419065145",
                "9,10453,l,awl,1282637,1282637",
                "9,10453,l,jb,\"#ScribesWorker#@androidx.work.systemjobscheduler@com.twitter.android/"
                        + "androidx.work.impl.background.systemjob.SystemJobService\",1298125,4,1298125,4",
                "9,10453,l,jb,\"#DatabaseCleanupWorker#@x/y\",48,1,48,1",
                "9,10428,l,awl,299999,299999",
                "9,10170,l,jb,\"a,b\",600000,2,600000,2",
                "9,10170,l,cpu,26699,33456,0",
                "garbage");
    }

    @Test
    public void backgroundTimeIsTheLongerOfWakelocksAndJobsPerDay() {
        Map<Integer, Double> minutes = BackgroundActivityParser.minutesPerDay(checkin(2 * DAY_MS));
        // Jobs 1298125 + 48 ms outlast the 1282637 ms wakelock; a running job holds one, so they are not added.
        assertEquals((1298125 + 48) / 60_000.0 / 2, minutes.get(10453), 1e-9);
        assertEquals(5.0, minutes.get(10170), 1e-9);
        // Just under five minutes in the whole window is not named, whatever it is per day.
        assertFalse(minutes.containsKey(10428));
        assertEquals(2, minutes.size());
        // Windows line endings do not break the numbers at the end of a line.
        assertEquals(minutes, BackgroundActivityParser.minutesPerDay(
                checkin(2 * DAY_MS).replace("\n", "\r\n")));
    }

    @Test
    public void aShortWindowSaysNothing() {
        // Ten minutes of jobs one hour after unplugging are not four hours per day.
        assertTrue(BackgroundActivityParser.minutesPerDay(checkin(60 * 60_000L)).isEmpty());
        assertTrue(BackgroundActivityParser.minutesPerDay(
                checkin(BackgroundActivityParser.MIN_WINDOW_MS - 1)).isEmpty());
        assertFalse(BackgroundActivityParser.minutesPerDay(
                checkin(BackgroundActivityParser.MIN_WINDOW_MS)).isEmpty());
        assertTrue(BackgroundActivityParser.minutesPerDay(null).isEmpty());
        assertTrue(BackgroundActivityParser.minutesPerDay("").isEmpty());
    }

    // ---- rules ----

    @Test
    public void anUnopenedAppThatWorksInTheBackgroundIsNamedOnTheFirstDay() {
        // No battery share, no kills: nothing of the app's own measurements is needed.
        assertEquals(Collections.singletonList(SuggestionEngine.REASON_BACKGROUND),
                SuggestionEngine.reasonsFor(app("a", UNMANAGED, 3, 2, false)));
        assertEquals(Collections.singletonList(SuggestionEngine.REASON_SENSOR),
                SuggestionEngine.reasonsFor(app("a", UNMANAGED, 30, 0, true)));
        assertEquals(Arrays.asList(SuggestionEngine.REASON_BACKGROUND, SuggestionEngine.REASON_SENSOR),
                SuggestionEngine.reasonsFor(app("a", UNMANAGED, 3, 10, true)));
    }

    @Test
    public void anAppInUseOrAlreadyManagedIsLeftAlone() {
        // Opened within three days, or the last use is unknown.
        assertTrue(SuggestionEngine.reasonsFor(app("a", UNMANAGED, 2, 60, true)).isEmpty());
        assertTrue(SuggestionEngine.reasonsFor(app("a", UNMANAGED, -1, 60, true)).isEmpty());
        // Too little background time and no sensor.
        assertTrue(SuggestionEngine.reasonsFor(app("a", UNMANAGED, 30, 1.9, false)).isEmpty());
        for (int status : new int[] {AppPolicyListState.STATUS_PROTECTED,
                AppPolicyListState.STATUS_SMART, AppPolicyListState.STATUS_IMMEDIATE}) {
            assertTrue(SuggestionEngine.reasonsFor(app("a", status, 30, 60, true)).isEmpty());
        }
    }

    @Test
    public void moreBackgroundTimeRanksHigher() {
        List<Suggestion> result = SuggestionEngine.suggest(Arrays.asList(
                app("little", UNMANAGED, 5, 3, false),
                app("sensor", UNMANAGED, 5, 0, true),
                app("much", UNMANAGED, 5, 40, false)), Collections.emptySet());
        assertEquals("much", result.get(0).packageName);
        assertEquals("little", result.get(1).packageName);
        assertEquals("sensor", result.get(2).packageName);
    }

    // ---- levels ----

    @Test
    public void onlyTheStrictLevelEverStopsTheApp() {
        assertEquals(PolicyPresetSeeder.PRESET_MESSENGER, SuggestionLoader.presetFor(SuggestionLoader.LEVEL_GENTLE));
        assertEquals(PolicyPresetSeeder.PRESET_MESSENGER, SuggestionLoader.presetFor(SuggestionLoader.LEVEL_BALANCED));
        assertEquals(PolicyPresetSeeder.PRESET_RARELY_USED, SuggestionLoader.presetFor(SuggestionLoader.LEVEL_STRICT));
        for (com.gree1d.reappzuku.db.PolicyPreset preset : PolicyPresetSeeder.builtIns(0)) {
            if (preset.id == PolicyPresetSeeder.PRESET_MESSENGER) {
                assertEquals(Long.MAX_VALUE, preset.forceStopDelayMs);
            }
        }
        assertEquals(AppPolicy.RESTRICTION_NONE, SuggestionLoader.restrictionFor(SuggestionLoader.LEVEL_GENTLE));
        assertEquals(AppPolicy.RESTRICTION_SOFT, SuggestionLoader.restrictionFor(SuggestionLoader.LEVEL_BALANCED));
        assertEquals(AppPolicy.RESTRICTION_MEDIUM, SuggestionLoader.restrictionFor(SuggestionLoader.LEVEL_STRICT));
    }

    @Test
    public void applyingKeepsAnExistingRestrictionAndDoesNotStopTheApp() throws Exception {
        String loader = read("app/src/main/java/com/gree1d/reappzuku/core/SuggestionLoader.java");
        assertTrue(loader.contains("&& BackgroundAppManager.storedRestrictionLevel(context, packageName)\n"
                + "                        == AppPolicy.RESTRICTION_NONE;"));
        // Undo is told what the recommendation set, and nothing when it set nothing.
        assertTrue(loader.contains("restrict ? restrictionFor(level) : -1);"));
        // The quiet variant: the visible one force-stops the app, which would cut its notifications.
        assertTrue(loader.contains("context, packageName, restrictionFor(level), true);"));
        // The level dialog is skipped for a restart loop, which is only ever eased.
        String screen = read("app/src/main/java/com/gree1d/reappzuku/ui/RecommendationsActivity.java");
        assertTrue(screen.contains("if (suggestion.reason == SuggestionEngine.REASON_RESTARTS\n"
                + "                        || !SuggestionLoader.canRestrict(this)) {"));
        // A list rebuilt under the open dialog would leave a stale row that could be applied again.
        assertTrue(screen.contains("if (row.getParent() == null) {"));
    }

    @Test
    public void theStrictLevelIsTheRarelyUsedPreset() {
        for (com.gree1d.reappzuku.db.PolicyPreset preset : PolicyPresetSeeder.builtIns(0)) {
            if (preset.id != PolicyPresetSeeder.PRESET_RARELY_USED) continue;
            assertEquals(AppPolicy.STRATEGY_SMART, preset.strategy);
            assertEquals(30L * 60_000L, preset.standbyDelayMs);
            assertEquals(2L * 60 * 60_000L, preset.forceStopDelayMs);
            // The level text and the help promise exactly this restriction.
            assertEquals(SuggestionLoader.restrictionFor(SuggestionLoader.LEVEL_STRICT),
                    preset.backgroundRestriction);
        }
    }

    @Test
    public void onlyUserAppsThatCanBeOpenedCountAsIdleSinceInstall() throws Exception {
        String loader = read("app/src/main/java/com/gree1d/reappzuku/core/SuggestionLoader.java");
        // A system component the user can never open must not turn up as unused for years.
        assertTrue(loader.contains("&& background.contains(packageName)\n"
                + "                    && packageManager.getLaunchIntentForPackage(packageName) != null) {"));
        assertTrue(loader.contains("(info.flags & ApplicationInfo.FLAG_SYSTEM) == 0 ? packages[0] : null;"));
        assertTrue(loader.contains("if (packages == null || packages.length != 1) return null;"));
        // The copy of an app in a work profile is not this user's app.
        assertTrue(loader.contains(
                "if (uid / PER_USER_RANGE != android.os.Process.myUid() / PER_USER_RANGE) return null;"));
    }

    @Test
    public void theSystemDumpsDoNotHoldUpDismissAndAFailedDumpIsNotKept() throws Exception {
        String loader = read("app/src/main/java/com/gree1d/reappzuku/core/SuggestionLoader.java");
        assertTrue(loader.contains("private static Background background(Context context, long now) {\n"
                + "        synchronized (BACKGROUND_LOCK) {"));
        int failed = loader.indexOf("if (checkin == null || checkin.isEmpty()) return result;");
        assertTrue(failed >= 0 && failed < loader.indexOf("cachedBackground = result;"));
    }

    @Test
    public void undoOnlyLiftsARestrictionTheRecommendationSetAndThatIsStillInPlace() {
        RecommendationEffect.Applied applied =
                new RecommendationEffect.Applied("com.example.app", 1234L, 0, AppPolicy.RESTRICTION_SOFT);
        assertEquals("com.example.app|1234|0|1", applied.encode());
        assertEquals(AppPolicy.RESTRICTION_SOFT,
                RecommendationEffect.Applied.decode(applied.encode()).appliedRestriction);
        // An entry stored by an earlier version, or a Gentle one, set no restriction.
        assertEquals(-1, RecommendationEffect.Applied.decode("com.example.app|1234|0").appliedRestriction);
        assertEquals("com.example.app|1234|0",
                new RecommendationEffect.Applied("com.example.app", 1234L, 0, -1).encode());

        assertTrue(RecommendationEffect.shouldLiftRestriction(
                AppPolicy.RESTRICTION_SOFT, AppPolicy.RESTRICTION_SOFT));
        // The user raised or removed it by hand since: it is theirs.
        assertFalse(RecommendationEffect.shouldLiftRestriction(
                AppPolicy.RESTRICTION_SOFT, AppPolicy.RESTRICTION_HARD));
        assertFalse(RecommendationEffect.shouldLiftRestriction(
                AppPolicy.RESTRICTION_SOFT, AppPolicy.RESTRICTION_NONE));
        // Nothing was set, so a restriction found now was never the recommendation's.
        assertFalse(RecommendationEffect.shouldLiftRestriction(-1, AppPolicy.RESTRICTION_HARD));
        assertFalse(RecommendationEffect.shouldLiftRestriction(-1, AppPolicy.RESTRICTION_NONE));
        assertNull(RecommendationEffect.Applied.decode("com.example.app|1234|0|x"));
        assertNull(RecommendationEffect.Applied.decode("com.example.app|1234|0|0|0"));
    }

    @Test
    public void anAppExemptFromBatteryOptimizationIsPointedOut() throws Exception {
        // From an Android 16 device: only the user's own exemptions count.
        java.util.Set<String> exempt = SuggestionLoader.userExempt(String.join("\n",
                "system-excidle,com.android.providers.calendar,10071",
                "system,com.android.shell,2000",
                "user,com.machiav3lli.backup,10171",
                "user,com.aurora.store,10170\r"));
        assertEquals(new java.util.HashSet<>(Arrays.asList(
                "com.machiav3lli.backup", "com.aurora.store")), exempt);
        assertTrue(SuggestionLoader.userExempt(null).isEmpty());
        assertTrue(SuggestionLoader.userExempt("").isEmpty());
        // The note is part of the reason, so it is read before a level is chosen.
        String screen = read("app/src/main/java/com/gree1d/reappzuku/ui/RecommendationsActivity.java");
        assertTrue(screen.contains("SuggestionLoader.isBatteryExempt(suggestion.packageName)"));
        assertTrue(screen.contains("R.string.recommendations_exempt_note"));
    }

    // ---- Smart standby ----

    @Test
    public void smartStandbyEndsTheIdleProcessOnlyWhenItHoldsASensor() throws Exception {
        String smart = read("app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java");
        int standby = smart.indexOf("if (setStandby(pkg)) {");
        int holds = smart.indexOf("if (holdsSensor(pkg, sensorHolders)) {");
        int kill = smart.indexOf("privilegedShell.stopPackage(pkg, PrivilegedShell.KillMode.KILL);");
        assertTrue(standby >= 0 && standby < holds && holds < kill);
        // "am kill", which only ends a process the system could drop anyway. Never a force-stop here.
        assertTrue(kill < smart.indexOf("if (idle >= forceStopDelay) {"));
        // One sensor dump per pass at most, and none when no app goes on standby.
        assertEquals(1, smart.split("dumpsys sensorservice", -1).length - 1);
        assertTrue(smart.contains("if (sensorHolders == null) {"));
        // With a shared uid the sensor may belong to another app.
        assertTrue(smart.contains(
                "return sensorHolders.containsKey(uid) && packages != null && packages.length == 1;"));
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
