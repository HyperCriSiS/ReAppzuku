package com.gree1d.reappzuku.ui;

import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.manager.BackgroundAppManager;

public class LogAppOptionsBottomSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onShowKillDetail(String appName, String packageName, long windowMs);
        void onRestrictionTypeChanged(String packageName, BackgroundAppManager.RestrictionType type);
    }

    private static final String ARG_APP_NAME    = "app_name";
    private static final String ARG_PACKAGE     = "package";
    private static final String ARG_WINDOW_MS   = "window_ms";
    private static final String ARG_BG_SUPPORTED = "bg_supported";
    private static final String ARG_ACCENT_COLOR = "accent_color";

    private Listener listener;
    private BackgroundAppManager appManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService iconExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    public static LogAppOptionsBottomSheet newInstance(String appName, String packageName,
                                                         long windowMs, boolean bgRestrictionSupported,
                                                         int accentColor) {
        LogAppOptionsBottomSheet sheet = new LogAppOptionsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_APP_NAME, appName);
        args.putString(ARG_PACKAGE, packageName);
        args.putLong(ARG_WINDOW_MS, windowMs);
        args.putBoolean(ARG_BG_SUPPORTED, bgRestrictionSupported);
        args.putInt(ARG_ACCENT_COLOR, accentColor);
        sheet.setArguments(args);
        return sheet;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setAppManager(BackgroundAppManager appManager) {
        this.appManager = appManager;
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
        return inflater.inflate(R.layout.fragment_log_app_options_bottom_sheet, container, false);
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
        String appName     = args.getString(ARG_APP_NAME, "");
        String pkg          = args.getString(ARG_PACKAGE, "");
        long windowMs        = args.getLong(ARG_WINDOW_MS, -1L);
        boolean bgSupported = args.getBoolean(ARG_BG_SUPPORTED, false);
        int accentColor      = args.getInt(ARG_ACCENT_COLOR);



        TextView nameView = view.findViewById(R.id.log_sheet_app_name);
        TextView pkgView  = view.findViewById(R.id.log_sheet_package_name);
        ImageView iconView = view.findViewById(R.id.log_sheet_app_icon);
        nameView.setText(appName);
        pkgView.setText(pkg);
        loadAppIcon(pkg, iconView);

        TextView btnKillDetail = view.findViewById(R.id.log_sheet_btn_kill_detail);
        btnKillDetail.setOnClickListener(v -> {

            dismiss();
            if (listener != null) listener.onShowKillDetail(appName, pkg, windowMs);
        });

        LinearLayout bgHeader    = view.findViewById(R.id.log_sheet_bg_restriction_header);
        LinearLayout bgContainer = view.findViewById(R.id.log_sheet_bg_restriction_container);
        ImageView bgArrow        = view.findViewById(R.id.log_sheet_bg_restriction_arrow);

        LinearLayout itemSoft   = view.findViewById(R.id.log_sheet_item_soft);
        LinearLayout itemMedium = view.findViewById(R.id.log_sheet_item_medium);
        LinearLayout itemHard   = view.findViewById(R.id.log_sheet_item_hard);

        CheckBox checkSoft   = view.findViewById(R.id.log_sheet_check_soft);
        CheckBox checkMedium = view.findViewById(R.id.log_sheet_check_medium);
        CheckBox checkHard   = view.findViewById(R.id.log_sheet_check_hard);

        if (accentColor != 0) {
            ColorStateList accentTint = buildCheckboxTint(accentColor);
            checkSoft.setButtonTintList(accentTint);
            checkMedium.setButtonTintList(accentTint);
            checkHard.setButtonTintList(accentTint);
        }

        if (bgSupported && appManager != null) {
            bgHeader.setVisibility(View.VISIBLE);

            if (accentColor != 0) {
                TextView bgTitle = view.findViewById(R.id.log_sheet_bg_restriction_title);
                bgTitle.setTextColor(accentColor);
                bgArrow.setImageTintList(ColorStateList.valueOf(accentColor));
            }

            boolean isRestricted = appManager.getBackgroundRestrictedApps().contains(pkg);
            BackgroundAppManager.RestrictionType current = appManager.getRestrictionType(pkg);
            checkSoft.setChecked(isRestricted && current == BackgroundAppManager.RestrictionType.SOFT);
            checkMedium.setChecked(isRestricted && current == BackgroundAppManager.RestrictionType.MEDIUM);
            checkHard.setChecked(isRestricted && current == BackgroundAppManager.RestrictionType.HARD);

            bgHeader.setOnClickListener(v -> {
                boolean expanded = bgContainer.getVisibility() == View.VISIBLE;
                bgContainer.setVisibility(expanded ? View.GONE : View.VISIBLE);
                bgArrow.setRotation(expanded ? 0f : 180f);
            });

            itemSoft.setOnClickListener(v -> selectRestrictionType(pkg,
                    BackgroundAppManager.RestrictionType.SOFT,
                    checkSoft, checkMedium, checkHard));
            itemMedium.setOnClickListener(v -> selectRestrictionType(pkg,
                    BackgroundAppManager.RestrictionType.MEDIUM,
                    checkSoft, checkMedium, checkHard));
            itemHard.setOnClickListener(v -> selectRestrictionType(pkg,
                    BackgroundAppManager.RestrictionType.HARD,
                    checkSoft, checkMedium, checkHard));
        } else {
            bgHeader.setVisibility(View.GONE);
        }
    }

    private void loadAppIcon(String packageName, ImageView iconView) {
        if (packageName == null || !packageName.contains(".")) {
            iconView.setImageDrawable(null);
            return;
        }
        iconExecutor.execute(() -> {
            Drawable icon = null;
            try {
                PackageManager pm = requireContext().getPackageManager();
                icon = pm.getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException e) {

            }
            Drawable finalIcon = icon;
            handler.post(() -> {
                if (!isAdded()) return;
                iconView.setImageDrawable(finalIcon);
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        iconExecutor.shutdownNow();
    }

    private void selectRestrictionType(String pkg, BackgroundAppManager.RestrictionType type,
                                        CheckBox checkSoft, CheckBox checkMedium,
                                        CheckBox checkHard) {
        checkSoft.setChecked(type == BackgroundAppManager.RestrictionType.SOFT);
        checkMedium.setChecked(type == BackgroundAppManager.RestrictionType.MEDIUM);
        checkHard.setChecked(type == BackgroundAppManager.RestrictionType.HARD);



        if (listener != null) listener.onRestrictionTypeChanged(pkg, type);
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
