package com.example.youtubelite;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình tìm kiếm: hiện thumbnail + độ dài + kênh, tối đa 50 kết quả.
 * Đã nối NewPipeExtractor: tìm thật, lọc 1080p/30fps qua QualityPolicy.
 */
public class SearchActivity extends AppCompatActivity {
    private EditText queryInput;
    private ListView resultList;
    private final List<VideoItem> items = new ArrayList<>();
    private VideoAdapter adapter;

    private void openPlayer(String videoUrl, int height, @Nullable String audioUrl,
                            ArrayList<String> allUrls, ArrayList<String> allLabels) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_VIDEO_URL, videoUrl);
        i.putExtra(PlayerActivity.EXTRA_VIDEO_HEIGHT, height);
        if (audioUrl != null) i.putExtra(PlayerActivity.EXTRA_AUDIO_URL, audioUrl);
        i.putStringArrayListExtra(PlayerActivity.EXTRA_ALL_URLS, allUrls);
        i.putStringArrayListExtra(PlayerActivity.EXTRA_ALL_LABELS, allLabels);
        startActivity(i);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        queryInput = findViewById(R.id.query_input);
        Button searchBtn = findViewById(R.id.search_btn);
        resultList = findViewById(R.id.result_list);

        adapter = new VideoAdapter(this, items);
        resultList.setAdapter(adapter);
        resultList.setOnItemClickListener((parent, view, position, id) -> {
            VideoItem item = items.get(position);
            // Bấm vào mới lấy StreamInfo + lọc 1080p/30fps (chạy nền để không treo UI).
            // Ưu tiên stream CÓ TIẾNG (progressive); chỉ dùng videoOnly khi không còn cách nào.
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
                    List<QualityPolicy.Stream> prog = QualityPolicy.filter(raw);
                    QualityPolicy.Stream pick = QualityPolicy.pickDefault(prog);
                    String audioUrl = null;
                    try {
                        if (detail.getAudioStreams() != null && !detail.getAudioStreams().isEmpty()) {
                            audioUrl = detail.getAudioStreams().get(0).getContent();
                        }
                    } catch (Exception ignored) {
                        audioUrl = null;
                    }
                    if (pick == null) {
                        // Không còn progressive phù hợp -> dùng videoOnly tốt nhất + audio rời.
                        List<QualityPolicy.Stream> only = new ArrayList<>();
                        for (org.schabi.newpipe.extractor.stream.VideoStream vs : detail.getVideoOnlyStreams()) {
                            only.add(mapVideoStream(vs));
                        }
                        List<QualityPolicy.Stream> okOnly = QualityPolicy.filter(only);
                        QualityPolicy.Stream vpick = QualityPolicy.pickDefault(okOnly);
                        final QualityPolicy.Stream fpick = vpick;
                        final String faudio = audioUrl;
                        final ArrayList<String> allUrls = new ArrayList<>();
                        final ArrayList<String> allLabels = new ArrayList<>();
                        for (QualityPolicy.Stream s2 : okOnly) {
                            allUrls.add(s2.url);
                            allLabels.add(s2.label());
                        }
                        runOnUiThread(() -> {
                            if (fpick == null) {
                                Toast.makeText(this, "Không có định dạng phù hợp máy này", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            openPlayer(fpick.url, fpick.height, faudio, allUrls, allLabels);
                        });
                        return;
                    }
                    final QualityPolicy.Stream fpick2 = pick;
                    final ArrayList<String> allUrls2 = new ArrayList<>();
                    final ArrayList<String> allLabels2 = new ArrayList<>();
                    for (QualityPolicy.Stream s3 : prog) {
                        allUrls2.add(s3.url);
                        allLabels2.add(s3.label());
                    }
                    runOnUiThread(() -> openPlayer(fpick2.url, fpick2.height, null, allUrls2, allLabels2));
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
                for (org.schabi.newpipe.extractor.InfoItem it : info.getRelatedItems()) {
                    if (!(it instanceof org.schabi.newpipe.extractor.stream.StreamInfoItem)) continue;
                    org.schabi.newpipe.extractor.stream.StreamInfoItem s =
                            (org.schabi.newpipe.extractor.stream.StreamInfoItem) it;
                    String url = s.getUrl();
                    String title = s.getName() != null ? s.getName() : url;
                    // Thumbnail + độ dài + kênh để hiện list đẹp.
                    String thumb = "";
                    try {
                        if (s.getThumbnails() != null && !s.getThumbnails().isEmpty()) {
                            thumb = s.getThumbnails().get(0).getUrl();
                        }
                    } catch (Exception ignored) {
                        thumb = "";
                    }
                    long dur = -1;
                    try {
                        dur = s.getDuration();
                    } catch (Exception ignored) {
                        dur = -1;
                    }
                    String uploader = "";
                    try {
                        uploader = s.getUploaderName() != null ? s.getUploaderName() : "";
                    } catch (Exception ignored) {
                        uploader = "";
                    }
                    // Hiện kết quả NGAY, chưa lấy stream chi tiết (nhanh + không trống list).
                    // Bấm vào mới lấy StreamInfo + lọc 1080p/30fps (xem onItemClick).
                    found.add(new VideoItem(url, title, new ArrayList<>(), thumb, dur, uploader));
                    if (found.size() >= 50) break; // nâng lên 50 kết quả
                }
                runOnUiThread(() -> {
                    items.clear();
                    items.addAll(found);
                    adapter.notifyDataSetChanged();
                    if (found.isEmpty()) {
                        Toast.makeText(this, "Không tìm thấy video nào, thử từ khóa khác", Toast.LENGTH_LONG).show();
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

    /** 1 kết quả video: id + thumbnail + độ dài + kênh + stream (lấy sau khi bấm). */
    public static class VideoItem {
        public final String videoId;
        public final String title;
        public final List<QualityPolicy.Stream> streams;
        public String thumbUrl;
        public long durationSec;
        public String uploader;

        public VideoItem(String videoId, String title, List<QualityPolicy.Stream> streams) {
            this(videoId, title, streams, "", -1, "");
        }

        public VideoItem(String videoId, String title, List<QualityPolicy.Stream> streams,
                         String thumbUrl, long durationSec, String uploader) {
            this.videoId = videoId;
            this.title = title;
            this.streams = streams;
            this.thumbUrl = thumbUrl != null ? thumbUrl : "";
            this.durationSec = durationSec;
            this.uploader = uploader != null ? uploader : "";
        }

        public String durationLabel() {
            if (durationSec < 0) return "";
            long m = durationSec / 60, s = durationSec % 60;
            if (m >= 60) return String.format("%d:%02d:%02d", m / 60, m % 60, s);
            return String.format("%d:%02d", m, s);
        }
    }
}
