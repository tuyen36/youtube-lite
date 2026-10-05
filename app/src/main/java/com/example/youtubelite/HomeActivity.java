package com.example.youtubelite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình chính: đề xuất khi mở app.
 * - Mục "Thịnh hành tại Việt Nam" (trending VN).
 * - Mục "Gợi ý cho bạn" (video liên quan tới lịch sử xem, fallback = trending).
 * - Ô tìm kiếm trên cùng -> sang SearchActivity.
 * Bấm video -> mở PlayerActivity (lấy link như SearchActivity).
 */
public class HomeActivity extends AppCompatActivity {
    private VideoAdapter trendingAdapter;
    private VideoAdapter suggestAdapter;
    private final List<SearchActivity.VideoItem> trending = new ArrayList<>();
    private final List<SearchActivity.VideoItem> suggest = new ArrayList<>();
    private TextView trendingState;
    private TextView suggestState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        EditText queryInput = findViewById(R.id.home_query_input);
        Button searchBtn = findViewById(R.id.home_search_btn);
        Button historyBtn = findViewById(R.id.home_history_btn);
        ListView trendingList = findViewById(R.id.home_trending_list);
        ListView suggestList = findViewById(R.id.home_suggest_list);
        trendingState = findViewById(R.id.home_trending_state);
        suggestState = findViewById(R.id.home_suggest_state);
        TextView suggestTitle = findViewById(R.id.home_suggest_title);

        trendingAdapter = new VideoAdapter(this, trending);
        trendingList.setAdapter(trendingAdapter);
        suggestAdapter = new VideoAdapter(this, suggest);
        suggestList.setAdapter(suggestAdapter);

        AdapterView.OnItemClickListener open = (parent, view, position, id) -> {
            List<SearchActivity.VideoItem> src =
                    parent == trendingList ? trending : suggest;
            if (position < 0 || position >= src.size()) return;
            SearchActivity.VideoItem item = src.get(position);
            openVideo(item);
        };
        trendingList.setOnItemClickListener(open);
        suggestList.setOnItemClickListener(open);

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
            List<WatchHistory.Entry> hist = WatchHistory.list(this);
            if (hist.isEmpty()) {
                Toast.makeText(this, "Chưa có lịch sử xem", Toast.LENGTH_SHORT).show();
                return;
            }
            StringBuilder sb = new StringBuilder();
            int n = Math.min(hist.size(), 10);
            for (int k = 0; k < n; k++) {
                sb.append(k + 1).append(". ").append(hist.get(k).title).append("\n");
            }
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Đã xem gần đây (" + hist.size() + ")")
                    .setMessage(sb.toString().trim())
                    .setPositiveButton("Đóng", null)
                    .setNeutralButton("Xoá lịch sử", (d, w) -> {
                        WatchHistory.clear(this);
                        Toast.makeText(this, "Đã xoá lịch sử", Toast.LENGTH_SHORT).show();
                        loadSuggest();
                    })
                    .show();
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
        new Thread(() -> {
            HomeSuggest.Bundle bundle = HomeSuggest.load(this);
            runOnUiThread(() -> {
                trending.clear();
                trending.addAll(bundle.trending);
                trendingAdapter.notifyDataSetChanged();
                if (bundle.trending.isEmpty()) {
                    trendingState.setText(bundle.trendingError.isEmpty()
                            ? "Không tải được thịnh hành" : "Lỗi: " + bundle.trendingError);
                } else {
                    trendingState.setVisibility(View.GONE);
                }
                suggest.clear();
                if (!bundle.related.isEmpty()) {
                    suggest.addAll(bundle.related);
                } else {
                    // Chưa xem gì / không lấy được related -> fallback trending.
                    suggest.addAll(bundle.trending);
                }
                suggestAdapter.notifyDataSetChanged();
                if (suggest.isEmpty()) {
                    suggestState.setText(bundle.relatedError.isEmpty()
                            ? "Xem vài video để có gợi ý riêng" : "Lỗi: " + bundle.relatedError);
                } else {
                    suggestState.setVisibility(View.GONE);
                }
            });
        }).start();
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
