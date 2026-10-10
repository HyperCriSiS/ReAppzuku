package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Production-capable but not yet used by RestrictionsScheduler.
 * Private synchronous snapshot journal; never import it from backup.
 * SharedPreferences returned for the same file is identity-cached by Android
 * in one process, so independent adapters share a lock/poison flag.
 */
public final class SchedulerRecoverySharedPreferencesStore
        implements SchedulerRecoveryTransaction.DurableStore {
    public static final String PREFERENCES_FILE = "scheduler_recovery_journal_v1";
    public static final String KEY = "journal";

    private static final Map<SharedPreferences, SchedulerRecoveryJournalStore.Gate> GATES =
            new WeakHashMap<>();

    private static SchedulerRecoveryJournalStore.Gate gateFor(SharedPreferences prefs) {
        synchronized (GATES) {
            SchedulerRecoveryJournalStore.Gate gate = GATES.get(prefs);
            if (gate == null) {
                gate = new SchedulerRecoveryJournalStore.Gate();
                GATES.put(prefs, gate);
            }
            return gate;
        }
    }

    private final SchedulerRecoveryJournalStore journal;

    public SchedulerRecoverySharedPreferencesStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences(
                PREFERENCES_FILE, Context.MODE_PRIVATE));
    }

    /** Test injection must use a dedicated, test-owned preferences file. */
    public SchedulerRecoverySharedPreferencesStore(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("missing preferences");
        journal = new SchedulerRecoveryJournalStore(new SchedulerRecoveryJournalStore.Backend() {
            @Override public String read() {
                return preferences.getString(KEY, null);
            }

            @Override public boolean writeSync(String encoded) {
                return preferences.edit().putString(KEY, encoded).commit();
            }
        }, gateFor(preferences));
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
