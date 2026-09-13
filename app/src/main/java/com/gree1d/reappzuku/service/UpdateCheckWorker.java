package com.gree1d.reappzuku.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.gree1d.reappzuku.manager.UpdateChecker;

public class UpdateCheckWorker extends Worker {


    public UpdateCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();



        UpdateChecker.ReleaseInfo info = UpdateChecker.fetchLatestRelease();

        if (info == null) {

            return Result.retry();
        }

        String currentVersion = UpdateChecker.getAppVersion(context);

        if (UpdateChecker.isNewer(info.tagName, currentVersion)) {

            UpdateChecker.postUpdateNotification(context, info);
        } else {

        }

        return Result.success();
    }
}
