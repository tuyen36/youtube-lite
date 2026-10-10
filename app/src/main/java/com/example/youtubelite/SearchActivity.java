package com.example.youtubelite;

import android.content.Intent;
import android.os.Bundle;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình tìm kiếm: hiện thumbnail + độ dài + kênh, tải thêm khi kéo xuống.
 * Đã nối NewPipeExtractor: tìm thật, lọc 1080p/30fps qua QualityPolicy.
 */
public class SearchActivity extends AppCompatActivity {
    public static final String EXTRA_QUERY = "query";
    private EditText queryInput;
    private ListView resultList;
    private final List<VideoItem> items = new ArrayList<>();
    private VideoAdapter adapter;

    // Phân trang tìm kiếm: giữ extractor + trang kế tiếp.
    private String currentQuery = "";
    private org.schabi.newpipe.extractor.search.SearchExtractor searchExtractor;
    private org.schabi.newpipe.extractor.Page nextPage;
    private boolean loadingMore = false;

    private void openPlayer(SearchActivity.VideoItem item, String videoUrl, int height,
                            @Nullable String audioUrl,
                            ArrayList<String> allUrls, ArrayList<String> allLabels,
                            ArrayList<String> allAudios) {
        openPlayer(this, item, videoUrl, height, audioUrl, allUrls, allLabels, allAudios);
    }

    static void openPlayer(android.content.Context ctx, SearchActivity.VideoItem item,
                           String videoUrl, int height, @Nullable String audioUrl,
                           ArrayList<String> allUrls, ArrayList<String> allLabels,
                           ArrayList<String> allAudios) {
        Intent i = new Intent(ctx, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_VIDEO_URL, videoUrl);
        i.putExtra(PlayerActivity.EXTRA_VIDEO_HEIGHT, height);
        if (audioUrl != null) i.putExtra(PlayerActivity.EXTRA_AUDIO_URL, audioUrl);
        i.putStringArrayListExtra(PlayerActivity.EXTRA_ALL_URLS, allUrls);
        i.putStringArrayListExtra(PlayerActivity.EXTRA_ALL_LABELS, allLabels);
        i.putStringArrayListExtra(PlayerActivity.EXTRA_ALL_AUDIOS, allAudios);
        // Thông tin video để ghi lịch sử xem (gợi ý lần sau).
        i.putExtra("video_page_url", item.videoId);
        i.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, item.title);
        i.putExtra(PlayerActivity.EXTRA_VIDEO_THUMB, item.thumbUrl);
        i.putExtra(PlayerActivity.EXTRA_VIDEO_DURATION, item.durationSec);
        i.putExtra(PlayerActivity.EXTRA_VIDEO_UPLOADER, item.uploader);
        i.putExtra(PlayerActivity.EXTRA_UPLOADER_URL, item.uploaderUrl);
        if (!(ctx instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
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
        // Giu lau 1 video -> mo trang kenh cua video do (avatar + tab Video/Playlist).
        resultList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= items.size()) return true;
            VideoItem it = items.get(position);
            if (it.uploaderUrl != null && !it.uploaderUrl.isEmpty()) {
                ChannelActivity.open(this, it.uploaderUrl, it.uploader);
            } else {
                Toast.makeText(this, "Video này chưa có link kênh",
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        // 2.8: Enter trên bàn phím điện thoại để tìm (không cần bấm nút Tìm).
        queryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO
                    || (event != null
                        && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                doSearch(queryInput.getText().toString().trim());
                return true;
            }
            return false;
        });
        // Mở từ màn hình chính (HomeActivity) có kèm từ khóa -> tìm luôn.
        String startQuery = getIntent().getStringExtra(EXTRA_QUERY);
        if (startQuery != null && !startQuery.trim().isEmpty()) {
            queryInput.setText(startQuery.trim());
            resultList.post(() -> doSearch(startQuery.trim()));
        }
        resultList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= items.size()) return;
            VideoItem item = items.get(position);
            // Bấm vào mới lấy StreamInfo + lọc 1080p/30fps (chạy nền để không treo UI).
            // Gộp progressive (có tiếng) + videoOnly (ghép audio rời) -> nhiều mức để chọn.
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
                    // 1) progressive (có sẵn tiếng) — ưu tiên vì máy yếu phát nhẹ nhất.
                    // 2.7: giu link kenh de man phat bam ten kenh mo trang kenh.
                    try {
                        String cu = detail.getUploaderUrl();
                        if (cu != null && !cu.isEmpty()) item.uploaderUrl = cu;
                    } catch (Exception ignored) {
                    }
                    List<QualityPolicy.Stream> rawProg = new ArrayList<>();
                    for (org.schabi.newpipe.extractor.stream.VideoStream vs : detail.getVideoStreams()) {
                        rawProg.add(mapVideoStream(vs));
                    }
                    List<QualityPolicy.Stream> prog = QualityPolicy.filter(rawProg);
                    // 2) videoOnly (câm) + audio rời — mở thêm các mức 480/720/1080.
                    List<QualityPolicy.Stream> rawOnly = new ArrayList<>();
                    for (org.schabi.newpipe.extractor.stream.VideoStream vs : detail.getVideoOnlyStreams()) {
                        rawOnly.add(mapVideoStream(vs));
                    }
                    List<QualityPolicy.Stream> only = QualityPolicy.filter(rawOnly);

