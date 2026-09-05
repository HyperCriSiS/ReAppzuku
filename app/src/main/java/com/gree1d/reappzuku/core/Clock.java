package com.gree1d.reappzuku.core;

import java.util.Calendar;

/** Time source used by scheduling policy so time-dependent logic can be deterministic in tests. */
public interface Clock {
    Clock SYSTEM = new Clock() {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public Calendar calendarNow() {
            return Calendar.getInstance();
        }
    };

    long currentTimeMillis();

    Calendar calendarNow();
}
