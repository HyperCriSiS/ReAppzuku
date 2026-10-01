package com.gree1d.reappzuku.core;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.PolicyPreset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict portable codec for the canonical Phase 9 lifecycle-policy backup section. */
public final class Phase9BackupCodec {
    static final String KEY_PHASE9 = "phase9";
    static final String KEY_APP_POLICIES = "app_policies";
    static final String KEY_POLICY_PRESETS = "policy_presets";
    static final String KEY_NEW_APP_SETUP = "new_app_setup";

    private static final String KEY_MODE = "mode";
    private static final String KEY_DEFAULT_PRESET_ID = "default_preset_id";
    private static final String KEY_PENDING = "pending";

    private Phase9BackupCodec() {}

    public static final class Snapshot {
        public final List<AppPolicy> appPolicies;
        public final List<PolicyPreset> userPresets;
        public final int newAppSetupMode;
        public final long defaultPresetId;
        public final Set<String> pendingSetup;

        Snapshot(
                List<AppPolicy> appPolicies,
                List<PolicyPreset> userPresets,
                int newAppSetupMode,
                long defaultPresetId,
                Set<String> pendingSetup) {
            this.appPolicies = appPolicies;
            this.userPresets = userPresets;
            this.newAppSetupMode = newAppSetupMode;
            this.defaultPresetId = defaultPresetId;
            this.pendingSetup = pendingSetup;
        }
    }

    public static void putPhase9(
            JSONObject root,
            List<AppPolicy> appPolicies,
            List<PolicyPreset> userPresets,
            int newAppSetupMode,
            long defaultPresetId,
            Set<String> pendingSetup) throws Exception {
        if (root == null) throw new IllegalArgumentException("root == null");
        Snapshot snapshot = new Snapshot(
                new ArrayList<>(appPolicies),
                new ArrayList<>(userPresets),
                newAppSetupMode,
                defaultPresetId,
                new HashSet<>(pendingSetup));
        validate(snapshot);

        JSONObject phase9 = new JSONObject();
        JSONArray policies = new JSONArray();
        for (AppPolicy policy : snapshot.appPolicies) {
            policies.put(policyToJson(policy));
        }
        JSONArray presets = new JSONArray();
        for (PolicyPreset preset : snapshot.userPresets) {
            presets.put(presetToJson(preset));
        }
        JSONObject setup = new JSONObject()
                .put(KEY_MODE, snapshot.newAppSetupMode)
                .put(KEY_DEFAULT_PRESET_ID, snapshot.defaultPresetId)
                .put(KEY_PENDING, new JSONArray(snapshot.pendingSetup));

        phase9.put(KEY_APP_POLICIES, policies);
        phase9.put(KEY_POLICY_PRESETS, presets);
        phase9.put(KEY_NEW_APP_SETUP, setup);
        root.put(KEY_PHASE9, phase9);
    }

    public static Snapshot parse(JSONObject root, int backupVersion) throws Exception {
        if (backupVersion < 7) return null;
        if (root == null || !root.has(KEY_PHASE9)) {
            throw new IllegalArgumentException("Backup v7 requires phase9 section");
        }

        JSONObject phase9 = root.getJSONObject(KEY_PHASE9);
        JSONArray policiesJson = phase9.getJSONArray(KEY_APP_POLICIES);
        JSONArray presetsJson = phase9.getJSONArray(KEY_POLICY_PRESETS);
        JSONObject setupJson = phase9.getJSONObject(KEY_NEW_APP_SETUP);

        BackupCollectionPolicy.requirePackageEntryCount(KEY_APP_POLICIES, policiesJson.length());
        if (presetsJson.length() < 0
                || presetsJson.length() > BackupCollectionPolicy.MAX_PACKAGE_ENTRIES) {
            throw new IllegalArgumentException("policy preset collection out of bounds");
        }

        List<AppPolicy> policies = new ArrayList<>(policiesJson.length());
        for (int i = 0; i < policiesJson.length(); i++) {
            policies.add(policyFromJson(policiesJson.getJSONObject(i)));
        }

        List<PolicyPreset> presets = new ArrayList<>(presetsJson.length());
        for (int i = 0; i < presetsJson.length(); i++) {
            presets.add(presetFromJson(presetsJson.getJSONObject(i)));
        }

        JSONArray pendingJson = setupJson.getJSONArray(KEY_PENDING);
        BackupCollectionPolicy.requirePackageEntryCount(KEY_PENDING, pendingJson.length());
        Set<String> pending = new HashSet<>();
        for (int i = 0; i < pendingJson.length(); i++) {
            String packageName = pendingJson.getString(i);
            if (!PackageNameValidator.isValid(packageName) || !pending.add(packageName)) {
                throw new IllegalArgumentException("Invalid or duplicate pending package");
            }
        }

        Snapshot snapshot = new Snapshot(
                policies,
                presets,
                requireInt(setupJson, KEY_MODE),
                requireLong(setupJson, KEY_DEFAULT_PRESET_ID),
                pending);
        validate(snapshot);
        return snapshot;
    }

