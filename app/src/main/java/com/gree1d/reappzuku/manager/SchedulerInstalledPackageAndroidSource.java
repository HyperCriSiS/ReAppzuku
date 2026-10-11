package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.UserHandle;

import com.gree1d.reappzuku.core.PackageNameValidator;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Read-only installed-package identity probe. The visible UID package catalog
 * is NOT guaranteed complete under Android package visibility restrictions.
 * The current APK signer is not a replacement for authenticated install history.
 */
public final class SchedulerInstalledPackageAndroidSource
        implements SchedulerInstalledPackageIdentity.Source {
    private final PackageManager pm;

    public SchedulerInstalledPackageAndroidSource(Context context) {
        if (context == null) throw new IllegalArgumentException("null context");
        pm = context.getApplicationContext().getPackageManager();
    }

    @Override @SuppressWarnings("deprecation")
    public SchedulerInstalledPackageIdentity.Snapshot read(String packageName) {
        if (!PackageNameValidator.isValid(packageName)) return null;
        try {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            PackageInfo info = pm.getPackageInfo(packageName, flags);
            if (info.applicationInfo == null || info.applicationInfo.uid <= 0) return null;
            int uid = info.applicationInfo.uid;
            if (pm.getPackageUid(packageName, 0) != uid) return null;
            String[] visible = pm.getPackagesForUid(uid);
            if (visible == null || visible.length != 1
                    || !packageName.equals(visible[0])) return null;
            Signature[] signers;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (info.signingInfo == null) return null;
                signers = info.signingInfo.getApkContentsSigners();
            } else {
                signers = info.signatures;
            }
            // Fail closed on missing/multiple signers. Rotation and shared UIDs
            // require a separate authenticated recovery contract.
            if (signers == null || signers.length != 1 || signers[0] == null) return null;
            byte[] certificate = signers[0].toByteArray();
            if (certificate == null || certificate.length == 0) return null;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                int value = b & 0xff;
                hex.append(Character.forDigit(value >>> 4, 16));
                hex.append(Character.forDigit(value & 15, 16));
            }
            int userId = UserHandle.getUserHandleForUid(uid).getIdentifier();
            long version = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
            return new SchedulerInstalledPackageIdentity.Snapshot(
                    packageName, uid, userId, hex.toString(), version,
                    info.firstInstallTime, info.lastUpdateTime, true);
        } catch (PackageManager.NameNotFoundException | SecurityException
                | IllegalArgumentException | NoSuchAlgorithmException failure) {
            return null;
        }
    }
}
