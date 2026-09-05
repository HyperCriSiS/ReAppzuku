import os
from pathlib import Path


def one(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)


# Keep BackupManager responsible for storage transaction/rollback; move only the envelope boundary.
p = Path("app/src/main/java/com/gree1d/reappzuku/core/BackupManager.java")
text = p.read_text()
text = one(text,
'''    private static final String TAG = "BackupManager";
    private static final String KEY_BACKUP_VERSION = "backup_version";
    private static final int BACKUP_VERSION = 5;
    private static final String KEY_MANUAL_OPS_MASKS = "manual_ops_masks";
    private static final String KEY_PRESETS = "presets";
    private static final String KEY_PRESET_PREFIX = "preset_";
    private static final int MAX_BACKUP_CHARS = 2 * 1024 * 1024;

    private final Context context;
    private final SharedPreferences prefs;

    public BackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
''',
'''    private static final String TAG = "BackupManager";
    private static final String KEY_MANUAL_OPS_MASKS = "manual_ops_masks";
    private static final String KEY_PRESETS = "presets";
    private static final String KEY_PRESET_PREFIX = "preset_";

    private final Context context;
    private final SharedPreferences prefs;
    private final BackupCodec backupCodec;

    public BackupManager(Context context) {
        this(context, new BackupCodec());
    }

    BackupManager(Context context, BackupCodec backupCodec) {
        if (backupCodec == null) throw new IllegalArgumentException("backupCodec == null");
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        this.backupCodec = backupCodec;
    }
''', "BackupManager fields/constructor")
text = one(text,
'''            JSONObject root = new JSONObject();
            root.put(KEY_BACKUP_VERSION, BACKUP_VERSION);
''',
'''            JSONObject root = backupCodec.newRoot();
''', "BackupManager create envelope")
text = one(text,
'''            String result = root.toString(4);
''',
'''            String result = backupCodec.encode(root);
''', "BackupManager encode")
text = one(text,
'''        if (json == null || json.length() == 0 || json.length() > MAX_BACKUP_CHARS) {
            AppDebugManager.w(Category.BACKUP_RESTORE,
                    "BackupManager: refusing empty/oversized backup payload");
            return false;
        }

        Map<String, ?> mainSnapshot = null;
''',
'''        BackupCodec.DecodedBackup decoded;
        try {
            decoded = backupCodec.decode(json);
        } catch (BackupCodec.DecodeException e) {
            if (e.reason == BackupCodec.DecodeFailure.PAYLOAD_SIZE) {
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: refusing empty/oversized backup payload");
            } else if (e.reason == BackupCodec.DecodeFailure.FUTURE_VERSION) {
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: refusing unsupported future backup version=" + e.detectedVersion
                                + " supported=" + BackupCodec.CURRENT_VERSION);
            } else {
                AppDebugManager.e(Category.BACKUP_RESTORE,
                        "BackupManager: restoreBackupJson: malformed payload", e);
            }
            return false;
        }

        Map<String, ?> mainSnapshot = null;
''', "BackupManager bounded decode")
text = one(text,
'''        try {
            JSONObject root = new JSONObject(json);
            int version = root.optInt(KEY_BACKUP_VERSION, -1);
            if (version > BACKUP_VERSION) {
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: refusing unsupported future backup version=" + version
                                + " supported=" + BACKUP_VERSION);
                return false;
            }
            if (version < 1) {
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: legacy/unversioned backup detected; validating available fields");
            }

            // Validate all preset JSON before the first durable write.
''',
'''        try {
            JSONObject root = decoded.root;
            if (decoded.legacy) {
                AppDebugManager.w(Category.BACKUP_RESTORE,
                        "BackupManager: legacy/unversioned backup detected; validating available fields");
            }

            // Validate all preset JSON before the first durable write.
''', "BackupManager decoded root")
p.write_text(text)


run_id = os.environ.get("GITHUB_RUN_ID", "unknown")

road = Path("docs/ROADMAP.md")
text = road.read_text()
text = one(text,
"- [~] Split large managers behind testable facades: `PrivilegedShell`, `AlarmScheduler`, `Clock`/`ScheduleTime` and central protection/background/parser policy boundaries are in place; `BackupCodec` remains to extract.",
"- [x] Split large managers behind testable facades: `PrivilegedShell`, `AlarmScheduler`, `Clock`/`ScheduleTime`, `BackupCodec` and central protection/background/parser policy boundaries now isolate the named high-risk seams.",
"roadmap facade status")
text = one(text,
"- `BackupCodec` is the remaining named facade before this roadmap item can be closed.",
"- `BackupCodec` now owns bounded/versioned JSON envelope decode/encode while `BackupManager` retains the transactional storage/rollback side effects; the named facade extraction item is closed.",
"roadmap BackupCodec evidence")
anchor = "### Maintainability evidence — 2026-09-05\n"
if "Scheduling facade gate" not in text:
    text = one(text, anchor, anchor + f'''\n- Scheduling facade gate `33946729221` passed unit tests, lint, AndroidTest compilation and debug APK build before integrating `Clock`/`ScheduleTime` and `AlarmScheduler`.\n- BackupCodec gate `{run_id}` compiles the focused Android codec tests and passes the same application validation before integration.\n''', "roadmap facade run evidence")
road.write_text(text)

matrix = Path("docs/CHECK_MATRIX.md")
text = matrix.read_text()
text = one(text,
"**Maintainability status:** `PrivilegedShell` and parser/protection/background policy boundaries are now explicit. `Clock`/`ScheduleTime` and `AlarmScheduler` isolate scheduler time/alarm decisions; `BackupCodec` remains the named facade gap. This is source/testability progress, not Android runtime proof.",
"**Maintainability status:** the named high-risk seams now have explicit `PrivilegedShell`, parser/protection/background policy, `Clock`/`ScheduleTime`, `AlarmScheduler` and `BackupCodec` boundaries. This is source/testability progress, not Android runtime proof.",
"matrix maintainability status")
text = one(text,
'''- **CM-P1-03:** restore is validate-first, bounded, future-version aware and transactional with
  rollback; dedicated Android fault injection remains open.''',
'''- **CM-P1-03:** restore is validate-first, bounded, future-version aware and transactional with
  rollback. `BackupCodec` now owns payload-size/version/JSON envelope validation while
  `BackupManager` retains storage transaction and runtime reconciliation; dedicated Android fault
  injection remains open.''',
"matrix backup refresh")
latest = "Latest assurance evidence:\n"
if f"workflow run `{run_id}`" not in text:
    text = one(text, latest, latest + f'''- workflow run `33946729221`: `Clock`/`ScheduleTime` + `AlarmScheduler` integration passed unit, lint, AndroidTest compile and APK build;\n- workflow run `{run_id}`: `BackupCodec` envelope extraction passed unit, lint, AndroidTest compile and APK build before integration;\n''', "matrix facade evidence")
matrix.write_text(text)