    private static void validate(Snapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot == null");
        BackupCollectionPolicy.requirePackageEntryCount(
                KEY_APP_POLICIES, snapshot.appPolicies.size());
        if (snapshot.userPresets.size() > BackupCollectionPolicy.MAX_PACKAGE_ENTRIES) {
            throw new IllegalArgumentException("policy preset collection out of bounds");
        }
        BackupCollectionPolicy.requirePackageEntryCount(
                KEY_PENDING, snapshot.pendingSetup.size());

        if (snapshot.newAppSetupMode < NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL
                || snapshot.newAppSetupMode > NewAppSetupPolicy.MODE_LEAVE_UNMANAGED) {
            throw new IllegalArgumentException("Invalid new-app setup mode");
        }
        if (snapshot.defaultPresetId <= 0L) {
            throw new IllegalArgumentException("Invalid default preset id");
        }

        Set<Long> availablePresetIds = builtInPresetIds();
        Set<Long> userPresetIds = new HashSet<>();
        for (PolicyPreset preset : snapshot.userPresets) {
            validatePreset(preset);
            if (!userPresetIds.add(preset.id)) {
                throw new IllegalArgumentException("Duplicate policy preset id");
            }
            availablePresetIds.add(preset.id);
        }

        Map<String, AppPolicy> policiesByPackage = new HashMap<>();
        for (AppPolicy policy : snapshot.appPolicies) {
            validatePolicy(policy, availablePresetIds);
            if (policiesByPackage.put(policy.packageName, policy) != null) {
                throw new IllegalArgumentException("Duplicate app policy package");
            }
        }

        for (String packageName : snapshot.pendingSetup) {
            if (!PackageNameValidator.isValid(packageName)) {
                throw new IllegalArgumentException("Invalid pending package");
            }
            AppPolicy policy = policiesByPackage.get(packageName);
            if (policy == null
                    || policy.source != AppPolicy.SOURCE_EXPLICIT
                    || policy.strategy != AppPolicy.STRATEGY_UNMANAGED) {
                throw new IllegalArgumentException(
                        "Needs-setup package must have explicit unmanaged policy");
            }
        }
    }

    private static void validatePolicy(AppPolicy policy, Set<Long> availablePresetIds) {
        if (policy == null || !PackageNameValidator.isValid(policy.packageName)) {
            throw new IllegalArgumentException("Invalid app policy package");
        }
        if (policy.source != AppPolicy.SOURCE_EXPLICIT) {
            throw new IllegalArgumentException("Only explicit app policies are portable");
        }
        validateCommon(
                policy.strategy,
                policy.standbyDelayMs,
                policy.forceStopDelayMs,
                policy.killMethod,
                policy.backgroundRestriction,
                policy.triggerMask);
        if (policy.presetId != null) {
            if (policy.presetId <= 0L) {
                throw new IllegalArgumentException("Invalid app policy preset id");
            }
            if (!policy.customized && !availablePresetIds.contains(policy.presetId)) {
                throw new IllegalArgumentException(
                        "Non-customized policy references missing preset");
            }
        }
        validateTimestamps(policy.createdAt, policy.updatedAt);
    }

