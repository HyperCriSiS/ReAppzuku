package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.gree1d.reappzuku.core.ShellBackendState;
import com.gree1d.reappzuku.core.ShellManager;
import org.junit.Test;

public final class SchedulerAppOpsReadOnlyShellSourceTest {
    private static final String PKG = "com.example.shell";
    private static final String SIGNER =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static SchedulerInstalledPackageIdentity.Snapshot identity() {
        return new SchedulerInstalledPackageIdentity.Snapshot(
                PKG, 10342, 0, SIGNER, 8, 1000, 2000, true);
    }

    private static final class Reader
            implements SchedulerAppOpsReadOnlyShellSource.CommandReader {
        final List<String> commands = new ArrayList<>();
        String next = "No operations.\nDefault mode: allow";
        boolean fail;
        @Override public String readSuccessful(String command) {
            commands.add(command);
            if (fail) throw new IllegalStateException("backend died");
            return next;
        }
    }

    @Test public void generatedCommandsAreExactAndTargetPinned() {
        Reader reader = new Reader();
        SchedulerAppOpsReadOnlyShellSource source =
                new SchedulerAppOpsReadOnlyShellSource(identity(), reader);
        assertEquals(reader.next, source.readPackage(PKG, "WAKE_LOCK"));
        assertEquals(reader.next, source.readUid(10342, "RUN_IN_BACKGROUND"));
        assertEquals(Arrays.asList(
                "cmd appops get --user 0 com.example.shell WAKE_LOCK",
                "cmd appops get --user 0 10342 RUN_IN_BACKGROUND"), reader.commands);
        assertNull(source.readPackage("com.other.target", "WAKE_LOCK"));
        assertNull(source.readUid(10343, "WAKE_LOCK"));
        assertNull(source.readPackage(PKG, "WAKE_LOCK;set"));
        assertNull(source.readPackage(PKG, ""));
        assertNull(source.readUid(10342, "bad op"));
        assertEquals("No rejected request executes a shell command", 2, reader.commands.size());
    }

    @Test public void warningsNullOversizeAndBackendDeathFailClosed() {
        Reader reader = new Reader();
        SchedulerAppOpsReadOnlyShellSource source =
                new SchedulerAppOpsReadOnlyShellSource(identity(), reader);
        reader.next = null;
        assertNull(source.readPackage(PKG, "WAKE_LOCK"));
        reader.next = "";
        assertNull(source.readPackage(PKG, "WAKE_LOCK"));
        reader.next = new String(new char[4097]).replace('\0', 'x');
        assertNull(source.readPackage(PKG, "WAKE_LOCK"));
        reader.next = "WAKE_LOCK: allow\u0000";
        assertNull(source.readPackage(PKG, "WAKE_LOCK"));
        reader.fail = true;
        assertNull(source.readPackage(PKG, "WAKE_LOCK"));

        reader.fail = false;
        reader.next = "Error: permission denied";
        // The adapter preserves unrecognized shell text. The strict catalog
        // parser, rather than a whitelist guess, must reject it.
        SchedulerInstalledPackageIdentity.Source id = pkg -> identity();
        SchedulerAppOpsCatalogDiagnostic.Result invalid =
                SchedulerAppOpsCatalogDiagnostic.capture(PKG, new String[]{"WAKE_LOCK"},
                        id, source);
        assertEquals(SchedulerAppOpsCatalogDiagnostic.Status.QUERY_UNKNOWN, invalid.status);
        assertTrue(invalid.operations.isEmpty());
        assertFalse(invalid.authorizesRestore());
    }

    @Test public void commandReaderCannotCreateRecoveryAuthority() {
        Reader reader = new Reader();
        SchedulerAppOpsReadOnlyShellSource source =
                new SchedulerAppOpsReadOnlyShellSource(identity(), reader);
        SchedulerInstalledPackageIdentity.Source id = pkg -> identity();
        SchedulerAppOpsCatalogDiagnostic.Result result =
                SchedulerAppOpsCatalogDiagnostic.capture(
                        PKG, new String[]{"WAKE_LOCK", "RUN_IN_BACKGROUND"}, id, source);
        assertTrue(result.consistentDiagnosticOnly());
        assertFalse(result.authorizesRestore());
        assertEquals(8, reader.commands.size());
        for (String command : reader.commands) {
            assertTrue(command.startsWith("cmd appops get --user 0 "));
            assertFalse(command.contains(" set "));
            assertFalse(command.contains(" reset "));
        }
        assertEquals(SchedulerAppOpsDualScopeDiagnostic.Presence.NOT_REPORTED,
                result.operations.get("WAKE_LOCK").packageLayer.presence);
        assertEquals("allow", result.operations.get("WAKE_LOCK").uidLayer.defaultHint);
    }

    @Test public void corruptIdentityOrMissingReaderNeverCreatesSource() {
        Reader reader = new Reader();
        SchedulerInstalledPackageIdentity.Snapshot invalid =
                new SchedulerInstalledPackageIdentity.Snapshot(
                        PKG, 10342, 1, SIGNER, 8, 1000, 2000, true);
        try {
            new SchedulerAppOpsReadOnlyShellSource(invalid, reader);
            fail("UID/user mismatch must be rejected");
        } catch (IllegalArgumentException expected) {
            // No untrusted cross-user lookup.
        }
        try {
            new SchedulerAppOpsReadOnlyShellSource(identity(), null);
            fail("Missing command reader must be rejected");
        } catch (IllegalArgumentException expected) {
            // No implicit shell fallback.
        }
    }

