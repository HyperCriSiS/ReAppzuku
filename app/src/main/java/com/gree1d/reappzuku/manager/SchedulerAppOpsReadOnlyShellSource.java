package com.gree1d.reappzuku.manager;

import com.gree1d.reappzuku.core.PackageNameValidator;
import com.gree1d.reappzuku.core.ShellManager;

import java.util.regex.Pattern;

/**
 * Narrow read-only bridge from a pre-existing shell backend to the detached
 * package/UID AppOps diagnostics. The target identity is pinned at creation.
 *
 * No command here can modify an AppOp, request privilege, create a journal or
 * authorize restoration. Shell output has no guaranteed cross-query atomicity.
 */
public final class SchedulerAppOpsReadOnlyShellSource
        implements SchedulerAppOpsDualScopeDiagnostic.Source {

    /** A command must yield stdout ONLY on confirmed successful exit. */
    public interface CommandReader {
        String readSuccessful(String command);
    }

    private static final Pattern OP = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final int MAX_OUTPUT = 4096;

    private final String packageName;
    private final int uid;
    private final int userId;
    private final CommandReader reader;

    public SchedulerAppOpsReadOnlyShellSource(
            SchedulerInstalledPackageIdentity.Snapshot identity,
            CommandReader reader) {
        if (identity == null || !PackageNameValidator.isValid(identity.packageName)
                || !identity.validFor(identity.packageName) || reader == null
                || identity.uid / 100000 != identity.userId) {
            throw new IllegalArgumentException("invalid installed identity or command reader");
        }
        this.packageName = identity.packageName;
        this.uid = identity.uid;
        this.userId = identity.userId;
        this.reader = reader;
    }

    /** ShellManager remains permission-gated by its existing backend contract. */
    public static SchedulerAppOpsReadOnlyShellSource fromShellManager(
            SchedulerInstalledPackageIdentity.Snapshot identity, ShellManager shell) {
        if (shell == null) throw new IllegalArgumentException("null ShellManager");
        return new SchedulerAppOpsReadOnlyShellSource(identity, command -> {
            ShellManager.ShellResult result = shell.runShellCommandForResult(command);
            return result != null && result.succeeded() ? result.output() : null;
        });
    }

    @Override public String readPackage(String requestedPackage, String operation) {
        if (!packageName.equals(requestedPackage) || !validOperation(operation)) return null;
        return safeRead("cmd appops get --user " + userId + " " + packageName + " " + operation);
    }

    @Override public String readUid(int requestedUid, String operation) {
        if (requestedUid != uid || !validOperation(operation)) return null;
        return safeRead("cmd appops get --user " + userId + " " + uid + " " + operation);
    }

    private static boolean validOperation(String operation) {
        return operation != null && OP.matcher(operation).matches();
    }

    private String safeRead(String command) {
        try {
            String output = reader.readSuccessful(command);
            if (output == null || output.isEmpty() || output.length() > MAX_OUTPUT
                    || output.indexOf('\u0000') >= 0) return null;
            // Preserve content exactly for the strict parser. Do not turn
            // platform diagnostics, shell warnings or CRLF into valid modes.
            return output;
        } catch (RuntimeException failure) {
            return null;
        }
    }
}
