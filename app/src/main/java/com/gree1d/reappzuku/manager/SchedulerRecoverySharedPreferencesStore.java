package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * Production-capable, currently unconnected storage adapter.
 * Dedicated private preferences file; the entire journal is one committed value.
 * Never place this nonportable recovery state in the user backup envelope.
 */
public final class SchedulerRecoverySharedPreferencesStore
        implements SchedulerRecoveryTransaction.DurableStore {
    public static final String PREFERENCES_FILE = "scheduler_recovery_journal_v1";
    public static final String KEY = "journal";

    private final SchedulerRecoveryJournalStore journal;

    public SchedulerRecoverySharedPreferencesStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences(
                PREFERENCES_FILE, Context.MODE_PRIVATE));
    }

    /** Test injection must use a dedicated, test-owned preference file. */
    public SchedulerRecoverySharedPreferencesStore(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("missing preferences");
        journal = new SchedulerRecoveryJournalStore(new SchedulerRecoveryJournalStore.Backend() {
            @Override public String read() {
                return preferences.getString(KEY, null);
            }

            @Override public boolean writeSync(String encoded) {
                return preferences.edit().putString(KEY, encoded).commit();
            }
        });
    }

    @Override public boolean commit(SchedulerRecoveryTransaction.Record record) {
        return journal.commit(record);
    }

    public Map<String, SchedulerRecoveryTransaction.Record> snapshot() {
        return journal.snapshot();
    }

    public boolean removeResolved(String packageName) {
        return journal.removeResolved(packageName);
    }

    public boolean isPoisoned() {
        return journal.isPoisoned();
    }
}
