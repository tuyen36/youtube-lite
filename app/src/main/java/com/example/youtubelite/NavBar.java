package com.example.youtubelite;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

/**
 * Muc 2 UI/UX: thanh dieu huong chung duoi cung (Trang chu / Tim kiem / Da xem / Cai dat).
 * Moi man hinh chinh include layout nav_bar + goi NavBar.bind(this) trong onCreate.
 * Nut cua man hinh hien tai bi mo di. Dung REORDER_TO_FRONT de khong chong activity.
 * API 21 OK.
 */
public final class NavBar {
    private NavBar() {}

    public static void bind(Activity activity) {
        Button home = activity.findViewById(R.id.nav_home);
        Button search = activity.findViewById(R.id.nav_search);
        Button history = activity.findViewById(R.id.nav_history);
        Button settings = activity.findViewById(R.id.nav_settings);
        if (home == null || search == null || history == null || settings == null) return;

        wire(activity, home, HomeActivity.class);
        wire(activity, search, SearchActivity.class);
        wire(activity, history, HistoryActivity.class);
        wire(activity, settings, SettingsActivity.class);
    }

    private static void wire(Activity activity, Button btn, Class<?> target) {
        if (activity.getClass() == target) {
            // Dang o man nay: mo nut de biet vi tri hien tai.
            try {
                btn.setEnabled(false);
                btn.setAlpha(0.5f);
            } catch (Exception ignored) {
            }
            return;
        }
        btn.setOnClickListener(v -> {
            try {
                Intent i = new Intent(activity, target);
                i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(i);
            } catch (Exception ignored) {
            }
        });
    }

    /** An/hien thanh dieu huong (man phat fullscreen thi an). */
    public static void setVisible(Activity activity, boolean visible) {
        try {
            View bar = activity.findViewById(R.id.nav_bar_container);
            if (bar != null) bar.setVisibility(visible ? View.VISIBLE : View.GONE);
        } catch (Exception ignored) {
        }
    }
}
