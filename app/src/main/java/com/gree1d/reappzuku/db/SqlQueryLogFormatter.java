package com.gree1d.reappzuku.db;

import java.util.List;

/**
 * Formats opt-in SQL debug logs without exposing potentially sensitive bind values.
 */
final class SqlQueryLogFormatter {
    private SqlQueryLogFormatter() {
    }

    static String format(String sqlQuery, List<?> bindArgs) {
        int bindArgCount = bindArgs == null ? 0 : bindArgs.size();
        return "AppDatabase query: " + String.valueOf(sqlQuery)
                + " bindArgCount=" + bindArgCount
                + " values=<redacted>";
    }
}