    @Test public void pinnedRootCannotSilentlySwitchToShizukuOrPermissionRequired() {
        AtomicReference<ShellBackendState> state =
                new AtomicReference<>(ShellBackendState.ROOT_READY);
        Reader reader = new Reader();
        SchedulerAppOpsReadOnlyShellSource source =
                new SchedulerAppOpsReadOnlyShellSource(identity(), reader,
                        state::get, ShellBackendState.ROOT_READY);
        assertNotNull(source.readPackage(PKG, "WAKE_LOCK"));
        state.set(ShellBackendState.SHIZUKU_READY);
        assertNull(source.readUid(10342, "WAKE_LOCK"));
        assertEquals("No command must execute after backend switch", 1, reader.commands.size());
        state.set(ShellBackendState.SHIZUKU_PERMISSION_REQUIRED);
        assertNull(source.readPackage(PKG, "WAKE_LOCK"));
        assertEquals(1, reader.commands.size());
    }

    @Test public void pinnedShizukuCannotUseRootAsFallback() {
        AtomicReference<ShellBackendState> state =
                new AtomicReference<>(ShellBackendState.SHIZUKU_READY);
        Reader reader = new Reader();
        SchedulerAppOpsReadOnlyShellSource source =
                new SchedulerAppOpsReadOnlyShellSource(identity(), reader,
                        state::get, ShellBackendState.SHIZUKU_READY);
        assertNotNull(source.readUid(10342, "WAKE_LOCK"));
        state.set(ShellBackendState.ROOT_READY);
        assertNull(source.readPackage(PKG, "WAKE_LOCK"));
        assertEquals(1, reader.commands.size());
    }

    @Test public void backendChangeDuringSuccessfulCommandDiscardsRawOutput() {
        AtomicReference<ShellBackendState> state =
                new AtomicReference<>(ShellBackendState.ROOT_READY);
        final int[] calls = {0};
        SchedulerAppOpsReadOnlyShellSource source =
                new SchedulerAppOpsReadOnlyShellSource(identity(), command -> {
                    calls[0]++;
                    state.set(ShellBackendState.SHIZUKU_READY);
                    return "WAKE_LOCK: allow";
                }, state::get, ShellBackendState.ROOT_READY);
        assertNull(source.readPackage(PKG, "WAKE_LOCK"));
        assertEquals(1, calls[0]);
        assertNull(source.readUid(10342, "WAKE_LOCK"));
        assertEquals(1, calls[0]);
    }

    @Test public void entireCatalogRejectsMidPassBackendLossWithoutPartialEvidence() {
        AtomicReference<ShellBackendState> state =
                new AtomicReference<>(ShellBackendState.SHIZUKU_READY);
        final int[] reads = {0};
        SchedulerAppOpsReadOnlyShellSource source =
                new SchedulerAppOpsReadOnlyShellSource(identity(), command -> {
                    if (++reads[0] == 3) state.set(ShellBackendState.SHIZUKU_LOST);
                    return "No operations.\nDefault mode: allow";
                }, state::get, ShellBackendState.SHIZUKU_READY);
        SchedulerAppOpsCatalogDiagnostic.Result result =
                SchedulerAppOpsCatalogDiagnostic.capture(PKG,
                        new String[]{"WAKE_LOCK", "RUN_IN_BACKGROUND"},
                        name -> identity(), source);
        assertEquals(SchedulerAppOpsCatalogDiagnostic.Status.QUERY_UNKNOWN, result.status);
        assertTrue(result.operations.isEmpty());
        assertNull(result.identity);
        assertFalse(result.authorizesRestore());
        assertEquals(3, reads[0]);
    }

    @Test public void unreadyBackendCannotConstructPinnedSource() {
        AtomicReference<ShellBackendState> state =
                new AtomicReference<>(ShellBackendState.SHIZUKU_BINDING);
        try {
            new SchedulerAppOpsReadOnlyShellSource(identity(), command -> "No operations.",
                    state::get, ShellBackendState.SHIZUKU_BINDING);
            fail("Binding is not a ready backend");
        } catch (IllegalArgumentException expected) {
            // No implicit backend initialization or permission request.
        }
        try {
            new SchedulerAppOpsReadOnlyShellSource(identity(), command -> "No operations.",
                    state::get, ShellBackendState.SHIZUKU_READY);
            fail("Stale Shizuku READY is not sufficient");
        } catch (IllegalArgumentException expected) {
            // No data source from a stale ready marker.
        }
    }

    @Test public void onlyFixedReadOnlyAppOpsSyntaxCrossesPrivilegedBoundary() {
        assertTrue(ShellManager.isReadOnlyAppOpsCommand(
                "cmd appops get --user 0 com.example.shell WAKE_LOCK"));
        assertTrue(ShellManager.isReadOnlyAppOpsCommand(
                "cmd appops get --user 0 10342 RUN_IN_BACKGROUND"));
        for (String invalid : new String[]{
                "cmd appops set --user 0 com.example.shell WAKE_LOCK ignore",
                "cmd appops reset com.example.shell",
                "cmd appops get --user current com.example.shell WAKE_LOCK",
                "cmd appops get --user 0 com.example.shell WAKE_LOCK;id",
                "cmd appops get --user 0 com.example.shell WAKE_LOCK && id",
                "cmd appops get --user 0 2147483648 WAKE_LOCK",
                "cmd appops get --user 0 com.example.shell WAKE_LOCK\nid",
                "cmd appops get --user 0 com.example.shell WAKE_LOCK ",
                "cmd appops get --user 0 com.example.shell WAKE_LOCK --debug",
                "cmd appops get --user 0 com.example.shell bad-mode",
                "cmd appops get --user 0 com.example.shell WAKE_LOCK > /tmp/out"
        }) {
            assertFalse(invalid, ShellManager.isReadOnlyAppOpsCommand(invalid));
        }
    }
}
