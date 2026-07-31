// aidl/com/gree1d/reappzuku/core/shell/IShellService.aidl
package com.gree1d.reappzuku.core.shell;

import com.gree1d.reappzuku.core.shell.ShellExecResult;
import com.gree1d.reappzuku.core.shell.IShellCallback;

/**
 * Interface exposed by the long-lived Shizuku UserService that replaces the old
 * pattern of spawning one ShizukuRemoteProcess per command via Shizuku.newProcess().
 * The service process itself runs "sh -c <command>" internally and stays alive for
 * the lifetime of the binding, so no per-command AIDL/binder proxy is created or torn
 * down — the only long-lived binder is this service connection itself.
 */
interface IShellService {
    // Blocking call: runs the command to completion and returns the full result.
    // A timeout is enforced service-side (see ShizukuUserServiceImpl) so a hung
    // command cannot block this call indefinitely.
    ShellExecResult execute(String command);

    // Fire-and-forget call: runs the command and streams output back line by line
    // via the callback, then calls onComplete(exitCode) or onError(message).
    oneway void executeWithCallback(String command, IShellCallback callback);

    // Lets the app process explicitly tear down this UserService's own process.
    // 16777114 is Shizuku's reserved transaction code for the UserService destroy
    // contract: unbindUserService() alone does NOT kill the remote process, Shizuku
    // calls this specific method (and expects the implementation to call System.exit())
    // to actually terminate it. This exact method id must be preserved.
    void destroy() = 16777114;
}
