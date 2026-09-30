package com.gree1d.reappzuku.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class AppDatabaseMigrationTest {
    private static final String TEST_DB = "reappzuku-migration-test";
    private static final String TEST_DB_12 = "reappzuku-migration-12-test";

    @Rule
    public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    @Test
    public void migrate2To13_preservesExistingStatsAndAddsPolicySchema() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2);
        db.execSQL("INSERT INTO app_stats " +
                "(packageName, appName, killCount, relaunchCount, totalRecoveredKb, lastKillTime, lastRelaunchTime) " +
                "VALUES ('com.example.app', 'Example', 7, 3, 4096, 111, 222)");
        db.close();

        db = helper.runMigrationsAndValidate(
                TEST_DB,
                13,
                true,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13);

        try (Cursor cursor = db.query(
                "SELECT packageName, appName, relaunchCount, totalRecoveredKb, lastKillTime, lastRelaunchTime, lastKillSource " +
                        "FROM app_stats WHERE packageName='com.example.app'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("com.example.app", cursor.getString(0));
            assertEquals("Example", cursor.getString(1));
            assertEquals(3, cursor.getInt(2));
            assertEquals(4096L, cursor.getLong(3));
            assertEquals(111L, cursor.getLong(4));
            assertEquals(222L, cursor.getLong(5));
            assertTrue(cursor.isNull(6));
        }

        try (Cursor cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('app_policy','policy_preset') ORDER BY name")) {
            assertTrue(cursor.moveToFirst());
            assertEquals("app_policy", cursor.getString(0));
            assertTrue(cursor.moveToNext());
            assertEquals("policy_preset", cursor.getString(0));
        }
        db.close();
    }

    @Test
    public void migrate12To13_existingPoliciesDefaultToExplicitSource() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB_12, 12);
        db.execSQL("INSERT INTO app_policy " +
                "(packageName, strategy, presetId, customized, standbyDelayMs, forceStopDelayMs, " +
                "killMethod, bootCleanup, backgroundRestriction, protectMedia, " +
                "protectForegroundServices, protectWidgets, triggerMask, createdAt, updatedAt) " +
                "VALUES ('com.example.explicit', 3, NULL, 1, 3600000, 21600000, " +
                "0, 1, 0, 1, 1, 1, 1, 100, 100)");
        db.close();

        db = helper.runMigrationsAndValidate(
                TEST_DB_12, 13, true, AppDatabase.MIGRATION_12_13);

        try (Cursor cursor = db.query(
                "SELECT source FROM app_policy WHERE packageName='com.example.explicit'")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(AppPolicy.SOURCE_EXPLICIT, cursor.getInt(0));
        }
        db.close();
    }
}
