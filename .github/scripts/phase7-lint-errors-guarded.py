#!/usr/bin/env python3
from pathlib import Path
import runpy

checks = [
    ('app/src/main/AndroidManifest.xml', 'android:banner="@drawable/tv_banner"'),
    ('app/src/main/AndroidManifest.xml', 'tools:ignore="QueryAllPackagesPermission"'),
    ('app/src/main/java/com/gree1d/reappzuku/core/shell/ShizukuUserServiceImpl.java', 'private void destroyProcess(Process process)'),
    ('app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivityDialogs.java', 'rb.setTag(presetNumber);'),
    ('app/src/main/java/com/gree1d/reappzuku/ui/BackgroundAppsRecyclerViewAdapter.java', 'R.string.a11y_sleep_mode_app'),
    ('app/src/main/res/layout/activity_app_resource_detail.xml', 'android:contentDescription="@null"'),
    ('app/src/main/res/layout/item.xml', 'android:contentDescription="@string/a11y_app_action"'),
    ('app/src/main/res/layout/select_all_action.xml', 'android:contentDescription="@string/a11y_select_all"'),
]

if all(marker in Path(path).read_text() for path, marker in checks):
    print('final Phase 7 lint-error migration already applied')
else:
    runpy.run_path('.github/scripts/phase7-lint-errors.py', run_name='__main__')
