package com.gree1d.reappzuku.core;

import android.content.ServiceConnection;

import rikka.shizuku.Shizuku;

/** Package-private seam around the static Shizuku API for deterministic state-machine tests. */
interface ShizukuBridge {
    boolean pingBinder();
    int checkSelfPermission();
    void requestPermission(int requestCode);
    void addRequestPermissionResultListener(Shizuku.OnRequestPermissionResultListener listener);
    void removeRequestPermissionResultListener(Shizuku.OnRequestPermissionResultListener listener);
    void addBinderReceivedListenerSticky(Shizuku.OnBinderReceivedListener listener);
    void removeBinderReceivedListener(Shizuku.OnBinderReceivedListener listener);
    void addBinderDeadListener(Shizuku.OnBinderDeadListener listener);
    void removeBinderDeadListener(Shizuku.OnBinderDeadListener listener);
    void bindUserService(Shizuku.UserServiceArgs args, ServiceConnection connection);
    void unbindUserService(Shizuku.UserServiceArgs args, ServiceConnection connection, boolean remove);
}

final class RealShizukuBridge implements ShizukuBridge {
    @Override public boolean pingBinder() { return Shizuku.pingBinder(); }
    @Override public int checkSelfPermission() { return Shizuku.checkSelfPermission(); }
    @Override public void requestPermission(int requestCode) { Shizuku.requestPermission(requestCode); }
    @Override public void addRequestPermissionResultListener(Shizuku.OnRequestPermissionResultListener listener) {
        Shizuku.addRequestPermissionResultListener(listener);
    }
    @Override public void removeRequestPermissionResultListener(Shizuku.OnRequestPermissionResultListener listener) {
        Shizuku.removeRequestPermissionResultListener(listener);
    }
    @Override public void addBinderReceivedListenerSticky(Shizuku.OnBinderReceivedListener listener) {
        Shizuku.addBinderReceivedListenerSticky(listener);
    }
    @Override public void removeBinderReceivedListener(Shizuku.OnBinderReceivedListener listener) {
        Shizuku.removeBinderReceivedListener(listener);
    }
    @Override public void addBinderDeadListener(Shizuku.OnBinderDeadListener listener) {
        Shizuku.addBinderDeadListener(listener);
    }
    @Override public void removeBinderDeadListener(Shizuku.OnBinderDeadListener listener) {
        Shizuku.removeBinderDeadListener(listener);
    }
    @Override public void bindUserService(Shizuku.UserServiceArgs args, ServiceConnection connection) {
        Shizuku.bindUserService(args, connection);
    }
    @Override public void unbindUserService(Shizuku.UserServiceArgs args, ServiceConnection connection, boolean remove) {
        Shizuku.unbindUserService(args, connection, remove);
    }
}
