package com.gree1d.reappzuku.core.shell;

import android.os.Parcel;
import android.os.Parcelable;

public class ProcessMemoryInfo implements Parcelable {

    public final int pid;
    public final long totalPssKb;

    public ProcessMemoryInfo(int pid, long totalPssKb) {
        this.pid = pid;
        this.totalPssKb = totalPssKb;
    }

    protected ProcessMemoryInfo(Parcel in) {
        pid = in.readInt();
        totalPssKb = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(pid);
        dest.writeLong(totalPssKb);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ProcessMemoryInfo> CREATOR = new Creator<ProcessMemoryInfo>() {
        @Override
        public ProcessMemoryInfo createFromParcel(Parcel in) {
            return new ProcessMemoryInfo(in);
        }

        @Override
        public ProcessMemoryInfo[] newArray(int size) {
            return new ProcessMemoryInfo[size];
        }
    };
}
