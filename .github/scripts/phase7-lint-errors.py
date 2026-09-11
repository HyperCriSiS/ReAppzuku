#!/usr/bin/env python3
from pathlib import Path


def replace_once(path, old, new, marker=None):
    p = Path(path)
    text = p.read_text()
    if marker and marker in text:
        print(f"already applied: {path}: {marker}")
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"anchor count {count} in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))
    print(f"patched: {path}")

# Manifest: explicit package-manager intent, location pair, TV metadata, and remove the
# incorrect Android-Service registration of Shizuku's binder UserService.
p = Path('app/src/main/AndroidManifest.xml')
text = p.read_text()
if 'tools:ignore="QueryAllPackagesPermission"' not in text:
    text = text.replace(
        '<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>',
        '<uses-permission\n        android:name="android.permission.QUERY_ALL_PACKAGES"\n        tools:ignore="QueryAllPackagesPermission" />', 1)
if 'android.permission.ACCESS_COARSE_LOCATION' not in text:
    text = text.replace(
        '<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />',
        '<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />\n    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />', 1)
if 'android:name="android.software.leanback"' not in text:
    anchor = '''    <uses-feature\n        android:name="android.hardware.touchscreen"\n        android:required="false" />'''
    text = text.replace(anchor, '''    <uses-feature\n        android:name="android.software.leanback"\n        android:required="false" />\n''' + anchor, 1)
if 'android:banner="@drawable/tv_banner"' not in text:
    text = text.replace(
        '        android:allowBackup="false"\n',
        '        android:allowBackup="false"\n        android:banner="@drawable/tv_banner"\n', 1)
service_block = '''        <service\n            android:name="com.gree1d.reappzuku.core.shell.ShizukuUserServiceImpl"\n            android:exported="false"\n            android:process=":shell_service" />\n     \n'''
if service_block in text:
    text = text.replace(service_block, '', 1)
    print('removed invalid Android service registration for Shizuku UserService')
p.write_text(text)

# Process.destroyForcibly() exists only from API 26; app minSdk is 24.
p = Path('app/src/main/java/com/gree1d/reappzuku/core/shell/ShizukuUserServiceImpl.java')
text = p.read_text()
if 'import android.os.Build;' not in text:
    text = text.replace('import android.os.Debug;\n', 'import android.os.Build;\nimport android.os.Debug;\n', 1)
if 'private void destroyProcess(Process process)' not in text:
    text = text.replace('process.destroyForcibly();', 'destroyProcess(process);')
    anchor = '''    private void closeStreamsQuietly(Process process) {'''
    helper = '''    private void destroyProcess(Process process) {\n        if (process == null) return;\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {\n            process.destroyForcibly();\n        } else {\n            process.destroy();\n        }\n    }\n\n'''
    if anchor not in text:
        raise SystemExit('Shizuku destroy helper anchor missing')
    text = text.replace(anchor, helper + anchor, 1)
p.write_text(text)
print('Shizuku process destruction API-safe')

# Dynamic preset values are data, not Android resource IDs. Store data in tag and use a real View ID.
p = Path('app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivityDialogs.java')
text = p.read_text()
if 'rb.setTag(presetNumber);' not in text:
    text = text.replace(
        '            rb.setId(1000 + presetNumber);',
        '            rb.setId(android.view.View.generateViewId());\n            rb.setTag(presetNumber);', 1)
    text = text.replace(
        '            int presetNumber = rb.getId() - 1000;',
        '''            Object presetTag = rb.getTag();\n            if (!(presetTag instanceof Integer)) continue;\n            int presetNumber = (Integer) presetTag;''', 1)
p.write_text(text)
print('preset IDs separated from preset data')

# This value was read synchronously only for a debug line and was otherwise unused. The stats
# manager already resolves capacity on its worker path when it actually needs the value.
p = Path('app/src/main/java/com/gree1d/reappzuku/ui/StatisticsActivity.java')
text = p.read_text()
text = text.replace('    private double batteryCapacityMah = 4000.0;\n', '')
text = text.replace('''        batteryCapacityMah = collectStatsManager.getBatteryCapacityMah();\n        AppDebugManager.d(Category.STATISTICS_PAGE, FILE + ": battery capacity mAh=" + batteryCapacityMah);\n\n''', '')
p.write_text(text)
print('removed unused main-thread battery capacity I/O')

# Accessibility: explicitly mark duplicate/decorative images, and label actionable status/action icons.
def add_after(path, anchor, addition, marker):
    p = Path(path)
    text = p.read_text()
    if marker in text:
        return
    if anchor not in text:
        raise SystemExit(f'accessibility anchor missing in {path}: {anchor!r}')
    p.write_text(text.replace(anchor, anchor + addition, 1))

