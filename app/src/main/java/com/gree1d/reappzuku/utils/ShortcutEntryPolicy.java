package com.gree1d.reappzuku.utils;

final class ShortcutEntryPolicy {
    enum Decision {
        DIRECT_RAM_KILL,
        CONFIRM_RAM_KILL,
        CONFIRM_FOREGROUND_KILL,
        REJECT
    }

    private ShortcutEntryPolicy() {
    }

    static Decision decide(String action, boolean secureActionAuthorized) {
        if (ShortcutAuth.ACTION_RAM_KILL_SECURE.equals(action)) {
            return secureActionAuthorized ? Decision.DIRECT_RAM_KILL : Decision.REJECT;
        }
        if ("WIDGET_KILL".equals(action)) {
            return Decision.CONFIRM_RAM_KILL;
        }
        return Decision.CONFIRM_FOREGROUND_KILL;
    }
}
