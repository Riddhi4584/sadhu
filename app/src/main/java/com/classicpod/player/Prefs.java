package com.classicpod.player;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static final String FILE = "classicpod";
    private static final String KEY_SHUFFLE = "shuffle";
    private static final String KEY_REPEAT = "repeat";
    private static final String KEY_SONG = "last_song";
    private static final String KEY_POS = "last_pos";
    private static final String KEY_QUEUE = "last_queue";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean isShuffle() {
        return sp.getBoolean(KEY_SHUFFLE, false);
    }

    public void setShuffle(boolean value) {
        sp.edit().putBoolean(KEY_SHUFFLE, value).apply();
    }

    public int getRepeat() {
        return sp.getInt(KEY_REPEAT, REPEAT_OFF);
    }

    public void setRepeat(int value) {
        sp.edit().putInt(KEY_REPEAT, value).apply();
    }

    public int nextRepeat() {
        int n = (getRepeat() + 1) % 3;
        setRepeat(n);
        return n;
    }

    public void savePlayback(long songId, int positionMs, String queueCsv) {
        sp.edit()
                .putLong(KEY_SONG, songId)
                .putInt(KEY_POS, positionMs)
                .putString(KEY_QUEUE, queueCsv == null ? "" : queueCsv)
                .apply();
    }

    public long lastSongId() {
        return sp.getLong(KEY_SONG, -1L);
    }

    public int lastPosition() {
        return sp.getInt(KEY_POS, 0);
    }

    public String lastQueueCsv() {
        return sp.getString(KEY_QUEUE, "");
    }
}