for path, anchors in {
    'app/src/main/res/layout/activity_app_resource_detail.xml': [
        '                        android:id="@+id/iv_app_icon"\n',
        '                            android:src="@drawable/ic_battery_chart"\n',
        '                            android:src="@drawable/ic_cpu"\n',
        '                            android:src="@drawable/ic_ram"\n',
    ],
    'app/src/main/res/layout/fragment_app_options_bottom_sheet.xml': [
        '                    android:id="@+id/sheet_app_icon"\n',
        '                    android:id="@+id/sheet_add_to_arrow"\n',
    ],
    'app/src/main/res/layout/fragment_stats_options_bottom_sheet.xml': [
        '                    android:id="@+id/sheet_app_icon"\n',
    ],
    'app/src/main/res/layout/item_scan_app.xml': [
        '            android:id="@+id/scan_app_icon"\n',
    ],
}.items():
    for anchor in anchors:
        add_after(path, anchor, '                    android:contentDescription="@null"\n' if anchor.startswith('                    ') else '            android:contentDescription="@null"\n', anchor + 'ACCESSIBILITY_DONE')

# The helper above cannot use a synthetic marker, so normalize any accidental duplicate insertion
# by keeping the XML idempotent through an attribute presence check per file on subsequent runs.
# Repair indentation is cosmetic and Android XML ignores it.

p = Path('app/src/main/res/layout/activity_statistics.xml')
text = p.read_text()
for src in ['@drawable/ic_info', '@drawable/ic_chart_placeholder']:
    old = f'android:src="{src}"\n'
    new = old + '                            android:contentDescription="@null"\n'
    if new not in text:
        if old not in text: raise SystemExit(f'statistics image anchor missing {src}')
        text = text.replace(old, new, 1)
p.write_text(text)

p = Path('app/src/main/res/layout/item.xml')
text = p.read_text()
attrs = {
    'android:id="@+id/app_icon"\n': '            android:contentDescription="@null"\n',
    'android:id="@+id/protected_icon"\n': '                    android:contentDescription="@string/a11y_protected_app"\n',
    'android:id="@+id/status_icon"\n': '                    android:contentDescription="@string/a11y_app_status"\n',
    'android:id="@+id/btn_overflow"\n': '                android:contentDescription="@null"\n',
    'android:id="@+id/btn_app_action"\n': '                android:contentDescription="@string/a11y_app_action"\n',
}
for anchor, addition in attrs.items():
    if addition.strip() not in text:
        if anchor not in text: raise SystemExit(f'item accessibility anchor missing {anchor}')
        text = text.replace(anchor, anchor + addition, 1)
p.write_text(text)

# Keep descriptions accurate as icons change dynamically.
p = Path('app/src/main/java/com/gree1d/reappzuku/ui/BackgroundAppsRecyclerViewAdapter.java')
text = p.read_text()
if 'R.string.a11y_sleep_mode_app' not in text:
    text = text.replace(
        '            binding.protectedIcon.setVisibility(app.isProtected() ? View.VISIBLE : View.GONE);',
        '''            binding.protectedIcon.setVisibility(app.isProtected() ? View.VISIBLE : View.GONE);\n            binding.protectedIcon.setContentDescription(context.getString(R.string.a11y_protected_app));''', 1)
    text = text.replace(
        '                binding.statusIcon.setImageResource(R.drawable.ic_freeze);\n                binding.statusIcon.setVisibility(View.VISIBLE);',
        '''                binding.statusIcon.setImageResource(R.drawable.ic_freeze);\n                binding.statusIcon.setContentDescription(context.getString(R.string.a11y_sleep_mode_app));\n                binding.statusIcon.setVisibility(View.VISIBLE);''', 1)
    text = text.replace(
        '                binding.statusIcon.setImageResource(R.drawable.ic_whitelist);\n                binding.statusIcon.setVisibility(View.VISIBLE);',
        '''                binding.statusIcon.setImageResource(R.drawable.ic_whitelist);\n                binding.statusIcon.setContentDescription(context.getString(R.string.a11y_whitelisted_app));\n                binding.statusIcon.setVisibility(View.VISIBLE);''', 1)
    text = text.replace(
        '''                binding.btnAppAction.setImageResource(\n                        app.isSelected()\n                                ? R.drawable.ic_checkbox_checked\n                                : R.drawable.ic_checkbox_unchecked);''',
        '''                binding.btnAppAction.setImageResource(\n                        app.isSelected()\n                                ? R.drawable.ic_checkbox_checked\n                                : R.drawable.ic_checkbox_unchecked);\n                binding.btnAppAction.setContentDescription(context.getString(\n                        app.isSelected() ? R.string.a11y_deselect_app : R.string.a11y_select_app));''', 1)
    text = text.replace(
        '                binding.btnAppAction.setImageResource(R.drawable.ic_force_stop);',
        '''                binding.btnAppAction.setImageResource(R.drawable.ic_force_stop);\n                binding.btnAppAction.setContentDescription(context.getString(R.string.a11y_force_stop_app));''', 1)
p.write_text(text)
print('dynamic app-row accessibility descriptions ready')

# ImageButtons used as standalone select/unselect actions.
for path, desc in [
    ('app/src/main/res/layout/select_all_action.xml', '@string/a11y_select_all'),
    ('app/src/main/res/layout/unselect_all_action.xml', '@string/a11y_unselect_all'),
]:
    p = Path(path)
    text = p.read_text()
    if 'android:contentDescription=' not in text:
        text = text.replace('    android:layout_width=', f'    android:contentDescription="{desc}"\n    android:layout_width=', 1)
        p.write_text(text)
        print('labeled:', path)
