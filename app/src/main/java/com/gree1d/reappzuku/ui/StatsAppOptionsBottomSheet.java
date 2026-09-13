package com.gree1d.reappzuku.ui;

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

public class StatsAppOptionsBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "StatsAppOptionsBottomSheet";

    public interface Listener {
        void onToggleWhitelist(boolean nowChecked);
        void onToggleBlacklist(boolean nowChecked);
        void onToggleBackgroundRestriction(boolean nowChecked);
    }

    private static final String ARG_APP_NAME               = "app_name";
    private static final String ARG_PACKAGE                = "package";
    private static final String ARG_IN_WHITELIST           = "in_whitelist";
    private static final String ARG_IN_BLACKLIST           = "in_blacklist";
    private static final String ARG_BG_RESTRICTION         = "bg_restriction";
    private static final String ARG_BG_RESTRICT_SUPPORTED  = "bg_restrict_supported";
    private static final String ARG_BG_RESTRICT_LABEL      = "bg_restrict_label";
    private static final String ARG_ACCENT_COLOR           = "accent_color";

    private Listener listener;
    private android.graphics.drawable.Drawable appIcon;

    public static StatsAppOptionsBottomSheet newInstance(
            AppModel app,
            boolean inWhitelist,
            boolean inBlacklist,
            boolean supportsBackgroundRestriction,
            String backgroundRestrictionLabel,
            int accentColor) {

        if (app.isProtected()) {
            throw new IllegalArgumentException(
                    "StatsAppOptionsBottomSheet must not be opened for a protected app: " + app.getPackageName());
        }

        StatsAppOptionsBottomSheet sheet = new StatsAppOptionsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_APP_NAME, app.getAppName());
        args.putString(ARG_PACKAGE, app.getPackageName());
        args.putBoolean(ARG_IN_WHITELIST, inWhitelist);
        args.putBoolean(ARG_IN_BLACKLIST, inBlacklist);
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
        return inflater.inflate(R.layout.fragment_stats_options_bottom_sheet, container, false);
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
        String appName        = args.getString(ARG_APP_NAME, "");
        String pkg             = args.getString(ARG_PACKAGE, "");
        boolean inWhitelist     = args.getBoolean(ARG_IN_WHITELIST, false);
        boolean inBlacklist     = args.getBoolean(ARG_IN_BLACKLIST, false);
        boolean bgRestriction  = args.getBoolean(ARG_BG_RESTRICTION, false);
        boolean bgSupported    = args.getBoolean(ARG_BG_RESTRICT_SUPPORTED, false);
        String bgLabel          = args.getString(ARG_BG_RESTRICT_LABEL, "");
        int accentColor         = args.getInt(ARG_ACCENT_COLOR);



        ColorStateList accentTint = buildCheckboxTint(accentColor);

        ImageView iconView = view.findViewById(R.id.sheet_app_icon);
        TextView nameView  = view.findViewById(R.id.sheet_app_name);
        TextView pkgView   = view.findViewById(R.id.sheet_package_name);

        if (appIcon != null) iconView.setImageDrawable(appIcon);
        nameView.setText(appName);
        pkgView.setText(pkg);

        LinearLayout itemWhitelist  = view.findViewById(R.id.sheet_item_whitelist);
        LinearLayout itemBlacklist  = view.findViewById(R.id.sheet_item_blacklist);
        LinearLayout itemBgRestrict = view.findViewById(R.id.sheet_item_bg_restriction);
        TextView bgRestrictLabel    = view.findViewById(R.id.sheet_bg_restriction_label);

        CheckBox checkWhitelist  = view.findViewById(R.id.sheet_check_whitelist);
        CheckBox checkBlacklist  = view.findViewById(R.id.sheet_check_blacklist);
        CheckBox checkBgRestrict = view.findViewById(R.id.sheet_check_bg_restriction);

        checkWhitelist.setButtonTintList(accentTint);
        checkBlacklist.setButtonTintList(accentTint);
        checkBgRestrict.setButtonTintList(accentTint);

        for (View row : new View[] { itemWhitelist, itemBlacklist, itemBgRestrict }) {
            FocusHighlightUtil.apply(row);
        }

        checkWhitelist.setChecked(inWhitelist);
        checkBlacklist.setChecked(inBlacklist);
        checkBgRestrict.setChecked(bgRestriction);

        updateMutualExclusion(checkWhitelist, itemWhitelist, checkBlacklist, itemBlacklist);

        if (bgSupported) {
            itemBgRestrict.setVisibility(View.VISIBLE);
            bgRestrictLabel.setText(bgLabel);
        }

        itemWhitelist.setOnClickListener(v -> {
            boolean next = !checkWhitelist.isChecked();
            checkWhitelist.setChecked(next);


            updateMutualExclusion(checkWhitelist, itemWhitelist, checkBlacklist, itemBlacklist);

            if (listener != null) listener.onToggleWhitelist(next);
        });

        itemBlacklist.setOnClickListener(v -> {
            boolean next = !checkBlacklist.isChecked();
            checkBlacklist.setChecked(next);


            updateMutualExclusion(checkWhitelist, itemWhitelist, checkBlacklist, itemBlacklist);

            if (listener != null) listener.onToggleBlacklist(next);
        });

        itemBgRestrict.setOnClickListener(v -> {
            boolean next = !checkBgRestrict.isChecked();
            checkBgRestrict.setChecked(next);

            if (listener != null) listener.onToggleBackgroundRestriction(next);
        });
    }

    private void updateMutualExclusion(CheckBox checkWhitelist, LinearLayout itemWhitelist,
                                       CheckBox checkBlacklist, LinearLayout itemBlacklist) {
        boolean isWhitelistChecked = checkWhitelist.isChecked();
        boolean isBlacklistChecked = checkBlacklist.isChecked();

        itemBlacklist.setEnabled(!isWhitelistChecked);
        checkBlacklist.setEnabled(!isWhitelistChecked);
        itemBlacklist.setAlpha(isWhitelistChecked ? 0.4f : 1.0f);

        itemWhitelist.setEnabled(!isBlacklistChecked);
        checkWhitelist.setEnabled(!isBlacklistChecked);
        itemWhitelist.setAlpha(isBlacklistChecked ? 0.4f : 1.0f);
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
