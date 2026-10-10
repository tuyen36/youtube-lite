package com.example.youtubelite;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

/**
 * Muc 4 UI/UX: chip tu khoa toi (nen #333 chu trang) dung chung cho
 * man hinh chinh + man hinh tim kiem. API 21 OK.
 */
public final class ChipHelper {
    private ChipHelper() {}

    public interface OnChipClick {
        void onClick(String query);
    }

    public interface OnChipLongClick {
        void onLongClick(String query);
    }

    public static void renderHistoryChips(Context ctx, LinearLayout box,
                                          android.view.View title, android.view.View scroll,
                                          OnChipClick onClick, OnChipLongClick onLongClick) {
        if (box == null) return;
        List<String> queries = SearchHistory.list(ctx);
        box.removeAllViews();
        if (queries.isEmpty()) {
            if (title != null) title.setVisibility(View.GONE);
            if (scroll != null) scroll.setVisibility(View.GONE);
            return;
        }
        if (title != null) title.setVisibility(View.VISIBLE);
        if (scroll != null) scroll.setVisibility(View.VISIBLE);
        for (String q : queries) {
            Button b = new Button(ctx);
            b.setText(q);
            b.setTextSize(13);
            b.setAllCaps(false);
            try {
                b.setBackgroundColor(0xFF333333);
                b.setTextColor(0xFFFFFFFF);
            } catch (Exception ignored) {
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 12, 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                if (onClick != null) onClick.onClick(q);
            });
            b.setOnLongClickListener(v -> {
                if (onLongClick != null) onLongClick.onLongClick(q);
                return true;
            });
            box.addView(b);
        }
    }

    public static void toastDeleted(Context ctx, String q) {
        try {
            Toast.makeText(ctx, "Đã xóa: " + q, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }
}
