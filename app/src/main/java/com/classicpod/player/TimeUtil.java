package com.classicpod.player;

public final class TimeUtil {
    private TimeUtil() {
    }

    public static String format(int ms) {
        if (ms < 0) {
            ms = 0;
        }
        int totalSec = ms / 1000;
        int m = totalSec / 60;
        int s = totalSec % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }
}
