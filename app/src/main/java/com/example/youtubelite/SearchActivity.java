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
            // Bấm vào mới lấy StreamInfo + lọc 1080p/30fps (chạy nền để không treo UI).
            Toast.makeText(this, "Đang lấy link phát...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    NewPipeHolder.initIfNeeded();
                    org.schabi.newpipe.extractor.StreamingService yt2 =
                            org.schabi.newpipe.extractor.NewPipe.getService(0);
                    org.schabi.newpipe.extractor.stream.StreamInfo detail =
                            org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(yt2, item.videoId);
                    List<QualityPolicy.Stream> raw = new ArrayList<>();
                    for (org.schabi.newpipe.extractor.stream.VideoStream vs : detail.getVideoStreams()) {
                        raw.add(mapVideoStream(vs));
                    }
                    for (org.schabi.newpipe.extractor.stream.VideoStream vs : detail.getVideoOnlyStreams()) {
                        raw.add(mapVideoStream(vs));
                    }
                    List<QualityPolicy.Stream> ok = QualityPolicy.filter(raw);
                    QualityPolicy.Stream pick = QualityPolicy.pickDefault(ok);
                    runOnUiThread(() -> {
                        if (pick == null) {
                            Toast.makeText(this, "Không có định dạng phù hợp máy này", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Intent i = new Intent(this, PlayerActivity.class);
                        i.putExtra(PlayerActivity.EXTRA_VIDEO_URL, pick.url);
                        i.putExtra(PlayerActivity.EXTRA_VIDEO_HEIGHT, pick.height);
                        startActivity(i);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Không lấy được link phát: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
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
                    // Hiện kết quả NGAY, chưa lấy stream chi tiết (nhanh + không trống list).
                    // Bấm vào mới lấy StreamInfo + lọc 1080p/30fps (xem onItemClick).
                    found.add(new VideoItem(url, title, new ArrayList<>()));
                    foundTitles.add(title);
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
            height = vs.getHeight(); // API thật: int, không parse chuỗi
            if (height <= 0) {
                String res = vs.getResolution() != null ? vs.getResolution() : "";
                java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile("(\\d{3,4})\\s*p").matcher(res);
                if (m.find()) height = Integer.parseInt(m.group(1));
            }
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