    private static void validatePreset(PolicyPreset preset) {
        if (preset == null || preset.id <= PolicyPresetSeeder.PRESET_AGGRESSIVE) {
            throw new IllegalArgumentException("User preset id collides with built-in range");
        }
        if (preset.builtIn) {
            throw new IllegalArgumentException("Built-in presets are not portable payload");
        }
        String name = preset.name != null ? preset.name.trim() : "";
        if (name.isEmpty() || name.length() > 80) {
            throw new IllegalArgumentException("Invalid policy preset name");
        }
        validateCommon(
                preset.strategy,
                preset.standbyDelayMs,
                preset.forceStopDelayMs,
                preset.killMethod,
                preset.backgroundRestriction,
                preset.triggerMask);
        validateTimestamps(preset.createdAt, preset.updatedAt);
    }

    private static void validateCommon(
            int strategy,
            long standbyDelayMs,
            long forceStopDelayMs,
            int killMethod,
            int backgroundRestriction,
            long triggerMask) {
        if (strategy < AppPolicy.STRATEGY_UNMANAGED
                || strategy > AppPolicy.STRATEGY_IMMEDIATE) {
            throw new IllegalArgumentException("Invalid policy strategy");
        }
        if (standbyDelayMs < 0L || forceStopDelayMs < 0L) {
            throw new IllegalArgumentException("Negative lifecycle delay");
        }
        if (strategy == AppPolicy.STRATEGY_SMART
                && forceStopDelayMs != Long.MAX_VALUE
                && forceStopDelayMs < standbyDelayMs) {
            throw new IllegalArgumentException("Smart force-stop precedes standby");
        }
        if (killMethod != AppPolicy.KILL_METHOD_FORCE_STOP
                && killMethod != AppPolicy.KILL_METHOD_AM_KILL) {
            throw new IllegalArgumentException("Invalid kill method");
        }
        if (backgroundRestriction < AppPolicy.RESTRICTION_NONE
                || backgroundRestriction > AppPolicy.RESTRICTION_MANUAL) {
            throw new IllegalArgumentException("Invalid background restriction");
        }
        if ((triggerMask & ~AppPolicyEditorModel.ALL_TRIGGER_BITS) != 0L) {
            throw new IllegalArgumentException("Unknown trigger bits");
        }
    }

    private static void validateTimestamps(long createdAt, long updatedAt) {
        if (createdAt < 0L || updatedAt < 0L
                || (createdAt > 0L && updatedAt > 0L && updatedAt < createdAt)) {
            throw new IllegalArgumentException("Invalid policy timestamps");
        }
    }

    private static Set<Long> builtInPresetIds() {
        Set<Long> result = new HashSet<>();
        result.add(PolicyPresetSeeder.PRESET_NEVER_TOUCH);
        result.add(PolicyPresetSeeder.PRESET_MESSENGER);
        result.add(PolicyPresetSeeder.PRESET_MEDIA);
        result.add(PolicyPresetSeeder.PRESET_BALANCED);
        result.add(PolicyPresetSeeder.PRESET_RARELY_USED);
        result.add(PolicyPresetSeeder.PRESET_AGGRESSIVE);
        return result;
    }

    private static JSONObject policyToJson(AppPolicy policy) throws Exception {
        JSONObject json = new JSONObject()
                .put("package_name", policy.packageName)
                .put("strategy", policy.strategy)
                .put("source", policy.source)
                .put("customized", policy.customized)
                .put("standby_delay_ms", policy.standbyDelayMs)
                .put("force_stop_delay_ms", policy.forceStopDelayMs)
                .put("kill_method", policy.killMethod)
                .put("boot_cleanup", policy.bootCleanup)
                .put("background_restriction", policy.backgroundRestriction)
                .put("protect_media", policy.protectMedia)
                .put("protect_foreground_services", policy.protectForegroundServices)
                .put("protect_widgets", policy.protectWidgets)
                .put("trigger_mask", policy.triggerMask)
                .put("created_at", policy.createdAt)
                .put("updated_at", policy.updatedAt);
        json.put("preset_id", policy.presetId != null ? policy.presetId : JSONObject.NULL);
        return json;
    }

