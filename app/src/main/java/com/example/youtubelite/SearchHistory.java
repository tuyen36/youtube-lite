package com.example.youtubelite;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Lich su tu khoa tim kiem (SharedPreferences, toi da 15 tu khoa moi nhat).
 * Hien duoi o tim kiem o man hinh chinh + man hinh tim kiem de bam lai.
 */
public final class SearchHistory {
    private static final String PREF = "youtube_lite_search_history";
    private static final String KEY = "queries_json";
    private static final int MAX = 15;

    private SearchHistory() {}

    public static synchronized void push(Context ctx, String query) {
        if (query == null) return;
        String q = query.trim();
        if (q.isEmpty()) return;
        List<String> cur = list(ctx);
        List<String> next = new ArrayList<>();
        next.add(q);
        for (String s : cur) {
            if (!s.equalsIgnoreCase(q)) next.add(s);
            if (next.size() >= MAX) break;
        }
        save(ctx, next);
    }

    public static synchronized List<String> list(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String raw = sp.getString(KEY, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "").trim();
                if (!s.isEmpty()) out.add(s);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static synchronized void remove(Context ctx, String query) {
        if (query == null) return;
        List<String> cur = list(ctx);
        List<String> next = new ArrayList<>();
        for (String s : cur) {
            if (!s.equalsIgnoreCase(query.trim())) next.add(s);
        }
        save(ctx, next);
    }

    public static synchronized void clear(Context ctx) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    private static void save(Context ctx, List<String> items) {
        try {
            JSONArray arr = new JSONArray();
            for (String s : items) arr.put(s);
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
