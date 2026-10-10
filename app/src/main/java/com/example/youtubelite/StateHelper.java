package com.example.youtubelite;

import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Muc 5 UI/UX: cum trang thai tai/lỗi/thử lại dùng chung cho các màn hình list.
 * Layout can: ProgressBar (id state_loading), TextView (id state_error),
 * Button Thu lai (id state_retry). API 21 OK.
 */
public final class StateHelper {
    private StateHelper() {}

    public interface OnRetry {
        void onRetry();
    }

    public static void showLoading(android.app.Activity activity) {
        set(activity, true, false, null, null);
    }

    public static void showContent(android.app.Activity activity) {
        set(activity, false, false, null, null);
    }

    public static void showError(android.app.Activity activity, String message, OnRetry retry) {
        set(activity, false, true, message, retry);
    }

    private static void set(android.app.Activity activity, boolean loading,
                            boolean error, String message, OnRetry retry) {
        try {
            ProgressBar bar = activity.findViewById(R.id.state_loading);
            TextView err = activity.findViewById(R.id.state_error);
            Button retryBtn = activity.findViewById(R.id.state_retry);
            if (bar == null || err == null || retryBtn == null) return;
            bar.setVisibility(loading ? View.VISIBLE : View.GONE);
            err.setVisibility(error ? View.VISIBLE : View.GONE);
            retryBtn.setVisibility(error ? View.VISIBLE : View.GONE);
            if (error && message != null) err.setText(message);
            if (error) {
                retryBtn.setOnClickListener(v -> {
                    if (retry != null) retry.onRetry();
                });
            } else {
                retryBtn.setOnClickListener(null);
            }
        } catch (Exception ignored) {
        }
    }
}