                    // Gộp: progressive trước, videoOnly sau, khử trùng theo height.
                    ArrayList<String> allUrls = new ArrayList<>();
                    ArrayList<String> allLabels = new ArrayList<>();
                    ArrayList<String> allAudios = new ArrayList<>();
                    java.util.Set<Integer> seenHeights = new java.util.HashSet<>();
                    for (QualityPolicy.Stream s : prog) {
                        if (seenHeights.contains(s.height)) continue;
                        seenHeights.add(s.height);
                        allUrls.add(s.url);
                        allLabels.add(s.label() + " ♪");
                        allAudios.add(null); // progressive đã có tiếng
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
                        // Mac dinh K016: 360p co tieng truoc, roi 360p, roi 480p.
                        int defIdx = PlayerActivity.pickDefaultIndex(allLabels);
                        String a0 = allAudios.get(defIdx);
                        // Lấy height từ label "720p" để hiện đúng.
                        int h0 = 360;
                        try {
                            String d = allLabels.get(defIdx).replaceAll("[^0-9]", "");
                            if (d.length() > 4) d = d.substring(0, 4);
                            h0 = Integer.parseInt(d);
                        } catch (Exception ignored) {
                            h0 = 360;
                        }
                        openPlayer(item, allUrls.get(defIdx), h0, a0, allUrls, allLabels, allAudios);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Không lấy được link phát: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        });

        // Kéo xuống đáy list -> tải thêm trang kế tiếp.
        resultList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisible, int visibleCount, int totalCount) {
                if (totalCount == 0 || loadingMore) return;
                if (nextPage == null) return;
                if (firstVisible + visibleCount >= totalCount - 4) {
                    loadMore();
                }
            }
        });

        searchBtn.setOnClickListener(v -> doSearch(queryInput.getText().toString().trim()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderSearchHistory();
    }

    /** Vẽ các từ khóa đã tìm: bấm để tìm lại, giữ lâu để xóa. */
    private void renderSearchHistory() {
        android.view.View title = findViewById(R.id.search_history_title);
        android.view.View scroll = findViewById(R.id.search_history_scroll);
        android.widget.LinearLayout box = findViewById(R.id.search_history_container);
        if (title == null || scroll == null || box == null) return;
        List<String> queries = SearchHistory.list(this);
        box.removeAllViews();
        if (queries.isEmpty()) {
            title.setVisibility(android.view.View.GONE);
            scroll.setVisibility(android.view.View.GONE);
            return;
        }
        title.setVisibility(android.view.View.VISIBLE);
        scroll.setVisibility(android.view.View.VISIBLE);
        for (String q : queries) {
            android.widget.Button b = new android.widget.Button(this);
            b.setText(q);
            b.setTextSize(12);
            b.setAllCaps(false);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 12, 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                queryInput.setText(q);
                doSearch(q);
            });
            b.setOnLongClickListener(v -> {
                SearchHistory.remove(this, q);
                renderSearchHistory();
                Toast.makeText(this, "Đã xóa: " + q, Toast.LENGTH_SHORT).show();
                return true;
            });
            box.addView(b);
        }
    }

    private void doSearch(String q) {
        if (q.isEmpty()) {
            Toast.makeText(this, "Nhập từ khóa tìm kiếm", Toast.LENGTH_SHORT).show();
            return;
        }
        // 2.8: luu tu khoa + ve lai lich su de bam lai lan sau.
        SearchHistory.push(this, q);
        renderSearchHistory();
        // Tìm thật bằng NewPipeExtractor trên luồng nền (cấm chạy mạng trên UI thread).
        Toast.makeText(this, "Đang tìm: " + q, Toast.LENGTH_SHORT).show();
        currentQuery = q;
        nextPage = null;
        searchExtractor = null;
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
                // Giữ lại để tải thêm.
                searchExtractor = extractor;
                try {
                    nextPage = info.getNextPage();
                } catch (Exception ignored) {
                    nextPage = null;
                }
                List<VideoItem> found = itemsFromInfo(info);
                runOnUiThread(() -> {
                    items.clear();
                    items.addAll(found);
                    adapter.notifyDataSetChanged();
                    if (found.isEmpty()) {
                        Toast.makeText(this, "Không tìm thấy video nào, thử từ khóa khác", Toast.LENGTH_LONG).show();
                    } else if (nextPage != null) {
                        Toast.makeText(this, "Kéo xuống để tải thêm", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Tìm kiếm lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** Tải thêm trang kế tiếp khi kéo xuống đáy (tối đa ~200 video). */
    private void loadMore() {
        if (loadingMore || nextPage == null || searchExtractor == null) return;
        loadingMore = true;
        Toast.makeText(this, "Đang tải thêm...", Toast.LENGTH_SHORT).show();
        final org.schabi.newpipe.extractor.Page page = nextPage;
        new Thread(() -> {
            try {
                org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage<
                        org.schabi.newpipe.extractor.InfoItem> next =
                        searchExtractor.getPage(page);
                List<VideoItem> more = new ArrayList<>();
                for (org.schabi.newpipe.extractor.InfoItem it : next.getItems()) {
                    if (!(it instanceof org.schabi.newpipe.extractor.stream.StreamInfoItem)) continue;
                    more.add(itemFromStreamItem(
                            (org.schabi.newpipe.extractor.stream.StreamInfoItem) it));
                }
                org.schabi.newpipe.extractor.Page following = null;
                try {
                    following = next.getNextPage();
                } catch (Exception ignored) {
                    following = null;
                }
                final org.schabi.newpipe.extractor.Page fNext = following;
                runOnUiThread(() -> {
                    loadingMore = false;
                    nextPage = fNext;
                    if (!more.isEmpty() && items.size() < 200) {
                        items.addAll(more);
                        adapter.notifyDataSetChanged();
                    }
                    if (fNext == null) {
                        Toast.makeText(this, "Đã hết kết quả", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingMore = false;
                    Toast.makeText(this, "Tải thêm lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    static VideoItem itemFromStreamItem(
            org.schabi.newpipe.extractor.stream.StreamInfoItem s) {
        String url = s.getUrl();
        String title = s.getName() != null ? s.getName() : url;
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
        String uploaderUrl = "";
        try {
            uploaderUrl = s.getUploaderUrl() != null ? s.getUploaderUrl() : "";
        } catch (Exception ignored) {
            uploaderUrl = "";
        }
        // Ngày đăng (vd "3 ngày trước", "1 tháng trước").
        String uploadDate = "";
        try {
            String t = s.getTextualUploadDate();
            if (t != null) uploadDate = t;
        } catch (Exception ignored) {
            uploadDate = "";
        }
        return new VideoItem(url, title, new ArrayList<>(), thumb, dur, uploader, uploadDate, uploaderUrl);
    }

    private static List<VideoItem> itemsFromInfo(
            org.schabi.newpipe.extractor.search.SearchInfo info) {
        List<VideoItem> found = new ArrayList<>();
        for (org.schabi.newpipe.extractor.InfoItem it : info.getRelatedItems()) {
            if (!(it instanceof org.schabi.newpipe.extractor.stream.StreamInfoItem)) continue;
            found.add(itemFromStreamItem(
                    (org.schabi.newpipe.extractor.stream.StreamInfoItem) it));
            if (found.size() >= 50) break; // trang đầu 50 kết quả
        }
        return found;
    }

    /** Map VideoStream của NewPipeExtractor sang Stream của QualityPolicy. */
    static QualityPolicy.Stream mapVideoStream(
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

    /** 1 kết quả video: id + thumbnail + độ dài + kênh + ngày đăng + stream (lấy sau khi bấm). */
    public static class VideoItem {
        public final String videoId;
        public final String title;
        public final List<QualityPolicy.Stream> streams;
        public String thumbUrl;
        public long durationSec;
        public String uploader;
        public String uploadDate; // vd "3 ngày trước", "1 tháng trước"
        public String uploaderUrl; // link kenh de mo trang kenh

        public VideoItem(String videoId, String title, List<QualityPolicy.Stream> streams) {
            this(videoId, title, streams, "", -1, "", "", "");
        }

        public VideoItem(String videoId, String title, List<QualityPolicy.Stream> streams,
                         String thumbUrl, long durationSec, String uploader) {
            this(videoId, title, streams, thumbUrl, durationSec, uploader, "", "");
        }

        public VideoItem(String videoId, String title, List<QualityPolicy.Stream> streams,
                         String thumbUrl, long durationSec, String uploader, String uploadDate) {
            this(videoId, title, streams, thumbUrl, durationSec, uploader, uploadDate, "");
        }

        public VideoItem(String videoId, String title, List<QualityPolicy.Stream> streams,
                         String thumbUrl, long durationSec, String uploader, String uploadDate,
                         String uploaderUrl) {
            this.videoId = videoId;
            this.title = title;
            this.streams = streams;
            this.thumbUrl = thumbUrl != null ? thumbUrl : "";
            this.durationSec = durationSec;
            this.uploader = uploader != null ? uploader : "";
            this.uploadDate = uploadDate != null ? uploadDate : "";
            this.uploaderUrl = uploaderUrl != null ? uploaderUrl : "";
        }

        public String durationLabel() {
            if (durationSec < 0) return "";
            long m = durationSec / 60, s = durationSec % 60;
            if (m >= 60) return String.format("%d:%02d:%02d", m / 60, m % 60, s);
            return String.format("%d:%02d", m, s);
        }
    }
}
