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
 * Màn hình chính: đề xuất khi mở app.
 * - Mục "Thịnh hành tại Việt Nam" (trending VN, nhiều nguồn dự phòng).
 * - Mục "Gợi ý cho bạn" (video liên quan tới lịch sử xem, fallback = trending).
 * - Ô tìm kiếm trên cùng -> sang SearchActivity.
 * - Hiện 10 video/mục, nút "Xem thêm" để hiện tiếp (không dùng ListView trong
 *   ScrollView vì ListView chỉ đo được 1 dòng -> gợi ý mãi chỉ hiện 1 video).
 * Bấm video -> mở PlayerActivity (lấy link như SearchActivity).
 */
public class HomeActivity extends AppCompatActivity {
    private final List<SearchActivity.VideoItem> trendingFull = new ArrayList<>();
    private final List<SearchActivity.VideoItem> suggestFull = new ArrayList<>();
    private LinearLayout trendingContainer;
    private LinearLayout suggestContainer;
    private Button trendingMoreBtn;
    private Button suggestMoreBtn;
    private TextView trendingState;
    private TextView suggestState;
    private TextView suggestTitle;
    private ScrollView homeScroll;
    private int trendingShown = 10;
    private int suggestShown = 10;
    private static final int PAGE = 10;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        EditText queryInput = findViewById(R.id.home_query_input);
        Button searchBtn = findViewById(R.id.home_search_btn);
        Button historyBtn = findViewById(R.id.home_history_btn);
        trendingContainer = findViewById(R.id.home_trending_container);
        suggestContainer = findViewById(R.id.home_suggest_container);
        trendingMoreBtn = findViewById(R.id.home_trending_more_btn);
        suggestMoreBtn = findViewById(R.id.home_suggest_more_btn);
        trendingState = findViewById(R.id.home_trending_state);
        suggestState = findViewById(R.id.home_suggest_state);
        suggestTitle = findViewById(R.id.home_suggest_title);
        homeScroll = findViewById(R.id.home_scroll);

        searchBtn.setOnClickListener(v -> {
            String q = queryInput.getText().toString().trim();
            if (q.isEmpty()) {
                Toast.makeText(this, "Nhập từ khóa tìm kiếm", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, SearchActivity.class);
            i.putExtra(SearchActivity.EXTRA_QUERY, q);
            startActivity(i);
        });

        historyBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, HistoryActivity.class);
            startActivity(i);
        });

        trendingMoreBtn.setOnClickListener(v -> {
            trendingShown += PAGE;
            renderTrending();
        });
        suggestMoreBtn.setOnClickListener(v -> {
            suggestShown += PAGE;
            renderSuggest();
        });

        suggestTitle.setText("Gợi ý cho bạn");
        loadSuggest();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Quay lại từ player -> lịch sử có thêm video mới -> tải lại gợi ý.
        loadSuggest();
    }

    private void loadSuggest() {
        trendingState.setText("Đang tải thịnh hành...");
        trendingState.setVisibility(View.VISIBLE);
        suggestState.setText("Đang tải gợi ý...");
        suggestState.setVisibility(View.VISIBLE);
        trendingMoreBtn.setVisibility(View.GONE);
        suggestMoreBtn.setVisibility(View.GONE);
        new Thread(() -> {
            HomeSuggest.Bundle bundle = HomeSuggest.load(this);
            runOnUiThread(() -> {
                trendingFull.clear();
                trendingFull.addAll(bundle.trending);
                trendingShown = PAGE;
                renderTrending();
                if (bundle.trending.isEmpty()) {
                    String err = bundle.trendingError.isEmpty()
                            ? "Không tải được thịnh hành" : "Lỗi: " + bundle.trendingError;
                    // Hiện rõ đang dùng nguồn nào khi có.
                    trendingState.setText(err);
                } else {
                    String src = bundle.trendingSource.isEmpty() ? ""
                            : " (nguồn: " + bundle.trendingSource + ")";
                    trendingState.setText("Thịnh hành" + src + " — bấm video để xem");
                    // Ẩn dòng trạng thái sau 3s cho gọn.
                    trendingState.postDelayed(() -> {
                        if (!isFinishing()) trendingState.setVisibility(View.GONE);
                    }, 3000);
                }

                suggestFull.clear();
                if (!bundle.related.isEmpty()) {
                    suggestFull.addAll(bundle.related);
                } else {
                    // Chưa xem gì / không lấy được related -> fallback trending.
                    suggestFull.addAll(bundle.trending);
                }
                suggestShown = PAGE;
                renderSuggest();
                if (suggestFull.isEmpty()) {
                    suggestState.setText(bundle.relatedError.isEmpty()
                            ? "Xem vài video để có gợi ý riêng" : "Lỗi: " + bundle.relatedError);
                    suggestState.setVisibility(View.VISIBLE);
                } else if (bundle.related.isEmpty()) {
                    suggestState.setText("Chưa có lịch sử — đang hiện thịnh hành, xem vài video để có gợi ý riêng");
                    suggestState.setVisibility(View.VISIBLE);
                } else {
                    suggestState.setVisibility(View.GONE);
                }
            });
        }).start();
    }

    private void renderTrending() {
        renderList(trendingContainer, trendingFull, trendingShown);
        trendingMoreBtn.setVisibility(
                trendingShown < trendingFull.size() ? View.VISIBLE : View.GONE);
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
                    int defIdx = 0;
                    for (int k = 0; k < allLabels.size(); k++) {
                        if (allLabels.get(k).startsWith("480p")) { defIdx = k; break; }
                    }
                    // Mở player dùng chung openPlayer của SearchActivity
                    // (tự ghi lịch sử xem + đủ audio/độ phân giải).
                    SearchActivity.openPlayer(this, item,
                            allUrls.get(defIdx), 480, allAudios.get(defIdx),
                            allUrls, allLabels, allAudios);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Không lấy được link phát: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
