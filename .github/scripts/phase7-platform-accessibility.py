#!/usr/bin/env python3
from pathlib import Path


def replace_once(path, old, new, marker=None):
    p = Path(path)
    text = p.read_text()
    if marker and marker in text:
        print(f"already applied: {path}")
        return
    if text.count(old) != 1:
        raise SystemExit(f"anchor count {text.count(old)} for {path}")
    p.write_text(text.replace(old, new, 1))
    print(f"patched: {path}")

p = Path("app/src/main/java/com/gree1d/reappzuku/manager/AdditionalScenariosManager.java")
text = p.read_text()
if 'ContextCompat.RECEIVER_NOT_EXPORTED' not in text:
    text = text.replace('import android.os.Build;\n', '')
    if 'import androidx.core.content.ContextCompat;' not in text:
        text = text.replace('import androidx.annotation.Nullable;\n', 'import androidx.annotation.Nullable;\nimport androidx.core.content.ContextCompat;\n')
    old = '''        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {\n            context.registerReceiver(hardwareEventReceiver, filter, Context.RECEIVER_NOT_EXPORTED);\n        } else {\n            context.registerReceiver(hardwareEventReceiver, filter);\n        }'''
    new = '''        ContextCompat.registerReceiver(\n                context, hardwareEventReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);'''
    if old not in text: raise SystemExit('receiver anchor missing')
    p.write_text(text.replace(old, new, 1))
    print('patched receiver')
else: print('receiver already patched')

replace_once('app/src/main/AndroidManifest.xml', '''    <uses-feature\n        android:name="android.hardware.touchscreen"\n        android:required="false" />''', '''    <uses-feature\n        android:name="android.hardware.touchscreen"\n        android:required="false" />\n    <uses-feature\n        android:name="android.hardware.bluetooth"\n        android:required="false" />\n    <uses-feature\n        android:name="android.hardware.location.gps"\n        android:required="false" />''', 'android:name="android.hardware.location.gps"')

replace_once('app/src/main/res/layout/activity_main.xml', '''        <RelativeLayout\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:background="?android:attr/colorBackground"\n            android:paddingTop="4dp"\n            android:paddingBottom="4dp"\n            android:paddingStart="8dp"\n            android:paddingEnd="4dp">''', '''        <LinearLayout\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:orientation="horizontal"\n            android:gravity="center_vertical"\n            android:background="?android:attr/colorBackground"\n            android:paddingTop="4dp"\n            android:paddingBottom="4dp"\n            android:paddingStart="8dp"\n            android:paddingEnd="4dp">''', 'android:orientation="horizontal"\n            android:gravity="center_vertical"\n            android:background="?android:attr/colorBackground"')
p=Path('app/src/main/res/layout/activity_main.xml'); text=p.read_text()
if 'android:id="@+id/running_apps"\n                android:layout_width="0dp"' not in text:
    text=text.replace('''android:id="@+id/running_apps"\n                android:layout_width="wrap_content"\n                android:layout_height="wrap_content"\n                android:layout_centerVertical="true"\n                android:layout_alignParentStart="true"''', '''android:id="@+id/running_apps"\n                android:layout_width="0dp"\n                android:layout_height="wrap_content"\n                android:layout_weight="1"\n                android:paddingEnd="8dp"''',1)
    text=text.replace('''                android:layout_alignParentEnd="true"\n                android:layout_centerVertical="true"\n''','',1)
    text=text.replace('        </RelativeLayout>','        </LinearLayout>',1)
    p.write_text(text); print('patched activity_main children')
else: print('activity_main children already patched')

p=Path('app/src/main/java/com/gree1d/reappzuku/ui/ColorPickerDialog.java'); text=p.read_text()
if text.count('public boolean performClick()') < 2:
    text=text.replace('''            }\n            return true;\n        }\n    }\n\n    public static class HueBarView''','''            } else if (event.getAction() == MotionEvent.ACTION_UP) {\n                performClick();\n            }\n            return true;\n        }\n\n        @Override\n        public boolean performClick() {\n            super.performClick();\n            return true;\n        }\n    }\n\n    public static class HueBarView''',1)
    text=text.replace('''            }\n            return true;\n        }\n    }\n}''','''            } else if (event.getAction() == MotionEvent.ACTION_UP) {\n                performClick();\n            }\n            return true;\n        }\n\n        @Override\n        public boolean performClick() {\n            super.performClick();\n            return true;\n        }\n    }\n}''',1)
    p.write_text(text); print('patched ColorPicker click accessibility')
else: print('ColorPicker already patched')

replace_once('app/src/main/java/com/gree1d/reappzuku/ui/StatisticsActivity.java', '''            row.setBackground(obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground}).getDrawable(0));''', '''            android.content.res.TypedArray selectableAttrs =\n                    obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});\n            row.setBackground(selectableAttrs.getDrawable(0));\n            selectableAttrs.recycle();''', 'android.content.res.TypedArray selectableAttrs')

for path in ['app/src/main/res/layout/activity_log_detail.xml','app/src/main/res/layout/dialog_top_offenders.xml']:
    p=Path(path); text=p.read_text()
    if 'android:hint="@string/filter_label"' not in text:
        pos=text.find('        android:layout_height="wrap_content"\n', text.find('<com.google.android.material.textfield.TextInputLayout'))
        if pos<0: raise SystemExit(f'hint anchor missing {path}')
        pos += len('        android:layout_height="wrap_content"\n')
        text=text[:pos]+'        android:hint="@string/filter_label"\n'+text[pos:]
        p.write_text(text); print('patched',path)

replace_once('app/src/main/res/layout/dialog_filter.xml', '''        android:hint="@string/main_search_hint"\n        android:inputType="text"''', '''        android:hint="@string/main_search_hint"\n        android:inputType="text"\n        android:importantForAutofill="no"''', 'android:importantForAutofill="no"')

p=Path('app/src/main/res/layout/activity_statistics.xml'); text=p.read_text()
if 'android:contentDescription="@string/action_previous"' not in text:
    for drawable in ['ic_battery_chart','ic_cpu','ic_ram']:
        text=text.replace(f'android:src="@drawable/{drawable}"\n', f'android:src="@drawable/{drawable}"\n                        android:contentDescription="@null"\n',1)
    text=text.replace('android:id="@+id/btn_chart_prev"\n', 'android:id="@+id/btn_chart_prev"\n                            android:contentDescription="@string/action_previous"\n',1)
    text=text.replace('android:id="@+id/btn_chart_next"\n', 'android:id="@+id/btn_chart_next"\n                            android:contentDescription="@string/action_next"\n',1)
    p.write_text(text); print('patched statistics accessibility')

for path in ['app/src/main/res/layout/item.xml','app/src/main/res/layout/item_trigger.xml']:
    p=Path(path); text=p.read_text()
    if 'android:textSize="10sp"' in text:
        p.write_text(text.replace('android:textSize="10sp"','android:textSize="11sp"')); print('patched',path)
