// aidl/com/gree1d/reappzuku/core/shell/IShellCallback.aidl
package com.gree1d.reappzuku.core.shell;

/**
 * Callback used by IShellService#executeWithCallback to stream command output
 * back to the app process line by line, preserving the real-time behavior that
 * runShellCommandWithOutput() previously got from reading a ShizukuRemoteProcess
 * stream directly.
 *
 * oneway: calls are asynchronous and do not block the UserService's binder thread
 * while the app processes each line.
 */
oneway interface IShellCallback {
    void onLine(String line, boolean isError);
    void onComplete(int exitCode);
    void onError(String message);
}
