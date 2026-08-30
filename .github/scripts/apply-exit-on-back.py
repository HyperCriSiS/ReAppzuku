from pathlib import Path


def replace_once(path: str, old: str, new: str):
    p = Path(path)
    text = p.read_text()
    if new in text:
        return False
    if old not in text:
        raise RuntimeError(f"Expected text not found in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))
    return True

changed = False

# Shared preference key.
changed |= replace_once(
    "app/src/main/java/com/gree1d/reappzuku/core/PreferenceKeys.java",
    '    public static final String KEY_REPLACEMENT_NOTICE_SHOWN_VERSION = "replacement_notice_shown_version";\n',
    '    public static final String KEY_REPLACEMENT_NOTICE_SHOWN_VERSION = "replacement_notice_shown_version";\n'
    '    public static final String KEY_EXIT_ON_BACK = "exit_on_back";\n',
)

# Main screen Back handling. Use AndroidX dispatcher so predictive/system Back keeps working.
main = Path("app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java")
text = main.read_text()
if "setupBackButtonBehavior() {" not in text:
    text = text.replace(
        "import androidx.appcompat.app.AppCompatActivity;\n",
        "import androidx.appcompat.app.AppCompatActivity;\nimport androidx.activity.OnBackPressedCallback;\n",
        1,
    )
    text = text.replace(
        '        super.onCreate(savedInstanceState);\n        AppDebugManager.d(Category.MAIN_PAGE, "MainActivity: onCreate started");\n',
        '        super.onCreate(savedInstanceState);\n        setupBackButtonBehavior();\n        AppDebugManager.d(Category.MAIN_PAGE, "MainActivity: onCreate started");\n',
        1,
    )
    marker = "    @Override\n    protected void onDestroy() {\n"
    method = '''    private void setupBackButtonBehavior() {\n        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {\n            @Override\n            public void handleOnBackPressed() {\n                if (sharedPreferences.getBoolean(KEY_EXIT_ON_BACK, false)) {\n                    // Explicit user-requested exit from the main screen. Remove the\n                    // task from Recents while leaving Android in control of process\n                    // reclamation and any separately requested background features.\n                    finishAndRemoveTask();\n                    return;\n                }\n\n                // Preserve Android's normal Back behavior when the option is disabled.\n                setEnabled(false);\n                getOnBackPressedDispatcher().onBackPressed();\n                setEnabled(true);\n            }\n        });\n    }\n\n'''
    if marker not in text:
        raise RuntimeError("MainActivity onDestroy marker not found")
    text = text.replace(marker, method + marker, 1)
    main.write_text(text)
    changed = True

# Settings state/listeners + accent support.
settings = Path("app/src/main/java/com/gree1d/reappzuku/ui/SettingsActivity.java")
text = settings.read_text()
if "switchExitOnBack" not in text:
    text = text.replace(
        "            R.id.section_title_appearance,\n            R.id.section_title_stability,\n",
        "            R.id.section_title_appearance,\n            R.id.section_title_behavior,\n            R.id.section_title_stability,\n",
        1,
    )
    text = text.replace(
        "            R.id.switch_sleep_mode\n",
        "            R.id.switch_sleep_mode,\n            R.id.switch_exit_on_back\n",
        1,
    )
    text = text.replace(
        "        int notificationMode = sharedPreferences.getInt(KEY_NOTIFICATION_MODE, NOTIFICATION_MODE_ALL);\n"
        "        updateNotificationModeText(notificationMode);\n\n",
        "        int notificationMode = sharedPreferences.getInt(KEY_NOTIFICATION_MODE, NOTIFICATION_MODE_ALL);\n"
        "        updateNotificationModeText(notificationMode);\n\n"
        "        binding.switchExitOnBack.setChecked(sharedPreferences.getBoolean(KEY_EXIT_ON_BACK, false));\n\n",
        1,
    )
    text = text.replace(
        "        binding.layoutNotificationMode.setOnClickListener(v -> showNotificationModeDialog());\n\n",
        "        binding.layoutNotificationMode.setOnClickListener(v -> showNotificationModeDialog());\n\n"
        "        binding.switchExitOnBack.setOnCheckedChangeListener((buttonView, isChecked) ->\n"
        "                sharedPreferences.edit().putBoolean(KEY_EXIT_ON_BACK, isChecked).apply());\n"
        "        binding.layoutExitOnBack.setOnClickListener(v -> binding.switchExitOnBack.toggle());\n\n",
        1,
    )
    settings.write_text(text)
    changed = True

# Dedicated App behavior section directly after Appearance.
layout = Path("app/src/main/res/layout/activity_settings.xml")
text = layout.read_text()
if 'android:id="@+id/switch_exit_on_back"' not in text:
    needle = '''                    <LinearLayout\n                        android:id="@+id/layout_notification_mode"'''
    if needle not in text:
        raise RuntimeError("Notification settings block not found")
    transition = '''                </LinearLayout>\n            </com.google.android.material.card.MaterialCardView>\n\n            <com.google.android.material.card.MaterialCardView\n                style="@style/Widget.Material3.CardView.Outlined"'''
    pos = text.index(needle)
    end = text.index(transition, pos)
    insert_pos = end + len('''                </LinearLayout>\n            </com.google.android.material.card.MaterialCardView>\n''')
    behavior = '''\n            <com.google.android.material.card.MaterialCardView\n                style="@style/Widget.Material3.CardView.Outlined"\n                android:layout_width="match_parent"\n                android:layout_height="wrap_content"\n                android:layout_marginBottom="12dp"\n                app:cardCornerRadius="16dp">\n\n                <LinearLayout\n                    android:layout_width="match_parent"\n                    android:layout_height="wrap_content"\n                    android:orientation="vertical"\n                    android:padding="8dp">\n\n                    <LinearLayout\n                        android:layout_width="match_parent"\n                        android:layout_height="wrap_content"\n                        android:orientation="horizontal"\n                        android:gravity="center_vertical"\n                        android:paddingHorizontal="8dp"\n                        android:paddingTop="8dp"\n                        android:paddingBottom="4dp">\n\n                        <ImageView\n                            android:id="@+id/section_icon_behavior"\n                            android:layout_width="24dp"\n                            android:layout_height="24dp"\n                            android:layout_marginEnd="8dp"\n                            android:src="@drawable/ic_settings"\n                            android:contentDescription="@null" />\n\n                        <TextView\n                            android:id="@+id/section_title_behavior"\n                            android:layout_width="wrap_content"\n                            android:layout_height="wrap_content"\n                            android:text="@string/settings_section_behavior"\n                            android:textColor="?attr/colorSecondary"\n                            android:textSize="14sp"\n                            android:textStyle="bold" />\n                    </LinearLayout>\n\n                    <LinearLayout\n                        android:id="@+id/layout_exit_on_back"\n                        android:layout_width="match_parent"\n                        android:layout_height="wrap_content"\n                        android:orientation="horizontal"\n                        android:gravity="center_vertical"\n                        android:paddingHorizontal="8dp"\n                        android:paddingVertical="12dp"\n                        android:background="?attr/selectableItemBackground">\n\n                        <LinearLayout\n                            android:layout_width="0dp"\n                            android:layout_height="wrap_content"\n                            android:layout_weight="1"\n                            android:orientation="vertical">\n\n                            <TextView\n                                android:layout_width="wrap_content"\n                                android:layout_height="wrap_content"\n                                android:text="@string/settings_exit_on_back_title"\n                                android:textColor="@color/text_primary"\n                                android:textSize="16sp" />\n\n                            <TextView\n                                android:layout_width="wrap_content"\n                                android:layout_height="wrap_content"\n                                android:layout_marginTop="2dp"\n                                android:text="@string/settings_exit_on_back_subtitle"\n                                android:textColor="@color/text_secondary"\n                                android:textSize="12sp" />\n                        </LinearLayout>\n\n                        <com.google.android.material.materialswitch.MaterialSwitch\n                            android:id="@+id/switch_exit_on_back"\n                            android:layout_width="wrap_content"\n                            android:layout_height="wrap_content"\n                            android:clickable="true"\n                            android:focusable="true" />\n                    </LinearLayout>\n\n                </LinearLayout>\n            </com.google.android.material.card.MaterialCardView>\n'''
    text = text[:insert_pos] + behavior + text[insert_pos:]
    layout.write_text(text)
    changed = True

# Default locale strings; all existing translations safely fall back to these.
strings = Path("app/src/main/res/values/strings.xml")
text = strings.read_text()
if 'name="settings_exit_on_back_title"' not in text:
    block = '''\n    <!-- App behavior -->\n    <string name="settings_section_behavior">App behavior</string>\n    <string name="settings_exit_on_back_title">Exit app with Back button</string>\n    <string name="settings_exit_on_back_subtitle">On the main screen, Back closes ReAppzuku and removes it from Recents</string>\n'''
    if "\n</resources>" not in text:
        raise RuntimeError("strings.xml closing tag not found")
    strings.write_text(text.replace("\n</resources>", block + "\n</resources>", 1))
    changed = True

print("exit-on-back patch applied" if changed else "exit-on-back patch already present")
