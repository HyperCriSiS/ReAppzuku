package com.gree1d.reappzuku.utils.triggers.analyzers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProcessDumpParserTest {

    @Test
    public void parsesAospProcessRecordWithPidPrefix() {
        String dump = ""
                + "*APP* UID 10123 ProcessRecord{a1b2c3 1234:com.example.app/u0a123}\n"
                + "  adj=200 maxAdj=999\n"
                + "  curProcState=TOP cached=false\n";

        ProcessDumpParser.ProcessStateSnapshot state =
                ProcessDumpParser.parseProcessState(dump, "com.example.app");

        assertEquals(200, state.adj);
        assertEquals("TOP", state.procState);
        assertFalse(state.persistent);
    }

    @Test
    public void parsesOemStyleRemoteProcessAndSetProcState() {
        String dump = ""
                + "ProcessRecord{ff01 com.example.app:push/u0a321 persistent=true}\r\n"
                + "  setProcState=FOREGROUND_SERVICE adj=100\r\n";

        ProcessDumpParser.ProcessStateSnapshot state =
                ProcessDumpParser.parseProcessState(dump, "com.example.app");

        assertEquals(100, state.adj);
        assertEquals("FOREGROUND_SERVICE", state.procState);
        assertTrue(state.persistent);
    }

    @Test
    public void ignoresNeighboringPackageNameAndStopsAtNextProcess() {
        String dump = ""
                + "*APP* UID 10124 ProcessRecord{aaaa 2222:com.example.app2/u0a124}\n"
                + "  adj=0 curProcState=PERSISTENT\n"
                + "*APP* UID 10123 ProcessRecord{bbbb 3333:com.example.app/u0a123}\n"
                + "  adj=700 curProcState=CACHED_EMPTY\n"
                + "*APP* UID 10125 ProcessRecord{cccc 4444:com.other.app/u0a125}\n"
                + "  adj=0 curProcState=PERSISTENT\n";

        ProcessDumpParser.ProcessStateSnapshot state =
                ProcessDumpParser.parseProcessState(dump, "com.example.app");

        assertEquals(700, state.adj);
        assertEquals("CACHED_EMPTY", state.procState);
        assertFalse(state.persistent);
    }

    @Test
    public void returnsNullWhenTargetProcessIsAbsent() {
        String dump = "ProcessRecord{aaaa 2222:com.other.app/u0a124}\n  adj=100\n";
        assertNull(ProcessDumpParser.parseProcessState(dump, "com.example.app"));
    }

    @Test
    public void extractsPackageFromPidAndRemoteProcessBinderRecords() {
        assertEquals("com.push.client", ProcessDumpParser.extractProcessRecordPackage(
                "client=ProcessRecord{abcd 5555:com.push.client:remote/u0a200}"));
        assertEquals("android", ProcessDumpParser.extractProcessRecordPackage(
                "ProcessRecord{ef01 android/u0a0}"));
    }

    @Test
    public void parsesAospAndOemServiceRecordFormsExactly() {
        String shortForm = "ServiceRecord{abc123 u0 com.example.app/.SyncService}";
        String fullForm = "ServiceRecord{def456 u10 com.example.app/com.example.app.UploadService$Worker}";
        String neighbor = "ServiceRecord{eee777 u0 com.example.app2/.OtherService}";

        assertTrue(ProcessDumpParser.isServiceRecordForPackage(shortForm, "com.example.app"));
        assertEquals("SyncService",
                ProcessDumpParser.extractServiceShortName(shortForm, "com.example.app"));
        assertEquals("UploadService$Worker",
                ProcessDumpParser.extractServiceShortName(fullForm, "com.example.app"));
        assertFalse(ProcessDumpParser.isServiceRecordForPackage(neighbor, "com.example.app"));
        assertTrue(ProcessDumpParser.isServiceRecordLine(neighbor));
    }

    @Test
    public void extractsExactServiceRecordPackage() {
        String line = "* ServiceRecord{abc u0 com.example.app/.SyncService}";
        assertEquals("com.example.app", ProcessDumpParser.extractServiceRecordPackage(line));
        assertTrue(ProcessDumpParser.isServiceRecordForPackage(line, "com.example.app"));
        assertFalse(ProcessDumpParser.isServiceRecordForPackage(line, "com.example"));
        assertNull(ProcessDumpParser.extractServiceRecordPackage("not a service record"));
    }
}
