package com.example.youtubelite;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình chính: chỉ còn "Gợi ý cho bạn" (video liên quan tới lịch sử xem).
 * - Đã bỏ mục "Thịnh hành tại Việt Nam" theo yêu cầu user (2.8).
 * - Ô tìm kiếm: Enter trên bàn phím điện thoại để tìm + lưu lịch sử từ khóa,
 *   bấm từ khóa cũ để tìm lại, giữ lâu để xóa.
 * - Hiện 10 video, nút "Xem thêm" để hiện tiếp.
 * Bấm video -> mở PlayerActivity (lấy link như SearchActivity).
 */
public class HomeActivity extends AppCompatActivity {
    private final List<SearchActivity.VideoItem> suggestFull = new ArrayList<>();
    private LinearLayout suggestContainer;
    private Button suggestMoreBtn;
    private TextView suggestState;
    private TextView suggestTitle;
    private ScrollView homeScroll;
    private LinearLayout historyContainer;
    private TextView historyTitle;
    private android.widget.HorizontalScrollView historyScroll;
    private EditText queryInput;
    private int suggestShown = 10;
    private static final int PAGE = 10;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        queryInput = findViewById(R.id.home_query_input);
        Button searchBtn = findViewById(R.id.home_search_btn);
        Button historyBtn = findViewById(R.id.home_history_btn);
        suggestContainer = findViewById(R.id.home_suggest_container);
        suggestMoreBtn = findViewById(R.id.home_suggest_more_btn);
        suggestState = findViewById(R.id.home_suggest_state);
        suggestTitle = findViewById(R.id.home_suggest_title);
        homeScroll = findViewById(R.id.home_scroll);
        historyContainer = findViewById(R.id.home_history_container);
        historyTitle = findViewById(R.id.home_history_title);
        historyScroll = findViewById(R.id.home_history_scroll);

