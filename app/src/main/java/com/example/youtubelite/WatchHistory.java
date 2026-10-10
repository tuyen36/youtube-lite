package com.example.youtubelite;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lịch sử xem lưu local (SharedPreferences, tối đa 50 video mới nhất).
 * Dùng để gợi ý "xem tiếp / video tương tự" trên màn hình chính.
 */
public final class WatchHistory {
    private static final String PREF = "youtube_lite_history";
    private static final String KEY = "items_json";
    private static final int MAX = 50;

    private WatchHistory() {}

    public static class Entry {
        public final String url;
        public final String title;
        public final String thumbUrl;
        public final long durationSec;
        public final String uploader;
        public final long watchedAt;
        public final String uploaderUrl; // Muc 1 UI/UX: nut Xem kenh o trang Da xem

        public Entry(String url, String title, String thumbUrl,
                     long durationSec, String uploader, long watchedAt) {
            this(url, title, thumbUrl, durationSec, uploader, watchedAt, "");
        }

        public Entry(String url, String title, String thumbUrl,
                     long durationSec, String uploader, long watchedAt, String uploaderUrl) {
            this.url = url != null ? url : "";
            this.title = title != null ? title : "";
            this.thumbUrl = thumbUrl != null ? thumbUrl : "";
            this.durationSec = durationSec;
            this.uploader = uploader != null ? uploader : "";
            this.watchedAt = watchedAt;
            this.uploaderUrl = uploaderUrl != null ? uploaderUrl : "";
        }
    }

    public static synchronized void push(Context ctx, String url, String title,
                                         String thumbUrl, long durationSec, String uploader) {
        push(ctx, url, title, thumbUrl, durationSec, uploader, "");
    }

    public static synchronized void push(Context ctx, String url, String title,
                                         String thumbUrl, long durationSec, String uploader,
                                         String uploaderUrl) {
        if (url == null || url.isEmpty()) return;
        List<Entry> cur = list(ctx);
        List<Entry> next = new ArrayList<>();
        next.add(new Entry(url, title, thumbUrl, durationSec, uploader,
                System.currentTimeMillis(), uploaderUrl));
        for (Entry e : cur) {
            if (!e.url.equals(url)) next.add(e);
            if (next.size() >= MAX) break;
        }
        save(ctx, next);
    }

    public static synchronized List<Entry> list(Context ctx) {
        List<Entry> out = new ArrayList<>();
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String raw = sp.getString(KEY, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(
                        o.optString("url", ""),
                        o.optString("title", ""),
                        o.optString("thumb", ""),
                        o.optLong("dur", -1),
                        o.optString("uploader", ""),
                        o.optLong("at", 0),
                        o.optString("uploaderUrl", "")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static synchronized void clear(Context ctx) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    /** Xoá 1 video khỏi lịch sử theo URL (bấm giữ trong trang Đã xem). */
    public static synchronized void remove(Context ctx, String url) {
        if (url == null || url.isEmpty()) return;
        List<Entry> cur = list(ctx);
        List<Entry> next = new ArrayList<>();
        for (Entry e : cur) {
            if (!e.url.equals(url)) next.add(e);
        }
        save(ctx, next);
    }

    private static void save(Context ctx, List<Entry> items) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : items) {
                JSONObject o = new JSONObject();
                o.put("url", e.url);
                o.put("title", e.title);
                o.put("thumb", e.thumbUrl);
                o.put("dur", e.durationSec);
                o.put("uploader", e.uploader);
                o.put("at", e.watchedAt);
                o.put("uploaderUrl", e.uploaderUrl);
                arr.put(o);
            }
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
