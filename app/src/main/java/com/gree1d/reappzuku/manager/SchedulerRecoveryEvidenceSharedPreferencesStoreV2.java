package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Unconnected production-capable v2 evidence adapter. Uses a dedicated private
 * preferences file, NEVER the v1 recovery-journal key or user backup envelope.
 * Must not be used as authorization for live scheduler mutations.
 */
public final class SchedulerRecoveryEvidenceSharedPreferencesStoreV2 {
    public static final String PREFERENCES_FILE = "scheduler_recovery_evidence_v2";
    public static final String KEY = "journal";

    private static final Map<SharedPreferences, SchedulerRecoveryEvidenceStoreV2.Gate>
            PREF_GATES = new WeakHashMap<>();

    private static SchedulerRecoveryEvidenceStoreV2.Gate gateFor(SharedPreferences prefs) {
        synchronized (PREF_GATES) {
            SchedulerRecoveryEvidenceStoreV2.Gate gate = PREF_GATES.get(prefs);
            if (gate == null) {
                gate = new SchedulerRecoveryEvidenceStoreV2.Gate();
                PREF_GATES.put(prefs, gate);
            }
            return gate;
        }
    }

    private final SchedulerRecoveryEvidenceStoreV2 store;

    public SchedulerRecoveryEvidenceSharedPreferencesStoreV2(Context context) {
        this(context.getApplicationContext().getSharedPreferences(
                PREFERENCES_FILE, Context.MODE_PRIVATE));
    }

    /** Inject only a dedicated test-owned preference file. */
    public SchedulerRecoveryEvidenceSharedPreferencesStoreV2(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("null preferences");
        store = new SchedulerRecoveryEvidenceStoreV2(
                new SchedulerRecoveryEvidenceStoreV2.Backend() {
                    @Override public String read() {
                        return preferences.getString(KEY, null);
                    }
                    @Override public boolean writeSync(String value) {
                        return preferences.edit().putString(KEY, value).commit();
                    }
                }, gateFor(preferences));
    }

    public Map<String, SchedulerRecoveryEvidenceV2> snapshot() {
        return store.snapshot();
    }

    public boolean commitCaptured(SchedulerRecoveryEvidenceV2 record) {
        return store.commitCaptured(record);
    }

    public boolean requireManualReview(SchedulerRecoveryEvidenceV2 record) {
        return store.requireManualReview(record);
    }

    public boolean isPoisoned() {
        return store.isPoisoned();
    }
}
