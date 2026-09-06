package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.core.PrivilegedShell;
import com.gree1d.reappzuku.utils.AppModel;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;
import com.gree1d.reappzuku.utils.SleepModeLogManager;
import com.gree1d.reappzuku.core.ProtectedApps;

public class SleepModeManager {
    private static final String FILE_NAME = "SleepModeManager";

    public enum FreezeType {
        TIMER,
        PERMANENT
    }

    public enum FreezeMethod {
        SUSPEND,
        DISABLE
    }

    private final Context context;
    private final Handler handler;
    private final ExecutorService executor;
    private final ShellManager shellManager;
    private final PrivilegedShell privilegedShell;
    private final SharedPreferences sharedpreferences;
    private RestrictionsScheduler scheduler;
    private final Set<String> systemPackages = new HashSet<>();

    public SleepModeManager(Context context, Handler handler, ExecutorService executor,
            ShellManager shellManager) {
        this.context = context;
        this.handler = handler;
        this.executor = executor;
        this.shellManager = shellManager;
        this.privilegedShell = new PrivilegedShell(shellManager);
        this.sharedpreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public void setScheduler(RestrictionsScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public Set<String> getSleepModeApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_SLEEP_MODE_APPS, new HashSet<>()));
    }

    public Set<String> getPermanentFreezeApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_SLEEP_MODE_APPS_PERMANENT, new HashSet<>()));
    }

    public FreezeType getFreezeType(String packageName) {
        if (getPermanentFreezeApps().contains(packageName)) return FreezeType.PERMANENT;
        if (getSleepModeApps().contains(packageName)) return FreezeType.TIMER;
        return null;
    }

    public Set<String> getFrozenTimerApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_SLEEP_MODE_APPS_FROZEN, new HashSet<>()));
    }

    public boolean hasFrozenTimerApps() {
        return !getFrozenTimerApps().isEmpty();
    }

    public Set<String> getSuspendMethodApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_SLEEP_MODE_APPS_SUSPEND_METHOD, new HashSet<>()));
    }

    public FreezeMethod getFreezeMethod(String packageName) {
        if (isSystemPackage(packageName) || systemPackages.contains(packageName)) {
            return FreezeMethod.SUSPEND;
        }
        return getSuspendMethodApps().contains(packageName) ? FreezeMethod.SUSPEND : FreezeMethod.DISABLE;
    }

    public void setFreezeMethod(String packageName, FreezeMethod method) {
        if (isSystemPackage(packageName) || systemPackages.contains(packageName)) {
            AppDebugManager.w(Category.SLEEP_MODE, FILE_NAME + ": setFreezeMethod: ignored for system package=" + packageName + " (forced SUSPEND)");
            return;
        }
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": setFreezeMethod: package=" + packageName + ", method=" + method);
        Set<String> suspendApps = getSuspendMethodApps();
        applyMethodToSet(suspendApps, packageName, method);
        sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS_SUSPEND_METHOD, suspendApps).apply();
    }

    private void applyMethodToSet(Set<String> suspendApps, String packageName, FreezeMethod method) {
        if (method == FreezeMethod.SUSPEND) {
            suspendApps.add(packageName);
        } else {
            suspendApps.remove(packageName);
        }
    }

    private boolean markFrozen(String packageName) {
        Set<String> frozen = getFrozenTimerApps();
        frozen.add(packageName);
        boolean committed = sharedpreferences.edit()
                .putStringSet(KEY_SLEEP_MODE_APPS_FROZEN, frozen)
                .commit();
        if (!committed) {
            AppDebugManager.e(Category.SLEEP_MODE, FILE_NAME + ": markFrozen: failed to persist ownership marker for " + packageName);
        }
        return committed;
    }

    private boolean markUnfrozen(String packageName) {
        Set<String> frozen = getFrozenTimerApps();
        frozen.remove(packageName);
        boolean committed = sharedpreferences.edit()
                .putStringSet(KEY_SLEEP_MODE_APPS_FROZEN, frozen)
                .commit();
        if (!committed) {
            AppDebugManager.e(Category.SLEEP_MODE, FILE_NAME + ": markUnfrozen: failed to persist ownership marker removal for " + packageName);
        }
        return committed;
    }

    public boolean isSystemPackage(String packageName) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public boolean reapplyPermanentFreeze(String packageName) {
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": reapplyPermanentFreeze: re-applying permanent freeze, package=" + packageName);
        return freezeApp(packageName);
    }

    private boolean freezeApp(String packageName) {
        return freezeAppWithMethod(packageName, getFreezeMethod(packageName));
    }

    private boolean unfreezeApp(String packageName) {
        return unfreezeAppWithMethod(packageName, getFreezeMethod(packageName));
    }

    private boolean unfreezeOwnedTimerAppWithMethod(String packageName, FreezeMethod method) {
        if (!unfreezeAppWithMethod(packageName, method)) {
            return false;
        }
        if (markUnfrozen(packageName)) {
            return true;
        }

        // The durable marker is the recovery contract. If it cannot be cleared after thawing,
        // restore the physical freeze so the persisted ownership state remains truthful.
        boolean rollbackOk = freezeAppWithMethod(packageName, method);
        SleepModeLogManager.logFreeze(context, packageName, rollbackOk, method, FreezeType.TIMER);
        AppDebugManager.e(Category.SLEEP_MODE, FILE_NAME
                + ": unfreezeOwnedTimerAppWithMethod: marker clear failed; rollback=" + rollbackOk
                + ", package=" + packageName);
        return false;
    }

    private boolean freezeAppWithMethod(String packageName, FreezeMethod method) {
        PrivilegedShell.PackageStateAction action = method == FreezeMethod.SUSPEND
                ? PrivilegedShell.PackageStateAction.SUSPEND
                : PrivilegedShell.PackageStateAction.DISABLE_USER;
        boolean ok = privilegedShell.applyPackageStateBlocking(packageName, action);
        if (ok) {
            AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": freeze ok, package=" + packageName + ", method=" + method);
        } else {
            AppDebugManager.e(Category.SLEEP_MODE, FILE_NAME + ": freeze FAILED, package=" + packageName + ", method=" + method + ", action=" + action);
        }
        return ok;
    }

    private boolean unfreezeAppWithMethod(String packageName, FreezeMethod method) {
        PrivilegedShell.PackageStateAction action = method == FreezeMethod.SUSPEND
                ? PrivilegedShell.PackageStateAction.UNSUSPEND
                : PrivilegedShell.PackageStateAction.ENABLE;
        boolean ok = privilegedShell.applyPackageStateBlocking(packageName, action);
        if (ok) {
            AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": unfreeze ok, package=" + packageName + ", method=" + method);
        } else {
            AppDebugManager.e(Category.SLEEP_MODE, FILE_NAME + ": unfreeze FAILED, package=" + packageName + ", method=" + method + ", action=" + action);
        }
        return ok;
    }

    public void saveSleepModeApps(Set<String> timerPackages, Set<String> permanentPackages,
            Map<String, FreezeMethod> newMethods, Runnable onComplete) {
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": saveSleepModeApps: timer=" + timerPackages.size()
                + ", permanent=" + permanentPackages.size()
                + ", methodOverrides=" + (newMethods != null ? newMethods.size() : 0));
        Set<String> previousPermanent = getPermanentFreezeApps();
        Set<String> previousTimer = getSleepModeApps();
        Set<String> previousFrozenTimer = getFrozenTimerApps();

        Map<String, FreezeMethod> oldMethods = new java.util.HashMap<>();
        Set<String> allTouched = new HashSet<>();
        allTouched.addAll(previousPermanent);
        allTouched.addAll(previousTimer);
        allTouched.addAll(permanentPackages);
        allTouched.addAll(timerPackages);
        for (String packageName : allTouched) {
            oldMethods.put(packageName, getFreezeMethod(packageName));
        }

        sharedpreferences.edit()
                .putStringSet(KEY_SLEEP_MODE_APPS, new HashSet<>(timerPackages))
                .putStringSet(KEY_SLEEP_MODE_APPS_PERMANENT, new HashSet<>(permanentPackages))
                .apply();

        if (newMethods != null) {
            Set<String> suspendApps = getSuspendMethodApps();
            for (Map.Entry<String, FreezeMethod> entry : newMethods.entrySet()) {
                applyMethodToSet(suspendApps, entry.getKey(), entry.getValue());
            }
            sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS_SUSPEND_METHOD, suspendApps).apply();
        }

        Map<String, FreezeMethod> finalOldMethods = oldMethods;
        executor.execute(() -> {
            int unfreezeCount = 0;
            int freezeCount = 0;
            for (String packageName : allTouched) {
                FreezeType oldType = previousPermanent.contains(packageName) ? FreezeType.PERMANENT
                        : previousTimer.contains(packageName) ? FreezeType.TIMER : null;
                FreezeType newType = permanentPackages.contains(packageName) ? FreezeType.PERMANENT
                        : timerPackages.contains(packageName) ? FreezeType.TIMER : null;
                FreezeMethod oldMethod = finalOldMethods.get(packageName);
                FreezeMethod newMethod = getFreezeMethod(packageName);

                boolean wasPhysicallyFrozen = oldType == FreezeType.PERMANENT
                        || (oldType == FreezeType.TIMER && previousFrozenTimer.contains(packageName));
                boolean shouldBePhysicallyFrozen = newType == FreezeType.PERMANENT;

                if (!wasPhysicallyFrozen && !shouldBePhysicallyFrozen) continue;

                boolean methodChanged = oldMethod != newMethod;
                boolean needsUnfreeze = wasPhysicallyFrozen && (!shouldBePhysicallyFrozen || methodChanged);
                boolean needsFreeze = shouldBePhysicallyFrozen && (!wasPhysicallyFrozen || methodChanged);

                boolean unfreezeSucceeded = true;
                if (needsUnfreeze) {
                    unfreezeSucceeded = oldType == FreezeType.TIMER
                            ? unfreezeOwnedTimerAppWithMethod(packageName, oldMethod)
                            : unfreezeAppWithMethod(packageName, oldMethod);
                    SleepModeLogManager.logUnfreeze(context, packageName, unfreezeSucceeded, oldMethod, oldType);
                    if (!unfreezeSucceeded && methodChanged) {
                        Set<String> suspendApps = getSuspendMethodApps();
                        applyMethodToSet(suspendApps, packageName, oldMethod);
                        sharedpreferences.edit()
                                .putStringSet(KEY_SLEEP_MODE_APPS_SUSPEND_METHOD, suspendApps)
                                .commit();
                        AppDebugManager.w(Category.SLEEP_MODE, FILE_NAME
                                + ": saveSleepModeApps: kept old method after failed unfreeze, package=" + packageName
                                + ", method=" + oldMethod);
                    }
                    unfreezeCount++;
                }
                if (needsFreeze && unfreezeSucceeded) {
                    boolean ok = freezeAppWithMethod(packageName, newMethod);
                    SleepModeLogManager.logFreeze(context, packageName, ok, newMethod, newType);
                    if (!ok && methodChanged && wasPhysicallyFrozen) {
                        boolean rollbackOk = freezeAppWithMethod(packageName, oldMethod);
                        SleepModeLogManager.logFreeze(context, packageName, rollbackOk, oldMethod, oldType);
                        if (rollbackOk) {
                            Set<String> suspendApps = getSuspendMethodApps();
                            applyMethodToSet(suspendApps, packageName, oldMethod);
                            sharedpreferences.edit()
                                    .putStringSet(KEY_SLEEP_MODE_APPS_SUSPEND_METHOD, suspendApps)
                                    .commit();
                            if (oldType == FreezeType.TIMER) {
                                markFrozen(packageName);
                            }
                            AppDebugManager.w(Category.SLEEP_MODE, FILE_NAME
                                    + ": saveSleepModeApps: rolled back failed method transition, package=" + packageName
                                    + ", restoredMethod=" + oldMethod);
                        }
                    }
                    freezeCount++;
                }

                // A timer freeze that becomes permanent stays physically frozen, but its
                // temporary ownership marker must not cause SCREEN_ON to thaw it later.
                if (oldType == FreezeType.TIMER
                        && newType == FreezeType.PERMANENT
                        && wasPhysicallyFrozen
                        && !methodChanged) {
                    markUnfrozen(packageName);
                }
            }
            AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": saveSleepModeApps: applied " + freezeCount + " freeze(s), " + unfreezeCount + " unfreeze(s)");
            if (onComplete != null) handler.post(onComplete);
        });
    }

    public void saveSleepModeApps(Set<String> timerPackages, Set<String> permanentPackages,
            Runnable onComplete) {
        saveSleepModeApps(timerPackages, permanentPackages, null, onComplete);
    }

    public void saveSleepModeApps(Set<String> packages) {
        sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS, new HashSet<>(packages)).apply();
    }

    public boolean isSleepModeEnabled() {
        return sharedpreferences.getBoolean(KEY_SLEEP_MODE_ENABLED, false);
    }

    public void setSleepModeEnabled(boolean enabled) {
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": setSleepModeEnabled: " + enabled);
        sharedpreferences.edit().putBoolean(KEY_SLEEP_MODE_ENABLED, enabled).apply();
    }

    public void loadSleepModeApps(Consumer<List<AppModel>> callback) {
        executor.execute(() -> {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Set<String> timerApps = getSleepModeApps();
            Set<String> permanentApps = getPermanentFreezeApps();
            List<AppModel> result = new ArrayList<>();
            systemPackages.clear();
            for (ApplicationInfo appInfo : packages) {
                if (appInfo.packageName.equals(context.getPackageName())) continue;
                if (ProtectedApps.isProtected(context, appInfo.packageName)) continue;
                if ((appInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) continue;
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    systemPackages.add(appInfo.packageName);
                }
                boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                AppModel model = new AppModel(
                        pm.getApplicationLabel(appInfo).toString(),
                        appInfo.packageName,
                        "-",
                        0,
                        pm.getApplicationIcon(appInfo),
                        isSystem,
                        (appInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0,
                        ProtectedApps.isProtected(context, appInfo.packageName));
                boolean selected = timerApps.contains(appInfo.packageName)
                        || permanentApps.contains(appInfo.packageName);
                model.setSelected(selected);
                if (permanentApps.contains(appInfo.packageName)) {
                    model.setFreezeType(FreezeType.PERMANENT);
                } else if (timerApps.contains(appInfo.packageName)) {
                    model.setFreezeType(FreezeType.TIMER);
                }
                result.add(model);
            }
            handler.post(() -> callback.accept(result));
        });
    }

    public void freezeBackgroundRestrictedApps(Runnable onComplete) {
        Set<String> packages = getSleepModeApps();
        if (packages.isEmpty()) {
            AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": freezeBackgroundRestrictedApps: timer set is empty, nothing to freeze");
            if (onComplete != null) handler.post(onComplete);
            return;
        }
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": freezeBackgroundRestrictedApps: starting, candidates=" + packages.size());
        executor.execute(() -> {
            Set<String> alreadyFrozen = getFrozenTimerApps();
            int frozenNow = 0;
            int skippedAlreadyFrozen = 0;
            int skippedProtected = 0;
            int failed = 0;
            for (String packageName : packages) {
                if (alreadyFrozen.contains(packageName)) {
                    skippedAlreadyFrozen++;
                    continue;
                }
                if (scheduler != null && scheduler.isProtected(packageName, RestrictionsScheduler.PROTECT_SLEEP_MODE)) {
                    AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": freezeBackgroundRestrictedApps: skipping protected package=" + packageName);
                    skippedProtected++;
                    continue;
                }
                FreezeMethod method = getFreezeMethod(packageName);
                boolean ok = freezeApp(packageName);
                if (ok) {
                    if (markFrozen(packageName)) {
                        frozenNow++;
                    } else {
                        // Never leave a package frozen without a durable ownership marker.
                        boolean rollbackOk = unfreezeAppWithMethod(packageName, method);
                        SleepModeLogManager.logUnfreeze(context, packageName, rollbackOk, method, FreezeType.TIMER);
                        AppDebugManager.e(Category.SLEEP_MODE, FILE_NAME
                                + ": freezeBackgroundRestrictedApps: marker persistence failed; rollback=" + rollbackOk
                                + ", package=" + packageName);
                        ok = false;
                        failed++;
                    }
                } else {
                    failed++;
                }
                SleepModeLogManager.logFreeze(context, packageName, ok, method, FreezeType.TIMER);
            }
            AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": freezeBackgroundRestrictedApps: finished, frozen=" + frozenNow
                    + ", alreadyFrozen=" + skippedAlreadyFrozen + ", protected=" + skippedProtected + ", failed=" + failed);
            if (onComplete != null) handler.post(onComplete);
        });
    }

    public void unfreezeBackgroundRestrictedApps(Runnable onComplete) {
        // Only thaw packages for which ReAppzuku durably recorded a successful timer freeze.
        // Using the configured timer set here can otherwise ENABLE/UNSUSPEND an app that was
        // never frozen by ReAppzuku (for example because it was scheduler-protected or the
        // freeze command failed), overwriting an external/user-owned package state.
        Set<String> packages = getFrozenTimerApps();
        if (packages.isEmpty()) {
            AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": unfreezeBackgroundRestrictedApps: no owned frozen timer apps, nothing to unfreeze");
            if (onComplete != null) handler.post(onComplete);
            return;
        }
        AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": unfreezeBackgroundRestrictedApps: starting, ownedCandidates=" + packages.size());
        executor.execute(() -> {
            int unfrozenNow = 0;
            int failed = 0;
            Set<String> permanentApps = getPermanentFreezeApps();
            for (String packageName : packages) {
                if (permanentApps.contains(packageName)) {
                    // Ownership can move from timer -> permanent without a physical thaw. A stale
                    // timer marker must never thaw a package whose current desired state is permanent.
                    markUnfrozen(packageName);
                    AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME
                            + ": unfreezeBackgroundRestrictedApps: cleared stale timer marker for permanent package="
                            + packageName);
                    continue;
                }
                FreezeMethod method = getFreezeMethod(packageName);
                boolean ok = unfreezeOwnedTimerAppWithMethod(packageName, method);
                if (ok) {
                    unfrozenNow++;
                } else {
                    failed++;
                }
                SleepModeLogManager.logUnfreeze(context, packageName, ok, method, FreezeType.TIMER);
            }
            AppDebugManager.d(Category.SLEEP_MODE, FILE_NAME + ": unfreezeBackgroundRestrictedApps: finished, unfrozen=" + unfrozenNow + ", failed=" + failed);
            if (onComplete != null) handler.post(onComplete);
        });
    }
}
