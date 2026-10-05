package com.example.youtubelite;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình tìm kiếm tối giản cho máy phụ.
 * Đã nối NewPipeExtractor: tìm thật, lọc 1080p/30fps qua QualityPolicy.
 */
public class SearchActivity extends AppCompatActivity {
    private EditText queryInput;
    private ListView resultList;
    private final List<VideoItem> items = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private final List<String> titles = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        queryInput = findViewById(R.id.query_input);
        Button searchBtn = findViewById(R.id.search_btn);
        resultList = findViewById(R.id.result_list);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles);
        resultList.setAdapter(adapter);
        resultList.setOnItemClickListener((parent, view, position, id) -> {
            VideoItem item = items.get(position);
            // Lọc qua QualityPolicy trước khi phát: trần 1080p, tắt 60fps.
            List<QualityPolicy.Stream> ok = QualityPolicy.filter(item.streams);
            QualityPolicy.Stream pick = QualityPolicy.pickDefault(ok);
            if (pick == null) {
                Toast.makeText(this, "Không có định dạng phù hợp máy này", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, PlayerActivity.class);
            i.putExtra(PlayerActivity.EXTRA_VIDEO_URL, pick.url);
            i.putExtra(PlayerActivity.EXTRA_VIDEO_HEIGHT, pick.height);
            startActivity(i);
        });

        searchBtn.setOnClickListener(v -> doSearch(queryInput.getText().toString().trim()));
    }

    private void doSearch(String q) {
        if (q.isEmpty()) {
            Toast.makeText(this, "Nhập từ khóa tìm kiếm", Toast.LENGTH_SHORT).show();
            return;
        }
        // Tìm thật bằng NewPipeExtractor trên luồng nền (cấm chạy mạng trên UI thread).
        Toast.makeText(this, "Đang tìm: " + q, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                NewPipeHolder.initIfNeeded();
                org.schabi.newpipe.extractor.StreamingService yt =
                        org.schabi.newpipe.extractor.NewPipe.getService(0); // 0 = YouTube
                org.schabi.newpipe.extractor.search.SearchExtractor extractor =
                        yt.getSearchExtractor(q);
                extractor.fetchPage();
                org.schabi.newpipe.extractor.search.SearchInfo info =
                        org.schabi.newpipe.extractor.search.SearchInfo.getInfo(extractor);
                List<VideoItem> found = new ArrayList<>();
                List<String> foundTitles = new ArrayList<>();
                for (org.schabi.newpipe.extractor.InfoItem it : info.getRelatedItems()) {
                    if (!(it instanceof org.schabi.newpipe.extractor.stream.StreamInfoItem)) continue;
                    org.schabi.newpipe.extractor.stream.StreamInfoItem s =
                            (org.schabi.newpipe.extractor.stream.StreamInfoItem) it;
                    String url = s.getUrl();
                    String title = s.getName() != null ? s.getName() : url;
                    // Lấy stream chi tiết để lọc 1080p/30fps.
                    List<QualityPolicy.Stream> raw = new ArrayList<>();
                    try {
                        org.schabi.newpipe.extractor.stream.StreamInfo detail =
                                org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(yt, url);
                        for (org.schabi.newpipe.extractor.stream.VideoStream vs : detail.getVideoStreams()) {
                            raw.add(mapVideoStream(vs));
                        }
                        for (org.schabi.newpipe.extractor.stream.VideoStream vs : detail.getVideoOnlyStreams()) {
                            raw.add(mapVideoStream(vs));
                        }
                    } catch (Exception e) {
                        // Không lấy được stream chi tiết thì bỏ qua video này.
                        continue;
                    }
                    List<QualityPolicy.Stream> ok = QualityPolicy.filter(raw);
                    if (ok.isEmpty()) continue; // toàn 4K/60fps -> bỏ
                    found.add(new VideoItem(url, title, ok));
                    foundTitles.add(title + " [" + ok.get(0).label() + "+]");
                    if (found.size() >= 25) break; // máy cũ: tối đa 25 kết quả
                }
                runOnUiThread(() -> {
                    items.clear();
                    titles.clear();
                    items.addAll(found);
                    titles.addAll(foundTitles);
                    adapter.notifyDataSetChanged();
                    if (found.isEmpty()) {
                        Toast.makeText(this, "Không tìm thấy video phù hợp (toàn 4K/60fps?)", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Tìm kiếm lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** Map VideoStream của NewPipeExtractor sang Stream của QualityPolicy. */
    private static QualityPolicy.Stream mapVideoStream(
            org.schabi.newpipe.extractor.stream.VideoStream vs) {
        int height = 0;
        try {
            String res = vs.getResolution() != null ? vs.getResolution() : "";
            String digits = res.replaceAll("[^0-9]", "");
            // "720p" -> 720, "1080p60" -> 108060 -> lấy 4 số đầu = 1080
            if (digits.length() > 4) digits = digits.substring(0, 4);
            height = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Exception ignored) {
            height = 0;
        }
        int fps = 30;
        try {
            fps = vs.getFps();
            if (fps <= 0) fps = 30;
        } catch (Exception ignored) {
            fps = 30;
        }
        String codec = "";
        try {
            codec = vs.getCodec() != null ? vs.getCodec() : "";
        } catch (Exception ignored) {
            codec = "";
        }
        boolean progressive = true;
        try {
            progressive = !vs.isVideoOnly();
        } catch (Exception ignored) {
            progressive = true;
        }
        return new QualityPolicy.Stream(vs.getContent(), height, fps, codec, progressive);
    }

    /** 1 kết quả video: id + danh sách stream thô (chưa lọc). */
    public static class VideoItem {
        public final String videoId;
        public final String title;
        public final List<QualityPolicy.Stream> streams;

        public VideoItem(String videoId, String title, List<QualityPolicy.Stream> streams) {
            this.videoId = videoId;
            this.title = title;
            this.streams = streams;
        }
    }
}
