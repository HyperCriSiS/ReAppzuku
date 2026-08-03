package com.gree1d.reappzuku.core.shell;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Parcelable result of a single shell command execution, sent back across the
 * AIDL boundary from the Shizuku UserService to the app process. Mirrors the
 * shape of ShellManager.ShellResult so converting between the two at the
 * ShellManager boundary is a straight field copy.
 */
public class ShellExecResult implements Parcelable {

    public final boolean succeeded;
    public final int exitCode;
    public final String output;

    public ShellExecResult(boolean succeeded, int exitCode, String output) {
        this.succeeded = succeeded;
        this.exitCode = exitCode;
        this.output = output == null ? "" : output;
    }

    protected ShellExecResult(Parcel in) {
        succeeded = in.readByte() != 0;
        exitCode = in.readInt();
        output = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (succeeded ? 1 : 0));
        dest.writeInt(exitCode);
        dest.writeString(output);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ShellExecResult> CREATOR = new Creator<ShellExecResult>() {
        @Override
        public ShellExecResult createFromParcel(Parcel in) {
            return new ShellExecResult(in);
        }

        @Override
        public ShellExecResult[] newArray(int size) {
            return new ShellExecResult[size];
        }
    };
}
