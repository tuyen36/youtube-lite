package com.example.youtubelite;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

/**
 * Cai dat app (2.9): hen gio tat + do phan giai mac dinh.
 * Luu ben vao SharedPreferences, API 21 OK.
 */
public final class AppSettings {
    private static final String PREF = "youtube_lite_settings";
    private static final String KEY_DEFAULT_HEIGHT = "default_height"; // 360/480/720/1080
    private static final String KEY_SLEEP_MINUTES = "sleep_minutes";   // 0 = tat

    private AppSettings() {}

    public static final int[] HEIGHT_OPTIONS = {360, 480, 720, 1080};
    public static final int[] SLEEP_OPTIONS = {0, 30, 60, 120};

    public static synchronized int getDefaultHeight(Context ctx) {
        try {
            int h = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .getInt(KEY_DEFAULT_HEIGHT, 360);
            for (int o : HEIGHT_OPTIONS) {
                if (o == h) return h;
            }
        } catch (Exception ignored) {
        }
        return 360;
    }

    public static synchronized void setDefaultHeight(Context ctx, int height) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_DEFAULT_HEIGHT, height).apply();
        } catch (Exception ignored) {
        }
    }

    public static synchronized int getSleepMinutes(Context ctx) {
        try {
            return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .getInt(KEY_SLEEP_MINUTES, 0);
        } catch (Exception ignored) {
        }
        return 0;
    }

    public static synchronized void setSleepMinutes(Context ctx, int minutes) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_SLEEP_MINUTES, minutes).apply();
        } catch (Exception ignored) {
        }
    }

    public static String sleepLabel(int minutes) {
        if (minutes <= 0) return "Tắt";
        if (minutes == 30) return "30 phút";
        if (minutes == 60) return "1 giờ";
        if (minutes == 120) return "2 giờ";
        return minutes + " phút";
    }

    /** Chon muc mac dinh theo cai dat: dung muc da chon, thieu luong cao thi ve 480p. */
    public static int pickIndex(List<String> labels, int wantHeight) {
        if (labels == null || labels.isEmpty()) return 0;
        // 1) Dung muc muon neu co (uu tien co tieng ♪).
        for (int k = 0; k < labels.size(); k++) {
            String lb = labels.get(k);
            if (lb != null && lb.startsWith(wantHeight + "p")
                    && lb.indexOf(0x266A) >= 0) return k;
        }
        for (int k = 0; k < labels.size(); k++) {
            String lb = labels.get(k);
            if (lb != null && lb.startsWith(wantHeight + "p")) return k;
        }
        // 2) Thieu luong cao (720p/1080p) -> mac dinh 480p nhu yeu cau.
        for (int k = 0; k < labels.size(); k++) {
            String lb = labels.get(k);
            if (lb != null && lb.startsWith("480p")
                    && lb.indexOf(0x266A) >= 0) return k;
        }
        for (int k = 0; k < labels.size(); k++) {
            String lb = labels.get(k);
            if (lb != null && lb.startsWith("480p")) return k;
        }
        // 3) Van thieu -> 360p co tieng, roi muc dau.
        for (int k = 0; k < labels.size(); k++) {
            String lb = labels.get(k);
            if (lb != null && lb.startsWith("360p")
                    && lb.indexOf(0x266A) >= 0) return k;
        }
        return 0;
    }

    public static int heightFromLabel(String label, int fallback) {
        try {
            String d = label.replaceAll("[^0-9]", "");
            if (d.length() > 4) d = d.substring(0, 4);
            return Integer.parseInt(d);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