    private static AppPolicy policyFromJson(JSONObject json) throws Exception {
        AppPolicy policy = new AppPolicy(requireString(json, "package_name"));
        policy.strategy = requireInt(json, "strategy");
        policy.source = requireInt(json, "source");
        policy.customized = requireBoolean(json, "customized");
        policy.standbyDelayMs = requireLong(json, "standby_delay_ms");
        policy.forceStopDelayMs = requireLong(json, "force_stop_delay_ms");
        policy.killMethod = requireInt(json, "kill_method");
        policy.bootCleanup = requireBoolean(json, "boot_cleanup");
        policy.backgroundRestriction = requireInt(json, "background_restriction");
        policy.protectMedia = requireBoolean(json, "protect_media");
        policy.protectForegroundServices = requireBoolean(json, "protect_foreground_services");
        policy.protectWidgets = requireBoolean(json, "protect_widgets");
        policy.triggerMask = requireLong(json, "trigger_mask");
        policy.createdAt = requireLong(json, "created_at");
        policy.updatedAt = requireLong(json, "updated_at");
        policy.presetId = json.isNull("preset_id") ? null : requireLong(json, "preset_id");
        return policy;
    }

    private static JSONObject presetToJson(PolicyPreset preset) throws Exception {
        return new JSONObject()
                .put("id", preset.id)
                .put("name", preset.name)
                .put("strategy", preset.strategy)
                .put("standby_delay_ms", preset.standbyDelayMs)
                .put("force_stop_delay_ms", preset.forceStopDelayMs)
                .put("kill_method", preset.killMethod)
                .put("boot_cleanup", preset.bootCleanup)
                .put("background_restriction", preset.backgroundRestriction)
                .put("protect_media", preset.protectMedia)
                .put("protect_foreground_services", preset.protectForegroundServices)
                .put("protect_widgets", preset.protectWidgets)
                .put("trigger_mask", preset.triggerMask)
                .put("built_in", preset.builtIn)
                .put("created_at", preset.createdAt)
                .put("updated_at", preset.updatedAt);
    }

    private static PolicyPreset presetFromJson(JSONObject json) throws Exception {
        PolicyPreset preset = new PolicyPreset(requireString(json, "name"));
        preset.id = requireLong(json, "id");
        preset.strategy = requireInt(json, "strategy");
        preset.standbyDelayMs = requireLong(json, "standby_delay_ms");
        preset.forceStopDelayMs = requireLong(json, "force_stop_delay_ms");
        preset.killMethod = requireInt(json, "kill_method");
        preset.bootCleanup = requireBoolean(json, "boot_cleanup");
        preset.backgroundRestriction = requireInt(json, "background_restriction");
        preset.protectMedia = requireBoolean(json, "protect_media");
        preset.protectForegroundServices =
                requireBoolean(json, "protect_foreground_services");
        preset.protectWidgets = requireBoolean(json, "protect_widgets");
        preset.triggerMask = requireLong(json, "trigger_mask");
        preset.builtIn = requireBoolean(json, "built_in");
        preset.createdAt = requireLong(json, "created_at");
        preset.updatedAt = requireLong(json, "updated_at");
        return preset;
    }

    private static String requireString(JSONObject json, String key) throws Exception {
        Object raw = json.get(key);
        if (!(raw instanceof String)) {
            throw new IllegalArgumentException("Expected string for " + key);
        }
        return (String) raw;
    }

    private static boolean requireBoolean(JSONObject json, String key) throws Exception {
        Object raw = json.get(key);
        if (!(raw instanceof Boolean)) {
            throw new IllegalArgumentException("Expected boolean for " + key);
        }
        return (Boolean) raw;
    }

    private static int requireInt(JSONObject json, String key) throws Exception {
        long value = requireLong(json, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer out of range for " + key);
        }
        return (int) value;
    }

    private static long requireLong(JSONObject json, String key) throws Exception {
        Object raw = json.get(key);
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException("Expected integer for " + key);
        }
        Number number = (Number) raw;
        long value = number.longValue();
        double asDouble = number.doubleValue();
        if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)
                || asDouble != (double) value) {
            throw new IllegalArgumentException("Non-integral number for " + key);
        }
        return value;
    }
}
