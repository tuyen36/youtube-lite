package com.example.youtubelite;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Trang "Video đã xem": đầy đủ thumbnail + tiêu đề + kênh + độ dài + giờ xem.
 * - Bấm video -> xem lại (lấy link phát như SearchActivity/HomeActivity).
 * - Bấm giữ (long-press) 1 video -> xoá video đó khỏi lịch sử.
 * - Nút "Xoá tất cả" + nút "Đóng".
 * Tối đa 50 video mới nhất (do WatchHistory giữ).
 */
public class HistoryActivity extends AppCompatActivity {
    private LinearLayout container;
    private TextView stateText;
    private TextView countText;
    private final List<WatchHistory.Entry> items = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        container = findViewById(R.id.history_container);
        stateText = findViewById(R.id.history_state);
        countText = findViewById(R.id.history_count);
        Button clearBtn = findViewById(R.id.history_clear_btn);
        Button closeBtn = findViewById(R.id.history_close_btn);
        ScrollView scroll = findViewById(R.id.history_scroll);

        clearBtn.setOnClickListener(v -> {
            if (items.isEmpty()) {
                Toast.makeText(this, "Lịch sử đang trống", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Xoá tất cả lịch sử?")
                    .setMessage("Xoá " + items.size() + " video đã xem. Gợi ý trên màn hình chính sẽ quay về thịnh hành.")
                    .setPositiveButton("Xoá hết", (d, w) -> {
                        WatchHistory.clear(this);
                        loadHistory();
                        Toast.makeText(this, "Đã xoá lịch sử", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Giữ lại", null)
                    .show();
        });
        closeBtn.setOnClickListener(v -> finish());
        // Muc 2 UI/UX: thanh dieu huong chung duoi cung.
        NavBar.bind(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        items.clear();
        items.addAll(WatchHistory.list(this));
        render();
    }

    private void render() {
        container.removeAllViews();
        if (items.isEmpty()) {
            countText.setText("Chưa có video nào");
            stateText.setVisibility(View.VISIBLE);
            stateText.setText("Xem vài video để lịch sử hiện ở đây.\nGợi ý trên màn hình chính cũng dựa vào lịch sử này.");
            return;
        }
        countText.setText("Đã xem (" + items.size() + "/50)");
        stateText.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault());
        for (int i = 0; i < items.size(); i++) {
            WatchHistory.Entry e = items.get(i);
            View row = inflater.inflate(R.layout.item_video, container, false);
            ImageView thumb = row.findViewById(R.id.video_thumb);
            TextView title = row.findViewById(R.id.video_title);
            TextView meta = row.findViewById(R.id.video_meta);
            TextView duration = row.findViewById(R.id.video_duration);
            title.setText(e.title != null && !e.title.isEmpty() ? e.title : "(không rõ tiêu đề)");
            StringBuilder m = new StringBuilder();
            if (e.uploader != null && !e.uploader.isEmpty()) m.append(e.uploader);
            if (e.watchedAt > 0) {
                if (m.length() > 0) m.append(" • ");
                m.append(fmt.format(new Date(e.watchedAt)));
            }
            meta.setText(m.toString());
            meta.setVisibility(m.length() == 0 ? View.GONE : View.VISIBLE);
            // Muc 1 UI/UX: nut Xem kenh rieng (trang Da xem co link kenh tu 2.12).
            TextView channelLink = row.findViewById(R.id.video_channel_link);
            if (channelLink != null) {
                if (e.uploaderUrl != null && !e.uploaderUrl.isEmpty()) {
                    channelLink.setVisibility(View.VISIBLE);
                    channelLink.setOnClickListener(v ->
                            ChannelActivity.open(this, e.uploaderUrl, e.uploader));
                } else {
                    channelLink.setVisibility(View.GONE);
                    channelLink.setOnClickListener(null);
                }
            }
            String dur = durationLabel(e.durationSec);
            duration.setText(dur);
            duration.setVisibility(dur.isEmpty() ? View.GONE : View.VISIBLE);
            if (e.thumbUrl != null && !e.thumbUrl.isEmpty()) {
                Glide.with(this).load(e.thumbUrl).centerCrop().into(thumb);
            } else {
                thumb.setImageResource(android.R.color.darker_gray);
            }
            final int pos = i;
            row.setOnClickListener(v -> replay(pos));
            // Giữ lâu -> hỏi xoá 1 video này.
            row.setOnLongClickListener(v -> {
                confirmDeleteOne(pos);
                return true;
            });
            container.addView(row);
        }
    }

    /** Bấm video -> xem lại: lấy link phát như SearchActivity/HomeActivity. */
    private void replay(int position) {
        if (position < 0 || position >= items.size()) return;
        WatchHistory.Entry e = items.get(position);
        if (e.url == null || e.url.isEmpty()) {
            Toast.makeText(this, "Video này thiếu link, không xem lại được", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Đang lấy link phát...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                NewPipeHolder.initIfNeeded();
                org.schabi.newpipe.extractor.StreamingService yt =
                        org.schabi.newpipe.extractor.NewPipe.getService(0);
                org.schabi.newpipe.extractor.stream.StreamInfo detail =
                        org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(yt, e.url);
                String bestAudio = null;
                try {
                    if (detail.getAudioStreams() != null && !detail.getAudioStreams().isEmpty()) {
                        bestAudio = detail.getAudioStreams().get(0).getContent();
                    }
                } catch (Exception ignored) {
                    bestAudio = null;
                }
                // 2.11: link DASH thich ung cho 720p/1080p (het khung nhu YouTube goc).
                String dashMpd = "";
                try {
                    dashMpd = detail.getDashMpdUrl();
                } catch (Exception ignored) {
                    dashMpd = "";
                }
                final String fDashHist = dashMpd != null ? dashMpd : "";
                List<QualityPolicy.Stream> rawProg = new ArrayList<>();
                for (org.schabi.newpipe.extractor.stream.VideoStream vs : detail.getVideoStreams()) {
                    rawProg.add(SearchActivity.mapVideoStream(vs));
                }
                List<QualityPolicy.Stream> prog = QualityPolicy.filter(rawProg);
                List<QualityPolicy.Stream> rawOnly = new ArrayList<>();
                for (org.schabi.newpipe.extractor.stream.VideoStream vs : detail.getVideoOnlyStreams()) {
                    rawOnly.add(SearchActivity.mapVideoStream(vs));
                }
                List<QualityPolicy.Stream> only = QualityPolicy.filter(rawOnly);
                ArrayList<String> allUrls = new ArrayList<>();
                ArrayList<String> allLabels = new ArrayList<>();
                ArrayList<String> allAudios = new ArrayList<>();
                java.util.Set<Integer> seenHeights = new java.util.HashSet<>();
                for (QualityPolicy.Stream s : prog) {
                    if (seenHeights.contains(s.height)) continue;
                    seenHeights.add(s.height);
                    allUrls.add(s.url);
                    allLabels.add(s.label() + " \u266A");
                    allAudios.add(null);
                }
                for (QualityPolicy.Stream s : only) {
                    if (seenHeights.contains(s.height)) continue;
                    seenHeights.add(s.height);
                    allUrls.add(s.url);
                    allLabels.add(s.label());
                    allAudios.add(bestAudio);
                }
                runOnUiThread(() -> {
                    if (allUrls.isEmpty()) {
                        Toast.makeText(this, "Không có định dạng phù hợp máy này", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 2.9: muc mac dinh lay tu Cai dat (thieu luong cao ve 480p).
                    int defIdx = PlayerActivity.pickDefaultIndex(this, allLabels);
                    String channelUrl = "";
                    try {
                        channelUrl = detail.getUploaderUrl();
                    } catch (Exception ignored) {
                    }
                    SearchActivity.VideoItem item = new SearchActivity.VideoItem(
                            e.url, e.title, new ArrayList<>(), e.thumbUrl, e.durationSec,
                            e.uploader, "", channelUrl != null ? channelUrl : "");
                    int h0 = AppSettings.getDefaultHeight(this);
                    try {
                        String d = allLabels.get(defIdx).replaceAll("[^0-9]", "");
                        if (d.length() > 4) d = d.substring(0, 4);
                        h0 = Integer.parseInt(d);
                    } catch (Exception ignored) {
                        h0 = 360;
                    }
                    SearchActivity.openPlayer(this, item,
                            allUrls.get(defIdx), h0, allAudios.get(defIdx),
                            allUrls, allLabels, allAudios, fDashHist);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Không lấy được link phát: " + ex.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void confirmDeleteOne(int position) {
        if (position < 0 || position >= items.size()) return;
        WatchHistory.Entry e = items.get(position);
        new AlertDialog.Builder(this)
                .setTitle("Xoá video này khỏi lịch sử?")
                .setMessage(e.title)
                .setPositiveButton("Xoá", (d, w) -> {
                    WatchHistory.remove(this, e.url);
                    loadHistory();
                })
                .setNegativeButton("Giữ lại", null)
                .show();
    }

    private static String durationLabel(long durationSec) {
        if (durationSec < 0) return "";
        long m = durationSec / 60, s = durationSec % 60;
        if (m >= 60) return String.format(Locale.getDefault(), "%d:%02d:%02d", m / 60, m % 60, s);
        return String.format(Locale.getDefault(), "%d:%02d", m, s);
    }
}