        searchBtn.setOnClickListener(v -> doSearchFromHome());
        // 2.8: Enter trên bàn phím điện thoại để tìm (không cần bấm nút Tìm).
        queryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO
                    || (event != null
                        && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                doSearchFromHome();
                return true;
            }
            return false;
        });

        historyBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, HistoryActivity.class);
            startActivity(i);
        });

        // 2.9: nut Cai dat (hen gio tat + do phan giai mac dinh).
        try {
            Button settingsBtn = findViewById(R.id.home_settings_btn);
            settingsBtn.setOnClickListener(v -> {
                startActivity(new Intent(this, SettingsActivity.class));
            });
        } catch (Exception ignored) {
        }

        suggestMoreBtn.setOnClickListener(v -> {
            suggestShown += PAGE;
            renderSuggest();
        });

        // Muc 6 UI/UX: keo gan het tu tai them (do phai bam Xem them o day list).
        try {
            if (homeScroll != null) {
                homeScroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
                    try {
                        android.view.View child = homeScroll.getChildAt(0);
                        if (child == null) return;
                        int diff = child.getBottom() - (homeScroll.getHeight() + homeScroll.getScrollY());
                        if (diff <= 600 && suggestShown < suggestFull.size()) {
                            suggestShown += PAGE;
                            renderSuggest();
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
        } catch (Exception ignored) {
        }

        suggestTitle.setText("Gợi ý cho bạn");
        renderSearchHistory();
        loadSuggest();
        // Muc 2 UI/UX: thanh dieu huong chung duoi cung.
        NavBar.bind(this);
    }

    private void doSearchFromHome() {
        String q = queryInput.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "Nhập từ khóa tìm kiếm", Toast.LENGTH_SHORT).show();
            return;
        }
        SearchHistory.push(this, q);
        renderSearchHistory();
        Intent i = new Intent(this, SearchActivity.class);
        i.putExtra(SearchActivity.EXTRA_QUERY, q);
        startActivity(i);
    }

    /** Vẽ các từ khóa đã tìm: bấm để tìm lại, giữ lâu để xóa (chip tối, Muc 4 UI/UX). */
    private void renderSearchHistory() {
        if (historyContainer == null) return;
        ChipHelper.renderHistoryChips(this, historyContainer, historyTitle, historyScroll,
                q -> {
                    queryInput.setText(q);
                    doSearchFromHome();
                },
                q -> {
                    SearchHistory.remove(this, q);
                    renderSearchHistory();
                    ChipHelper.toastDeleted(this, q);
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Quay lại từ player -> lịch sử có thêm video mới -> tải lại gợi ý + từ khóa.
        renderSearchHistory();
        loadSuggest();
    }

    private void loadSuggest() {
        suggestState.setText("Đang tải gợi ý...");
        suggestState.setVisibility(View.VISIBLE);
        suggestMoreBtn.setVisibility(View.GONE);
        // Muc 5 UI/UX: vong xoay khi tai, loi co nut Thu lai.
        StateHelper.showLoading(this);
        new Thread(() -> {
            HomeSuggest.Bundle bundle = HomeSuggest.load(this);
            runOnUiThread(() -> {
                suggestFull.clear();
                // Chỉ giữ video liên quan lịch sử (hết fallback trending theo yêu cầu 2.8).
                suggestFull.addAll(bundle.related);
                suggestShown = PAGE;
                renderSuggest();
                if (suggestFull.isEmpty()) {
                    String msg = bundle.relatedError.isEmpty()
                            ? "Xem vài video để có gợi ý riêng" : "Lỗi: " + bundle.relatedError;
                    suggestState.setText(msg);
                    suggestState.setVisibility(View.VISIBLE);
                    // Co loi mang that thi hien nut Thu lai, chua xem gi thi thoi.
                    if (!bundle.relatedError.isEmpty()) {
                        StateHelper.showError(this, msg, this::loadSuggest);
                    } else {
                        StateHelper.showContent(this);
                    }
                } else {
                    suggestState.setVisibility(View.GONE);
                    StateHelper.showContent(this);
                }
            });
        }).start();
    }

    private void renderSuggest() {
        renderList(suggestContainer, suggestFull, suggestShown);
        suggestMoreBtn.setVisibility(
                suggestShown < suggestFull.size() ? View.VISIBLE : View.GONE);
    }

    /** Vẽ tối đa shown video vào container (inflate item_video cho từng video). */
    private void renderList(LinearLayout container,
                            List<SearchActivity.VideoItem> full, int shown) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int n = Math.min(shown, full.size());
        for (int i = 0; i < n; i++) {
            SearchActivity.VideoItem item = full.get(i);
            View row = inflater.inflate(R.layout.item_video, container, false);
            ImageView thumb = row.findViewById(R.id.video_thumb);
            TextView title = row.findViewById(R.id.video_title);
            TextView meta = row.findViewById(R.id.video_meta);
            TextView duration = row.findViewById(R.id.video_duration);
            title.setText(item.title != null ? item.title : "");
            String m = item.uploader != null ? item.uploader : "";
            if (item.uploadDate != null && !item.uploadDate.isEmpty()) {
                if (!m.isEmpty()) m += " • ";
                m += item.uploadDate;
            }
            meta.setText(m);
            meta.setVisibility(m.isEmpty() ? View.GONE : View.VISIBLE);
            String dur = item.durationLabel();
            duration.setText(dur);
            duration.setVisibility(dur.isEmpty() ? View.GONE : View.VISIBLE);
            if (item.thumbUrl != null && !item.thumbUrl.isEmpty()) {
                Glide.with(this).load(item.thumbUrl).centerCrop().into(thumb);
            } else {
                thumb.setImageResource(android.R.color.darker_gray);
            }
            // Muc 1 UI/UX: nut Xem kenh rieng trong tung dong goi y.
            TextView channelLink = row.findViewById(R.id.video_channel_link);
            if (channelLink != null) {
                if (item.uploaderUrl != null && !item.uploaderUrl.isEmpty()) {
                    channelLink.setVisibility(View.VISIBLE);
                    channelLink.setOnClickListener(v ->
                            ChannelActivity.open(this, item.uploaderUrl, item.uploader));
                } else {
                    channelLink.setVisibility(View.GONE);
                    channelLink.setOnClickListener(null);
                }
            }
            row.setOnClickListener(v -> openVideo(item));
            container.addView(row);
        }
    }

    /** Bấm video đề xuất -> lấy link phát y hệt SearchActivity (có tiếng, đủ mức). */
    private void openVideo(SearchActivity.VideoItem item) {
        Toast.makeText(this, "Đang lấy link phát...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                NewPipeHolder.initIfNeeded();
                org.schabi.newpipe.extractor.StreamingService yt2 =
                        org.schabi.newpipe.extractor.NewPipe.getService(0);
                org.schabi.newpipe.extractor.stream.StreamInfo detail =
                        org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(yt2, item.videoId);
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
                final String fDashHome = dashMpd != null ? dashMpd : "";
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
                    // 2.7: giu link kenh de man phat bam ten kenh mo trang kenh.
                    try {
                        String cu = detail.getUploaderUrl();
                        if (cu != null && !cu.isEmpty()) item.uploaderUrl = cu;
                    } catch (Exception ignored) {
                    }
                    // Mở player dùng chung openPlayer của SearchActivity
                    // (tự ghi lịch sử xem + đủ audio/độ phân giải).
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
                            allUrls, allLabels, allAudios, fDashHome);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Không lấy được link phát: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
