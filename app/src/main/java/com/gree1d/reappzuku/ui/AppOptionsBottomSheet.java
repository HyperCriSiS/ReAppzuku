package com.gree1d.reappzuku.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import com.gree1d.reappzuku.utils.AppModel;
import com.gree1d.reappzuku.utils.FocusHighlightUtil;
import com.gree1d.reappzuku.R;

public class AppOptionsBottomSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onAppInfo();
        void onAppTriggers();
        void onUninstall();
        void onToggleHiddenSingle();
        void onStrategyChosen(int strategy);
        void onToggleHidden(boolean nowChecked);
        void onToggleBackgroundRestriction(boolean nowChecked);
    }

    private static final String ARG_APP_NAME             = "app_name";
    private static final String ARG_PACKAGE              = "package";
    private static final String ARG_IS_PROTECTED         = "is_protected";
    private static final String ARG_IS_SYSTEM            = "is_system";
    private static final String ARG_STRATEGY             = "strategy";
    private static final String ARG_IN_HIDDEN            = "in_hidden";
    private static final String ARG_BG_RESTRICTION       = "bg_restriction";
    private static final String ARG_BG_RESTRICT_SUPPORTED = "bg_restrict_supported";
    private static final String ARG_BG_RESTRICT_LABEL    = "bg_restrict_label";
    private static final String ARG_ACCENT_COLOR         = "accent_color";

    private Listener listener;
    private android.graphics.drawable.Drawable appIcon;

    public static AppOptionsBottomSheet newInstance(
            AppModel app,
            int strategy,
            boolean inHidden,
            boolean supportsBackgroundRestriction,
            String backgroundRestrictionLabel,
            int accentColor) {

        AppOptionsBottomSheet sheet = new AppOptionsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_APP_NAME, app.getAppName());
        args.putString(ARG_PACKAGE, app.getPackageName());
        args.putBoolean(ARG_IS_PROTECTED, app.isProtected());
        args.putBoolean(ARG_IS_SYSTEM, app.isSystemApp());
        args.putInt(ARG_STRATEGY, strategy);
        args.putBoolean(ARG_IN_HIDDEN, inHidden);
        args.putBoolean(ARG_BG_RESTRICTION, app.isBackgroundRestrictionDesired());
        args.putBoolean(ARG_BG_RESTRICT_SUPPORTED, supportsBackgroundRestriction);
        args.putString(ARG_BG_RESTRICT_LABEL, backgroundRestrictionLabel);
        args.putInt(ARG_ACCENT_COLOR, accentColor);
        sheet.setArguments(args);
        return sheet;
    }

    public void setAppIcon(android.graphics.drawable.Drawable icon) {
        this.appIcon = icon;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Recreated by the system after rotation or process death: the listener and
        // the app it acted on are gone, so the sheet closes instead of doing nothing.
        if (savedInstanceState != null) {
            dismissAllowingStateLoss();
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }


    @Override
    public int getTheme() {
        return R.style.AppBottomSheetDialogTheme;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_app_options_bottom_sheet, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (isWideScreenOrientation()) {
            expandFullyForWideScreen();
        }
    }

    private boolean isWideScreenOrientation() {
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private void expandFullyForWideScreen() {
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog == null) return;

        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) return;

        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        bottomSheet.setLayoutParams(params);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        String appName       = args.getString(ARG_APP_NAME, "");
        String pkg           = args.getString(ARG_PACKAGE, "");
        boolean isProtected  = args.getBoolean(ARG_IS_PROTECTED, false);
        boolean isSystem     = args.getBoolean(ARG_IS_SYSTEM, false);
        int strategy         = args.getInt(ARG_STRATEGY, 0);
        boolean inHidden     = args.getBoolean(ARG_IN_HIDDEN, false);
        boolean bgRestriction = args.getBoolean(ARG_BG_RESTRICTION, false);
        boolean bgSupported  = args.getBoolean(ARG_BG_RESTRICT_SUPPORTED, false);
        String bgLabel       = args.getString(ARG_BG_RESTRICT_LABEL, "");
        int accentColor      = args.getInt(ARG_ACCENT_COLOR);



        ColorStateList accentTint = buildCheckboxTint(accentColor);

        ImageView iconView = view.findViewById(R.id.sheet_app_icon);
        TextView nameView  = view.findViewById(R.id.sheet_app_name);
        TextView pkgView   = view.findViewById(R.id.sheet_package_name);

        if (appIcon != null) iconView.setImageDrawable(appIcon);
        nameView.setText(appName);
        pkgView.setText(pkg);

        TextView btnInfo        = view.findViewById(R.id.sheet_btn_app_info);
        TextView btnTriggers    = view.findViewById(R.id.sheet_btn_app_triggers);
        TextView btnPolicy      = view.findViewById(R.id.sheet_btn_policy);
        TextView btnUninstall   = view.findViewById(R.id.sheet_btn_uninstall);
        TextView btnHiddenSingle = view.findViewById(R.id.sheet_btn_hidden_single);

        View dividerAddTo           = view.findViewById(R.id.sheet_divider_add_to);
        LinearLayout addToHeader    = view.findViewById(R.id.sheet_add_to_header);
        LinearLayout addToContainer = view.findViewById(R.id.sheet_add_to_container);
        ImageView addToArrow        = view.findViewById(R.id.sheet_add_to_arrow);
        TextView addToTitle         = view.findViewById(R.id.sheet_add_to_title);

        LinearLayout itemStrategy   = view.findViewById(R.id.sheet_item_strategy);
        LinearLayout itemHidden     = view.findViewById(R.id.sheet_item_hidden);
        LinearLayout itemBgRestrict = view.findViewById(R.id.sheet_item_bg_restriction);
        TextView bgRestrictLabel    = view.findViewById(R.id.sheet_bg_restriction_label);

        CheckBox checkHidden     = view.findViewById(R.id.sheet_check_hidden);
        CheckBox checkBgRestrict = view.findViewById(R.id.sheet_check_bg_restriction);

        checkHidden.setButtonTintList(accentTint);
        checkBgRestrict.setButtonTintList(accentTint);

        for (View row : new View[] {
                btnInfo, btnTriggers, btnPolicy, btnUninstall, btnHiddenSingle,
                addToHeader, itemStrategy, itemHidden, itemBgRestrict }) {
            FocusHighlightUtil.apply(row);
        }

        btnInfo.post(btnInfo::requestFocus);

        btnInfo.setOnClickListener(v -> {

            dismiss();
            if (listener != null) listener.onAppInfo();
        });

        btnTriggers.setOnClickListener(v -> {

            dismiss();
            if (listener != null) listener.onAppTriggers();
        });

        btnPolicy.setOnClickListener(v -> {
            dismiss();
            Intent intent = new Intent(requireContext(), AppPolicyEditorActivity.class);
            intent.putExtra(AppPolicyEditorActivity.EXTRA_PACKAGE_NAME, pkg);
            startActivity(intent);
        });

        if (isProtected) {
            btnHiddenSingle.setVisibility(View.VISIBLE);
            btnHiddenSingle.setText(getString(R.string.menu_hidden));
            btnHiddenSingle.setOnClickListener(v -> {

                dismiss();
                if (listener != null) listener.onToggleHiddenSingle();
            });
        } else {
            dividerAddTo.setVisibility(View.VISIBLE);
            addToHeader.setVisibility(View.VISIBLE);
            addToTitle.setTextColor(accentColor);
            addToArrow.setImageTintList(ColorStateList.valueOf(accentColor));
            // Open from the start: the strategy is the main choice of this sheet and
            // was not found behind a collapsed header.
            addToContainer.setVisibility(View.VISIBLE);
            addToArrow.setRotation(180f);

            if (!isSystem) {
                btnUninstall.setVisibility(View.VISIBLE);
                btnUninstall.setOnClickListener(v -> {

                    dismiss();
                    if (listener != null) listener.onUninstall();
                });
            }

            checkHidden.setChecked(inHidden);
            checkBgRestrict.setChecked(bgRestriction);

            PolicyStrategyRow.bind(itemStrategy, view.findViewById(R.id.sheet_strategy_value),
                    strategy, chosen -> {
                        if (listener != null) listener.onStrategyChosen(chosen);
                    });

            if (bgSupported) {
                itemBgRestrict.setVisibility(View.VISIBLE);
                bgRestrictLabel.setText(bgLabel);
            }

            addToHeader.setOnClickListener(v -> {
                boolean expanded = addToContainer.getVisibility() == View.VISIBLE;
                addToContainer.setVisibility(expanded ? View.GONE : View.VISIBLE);
                addToArrow.setRotation(expanded ? 0f : 180f);
            });

            itemHidden.setOnClickListener(v -> {
                boolean next = !checkHidden.isChecked();
                checkHidden.setChecked(next);

                if (listener != null) listener.onToggleHidden(next);
            });

            itemBgRestrict.setOnClickListener(v -> {
                boolean next = !checkBgRestrict.isChecked();
                checkBgRestrict.setChecked(next);

                if (listener != null) listener.onToggleBackgroundRestriction(next);
            });
        }
    }

    private ColorStateList buildCheckboxTint(int color) {
        int[][] states = new int[][] {
            new int[] { android.R.attr.state_checked },
            new int[] { -android.R.attr.state_checked }
        };
        int[] colors = new int[] { color, color };
        return new ColorStateList(states, colors);
    }
}
