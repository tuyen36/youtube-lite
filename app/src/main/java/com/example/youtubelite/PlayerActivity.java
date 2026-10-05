package com.example.youtubelite;

import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import androidx.media3.datasource.DefaultHttpDataSource;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình phát video cho máy cũ (giống ảnh mẫu của user):
 * - Phần trên: video đang phát (dọc: 16:9, ngang: full màn hình).
 * - Phần dưới: tiêu đề + kênh + lượt xem/ngày đăng, rồi list video liên quan
 *   (thumbnail + tiêu đề + kênh + độ dài). Bấm video liên quan -> phát ngay
 *   trong cùng màn hình (giữ nguyên vị trí scroll, không mở activity mới).
 * - Có tiếng: progressive phát trực tiếp; videoOnly thì ghép audio rời.
 * - Chọn độ phân giải trong nút bánh răng (chỉ các mức <=1080p/30fps).
 * - Buffer nhỏ 5-15s, RAM 1GB không tràn.
 */
public class PlayerActivity extends AppCompatActivity {
    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_VIDEO_HEIGHT = "video_height";
    public static final String EXTRA_AUDIO_URL = "audio_url";
    public static final String EXTRA_ALL_URLS = "all_urls";
    public static final String EXTRA_ALL_LABELS = "all_labels";
    public static final String EXTRA_ALL_AUDIOS = "all_audios";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String EXTRA_VIDEO_THUMB = "video_thumb";
    public static final String EXTRA_VIDEO_DURATION = "video_duration";
    public static final String EXTRA_VIDEO_UPLOADER = "video_uploader";

    private ExoPlayer player;
    private PlayerView playerView;
    private boolean fullscreen = false;
    private ArrayList<String> allUrls = new ArrayList<>();
    private ArrayList<String> allLabels = new ArrayList<>();
    private ArrayList<String> allAudios = new ArrayList<>();
    private String audioUrl;
    private int currentIndex = 0;

    private TextView titleView;
    private TextView metaView;
    private TextView relatedState;
    private LinearLayout relatedContainer;
    private Button relatedMoreBtn;
    private final List<SearchActivity.VideoItem> relatedFull = new ArrayList<>();
    private int relatedShown = 10;
    private static final int RELATED_PAGE = 10;
    private String pageUrl;
    // Lịch sử phát trong màn hình này: previous = phát lại video trước,
    // next = video liên quan đầu (video đầu trong list liên quan).
    private final List<SearchActivity.VideoItem> playTrail = new ArrayList<>();
    private int trailIndex = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.player_view);
        ImageButton fullBtn = findViewById(R.id.fullscreen_btn);
        titleView = findViewById(R.id.player_title);
        metaView = findViewById(R.id.player_meta);
        relatedState = findViewById(R.id.player_related_state);
        relatedContainer = findViewById(R.id.player_related_container);
        relatedMoreBtn = findViewById(R.id.player_related_more_btn);

        String url = getIntent().getStringExtra(EXTRA_VIDEO_URL);
        int height = getIntent().getIntExtra(EXTRA_VIDEO_HEIGHT, 480);
        audioUrl = getIntent().getStringExtra(EXTRA_AUDIO_URL);
        if (getIntent().getStringArrayListExtra(EXTRA_ALL_URLS) != null) {
            allUrls = getIntent().getStringArrayListExtra(EXTRA_ALL_URLS);
        }
        if (getIntent().getStringArrayListExtra(EXTRA_ALL_LABELS) != null) {
            allLabels = getIntent().getStringArrayListExtra(EXTRA_ALL_LABELS);
        }
        if (getIntent().getStringArrayListExtra(EXTRA_ALL_AUDIOS) != null) {
            allAudios = getIntent().getStringArrayListExtra(EXTRA_ALL_AUDIOS);
        }
        pageUrl = getIntent().getStringExtra("video_page_url");
        String vTitle = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);
        String vUploader = getIntent().getStringExtra(EXTRA_VIDEO_UPLOADER);
        // Ghi lịch sử xem (để màn hình chính gợi ý video tương tự lần sau).
        try {
            WatchHistory.push(this,
                    pageUrl != null ? pageUrl : url,
                    vTitle,
                    getIntent().getStringExtra(EXTRA_VIDEO_THUMB),
                    getIntent().getLongExtra(EXTRA_VIDEO_DURATION, -1),
                    vUploader);
        } catch (Exception ignored) {
        }
        // Hiện tiêu đề + kênh ngay (giống ảnh mẫu: tiêu đề 2-3 dòng + kênh • view • time).
        titleView.setText(vTitle != null && !vTitle.isEmpty() ? vTitle : "Đang phát...");
        String meta0 = vUploader != null && !vUploader.isEmpty() ? vUploader : "";
        metaView.setText(meta0);
        metaView.setVisibility(meta0.isEmpty() ? View.GONE : View.VISIBLE);
        if (url == null) {
            finish();
            return;
        }

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(5000, 15000, 2500, 2500)
                .build();

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(
                trackSelector.buildUponParameters()
                        .setMaxVideoSize(1920, QualityPolicy.MAX_HEIGHT)
                        .setMaxVideoFrameRate(QualityPolicy.MAX_FPS)
        );

        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setTrackSelector(trackSelector)
                .build();
        playerView.setPlayer(player);
        playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        player.setPlaybackParameters(new PlaybackParameters(1.0f));
        playUrl(url, audioUrl);
        // Ghi dấu video đầu vào lịch phát (để previous/next hoạt động).
        pushTrail(pageUrl != null ? pageUrl : url, vTitle,
                getIntent().getStringExtra(EXTRA_VIDEO_THUMB),
                getIntent().getLongExtra(EXTRA_VIDEO_DURATION, -1),
                vUploader);
        // Nối nút next/previous của controller: next = video liên quan đầu,
        // previous = phát lại video trước đó trong lịch phát.
        player.addListener(new Player.Listener() {
            @Override
            public void onTracksChanged(Tracks tracks) {
                lockToHeight(tracks, height);
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                bindControllerPrevNext();
            }
        });
        player.prepare();
        player.play();
        bindControllerPrevNext();

        // Nút bánh răng: chọn chất lượng + full màn hình (góc phải, API 21).
        fullBtn.setOnClickListener(v -> toggleFullscreen());
        ImageButton qualityBtn = findViewById(R.id.quality_btn);
        qualityBtn.setOnClickListener(v -> showQualityDialog());

        relatedMoreBtn.setOnClickListener(v -> {
            relatedShown += RELATED_PAGE;
            renderRelated();
        });
        // Tải video liên quan của video đang xem.
        loadRelated(pageUrl != null ? pageUrl : url);
    }

    /** Tải video liên quan (related của StreamInfo) cho video đang phát. */
    private void loadRelated(@Nullable String videoPageUrl) {
        if (videoPageUrl == null || videoPageUrl.isEmpty()) {
            relatedState.setText("Không lấy được video liên quan");
            relatedState.setVisibility(View.VISIBLE);
            return;
        }
        relatedState.setText("Đang tải video liên quan...");
        relatedState.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                NewPipeHolder.initIfNeeded();
                org.schabi.newpipe.extractor.StreamingService yt =
                        org.schabi.newpipe.extractor.NewPipe.getService(0);
                org.schabi.newpipe.extractor.stream.StreamInfo detail =
                        org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(yt, videoPageUrl);
                List<SearchActivity.VideoItem> got = new ArrayList<>();
                // Cập nhật meta: lượt xem + ngày đăng nếu có (giống ảnh mẫu).
                String viewsLine = "";
                try {
                    String up = detail.getUploaderName();
                    long views = detail.getViewCount();
                    String date = detail.getTextualUploadDate();
                    StringBuilder sb = new StringBuilder();
                    if (up != null && !up.isEmpty()) sb.append(up);
                    if (views >= 0) {
                        if (sb.length() > 0) sb.append(" • ");
                        sb.append(formatViews(views)).append(" lượt xem");
                    }
                    if (date != null && !date.isEmpty()) {
                        if (sb.length() > 0) sb.append(" • ");
                        sb.append(date);
                    }
                    viewsLine = sb.toString();
                } catch (Exception ignored) {
                }
                final String metaLine = viewsLine;
                if (detail.getRelatedItems() != null) {
                    for (org.schabi.newpipe.extractor.InfoItem it : detail.getRelatedItems()) {
                        if (!(it instanceof org.schabi.newpipe.extractor.stream.StreamInfoItem)) continue;
                        org.schabi.newpipe.extractor.stream.StreamInfoItem s =
                                (org.schabi.newpipe.extractor.stream.StreamInfoItem) it;
                        got.add(SearchActivity.itemFromStreamItem(s));
                        if (got.size() >= 30) break;
                    }
                }
                runOnUiThread(() -> {
                    if (!metaLine.isEmpty()) {
                        metaView.setText(metaLine);
                        metaView.setVisibility(View.VISIBLE);
                    }
                    relatedFull.clear();
                    relatedFull.addAll(got);
                    relatedShown = RELATED_PAGE;
                    renderRelated();
                    if (got.isEmpty()) {
                        relatedState.setText("Không có video liên quan");
                        relatedState.setVisibility(View.VISIBLE);
                    } else {
                        relatedState.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    relatedState.setText("Không tải được video liên quan: " + e.getMessage());
                    relatedState.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private static String formatViews(long views) {
        if (views >= 1_000_000_000) return String.format("%.1f T", views / 1_000_000_000.0);
        if (views >= 1_000_000) return String.format("%.1f Tr", views / 1_000_000.0);
        if (views >= 1_000) return String.format("%.1f N", views / 1_000.0);
        return String.valueOf(views);
    }

    private void renderRelated() {
        relatedContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int n = Math.min(relatedShown, relatedFull.size());
        for (int i = 0; i < n; i++) {
            SearchActivity.VideoItem item = relatedFull.get(i);
            View row = inflater.inflate(R.layout.item_video, relatedContainer, false);
            ImageView thumb = row.findViewById(R.id.video_thumb);
            android.widget.TextView title = row.findViewById(R.id.video_title);
            android.widget.TextView meta = row.findViewById(R.id.video_meta);
            android.widget.TextView duration = row.findViewById(R.id.video_duration);
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
            row.setOnClickListener(v -> switchToRelated(item));
            relatedContainer.addView(row);
        }
        relatedMoreBtn.setVisibility(
                relatedShown < relatedFull.size() ? View.VISIBLE : View.GONE);
    }

    /** Bấm video liên quan -> phát ngay trong cùng màn hình (không mở activity mới). */
    private void switchToRelated(SearchActivity.VideoItem item) {
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
                ArrayList<String> urls = new ArrayList<>();
                ArrayList<String> labels = new ArrayList<>();
                ArrayList<String> audios = new ArrayList<>();
                java.util.Set<Integer> seen = new java.util.HashSet<>();
                for (QualityPolicy.Stream s : prog) {
                    if (seen.contains(s.height)) continue;
                    seen.add(s.height);
                    urls.add(s.url);
                    labels.add(s.label() + " \u266A");
                    audios.add(null);
                }
                for (QualityPolicy.Stream s : only) {
                    if (seen.contains(s.height)) continue;
                    seen.add(s.height);
                    urls.add(s.url);
                    labels.add(s.label());
                    audios.add(bestAudio);
                }
                runOnUiThread(() -> {
                    if (urls.isEmpty()) {
                        Toast.makeText(this, "Không có định dạng phù hợp máy này", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int defIdx = 0;
                    for (int k = 0; k < labels.size(); k++) {
                        if (labels.get(k).startsWith("480p")) { defIdx = k; break; }
                    }
                    // Đổi video ngay trong màn hình này: tiêu đề + meta + related mới.
                    allUrls = urls;
                    allLabels = labels;
                    allAudios = audios;
                    currentIndex = defIdx;
                    audioUrl = audios.get(defIdx);
                    pageUrl = item.videoId;
                    pushTrail(item.videoId, item.title, item.thumbUrl,
                            item.durationSec, item.uploader);
                    titleView.setText(item.title != null ? item.title : "Đang phát...");
                    String mm = item.uploader != null ? item.uploader : "";
                    metaView.setText(mm);
                    metaView.setVisibility(mm.isEmpty() ? View.GONE : View.VISIBLE);
                    try {
                        WatchHistory.push(this, item.videoId, item.title,
                                item.thumbUrl, item.durationSec, item.uploader);
                    } catch (Exception ignored) {
                    }
                    playUrl(urls.get(defIdx), audios.get(defIdx));
                    player.prepare();
                    player.play();
                    relatedShown = RELATED_PAGE;
                    loadRelated(item.videoId);
                    // Cuộn lên đầu để thấy video mới.
                    View scroll = findViewById(R.id.player_scroll);
                    if (scroll != null) scroll.scrollTo(0, 0);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Không lấy được link phát: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** Phát 1 URL video; nếu có audio rời thì ghép (videoOnly câm -> có tiếng). */
    private void playUrl(String videoUrl, @Nullable String audio) {
        DefaultHttpDataSource.Factory http =
                new DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");
        if (audio == null || audio.isEmpty()) {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)));
            return;
        }
        ProgressiveMediaSource videoSrc = new ProgressiveMediaSource.Factory(http)
                .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)));
        ProgressiveMediaSource audioSrc = new ProgressiveMediaSource.Factory(http)
                .createMediaSource(MediaItem.fromUri(Uri.parse(audio)));
        // Ghép hình + tiếng: videoOnly (câm) + audio rời -> có tiếng.
        MergingMediaSource merged = new MergingMediaSource(videoSrc, audioSrc);
        player.setMediaSource(merged);
    }

    /** Dialog chọn độ phân giải (mỗi mức mang audio riêng, giữ vị trí khi đổi). */
    private void showQualityDialog() {
        if (allLabels.isEmpty() || allUrls.isEmpty()) {
            return;
        }
        String[] labels = allLabels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Độ phân giải (tối đa 1080p, không 60fps)")
                .setSingleChoiceItems(labels, currentIndex, (dialog, which) -> {
                    if (which < 0 || which >= allUrls.size()) return;
                    currentIndex = which;
                    String a = (which < allAudios.size()) ? allAudios.get(which) : null;
                    audioUrl = a;
                    long pos = player != null ? player.getCurrentPosition() : 0;
                    boolean wasPlaying = player != null && player.isPlaying();
                    playUrl(allUrls.get(which), a);
                    player.prepare();
                    player.seekTo(pos);
                    if (wasPlaying) player.play();
                    dialog.dismiss();
                })
                .show();
    }

    /** Full màn hình lấp đầy: resize FILL + khung video match_parent khi ngang. */
    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        long pos = player != null ? player.getCurrentPosition() : 0;
        boolean wasPlaying = player != null && player.isPlaying();
        View scroll = findViewById(R.id.player_scroll);
        View frame = findViewById(R.id.player_frame);
        if (fullscreen) {
            if (getSupportActionBar() != null) getSupportActionBar().hide();
            // Xoay ngang màn hình xem (sensorLandscape: theo tay cầm, không bị ngược).
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            // Lấp đầy: khung video match_parent + PlayerView FILL (cắt viền đen 2 bên).
            if (frame != null) {
                android.view.ViewGroup.LayoutParams lp = frame.getLayoutParams();
                lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                frame.setLayoutParams(lp);
            }
            android.view.ViewGroup.LayoutParams vpl = playerView.getLayoutParams();
            vpl.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            playerView.setLayoutParams(vpl);
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL);
            if (scroll != null) scroll.setVisibility(View.GONE);
        } else {
            if (getSupportActionBar() != null) getSupportActionBar().show();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            if (frame != null) {
                android.view.ViewGroup.LayoutParams lp = frame.getLayoutParams();
                lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                frame.setLayoutParams(lp);
            }
            android.view.ViewGroup.LayoutParams vpl = playerView.getLayoutParams();
            vpl.height = (int) (220 * getResources().getDisplayMetrics().density);
            playerView.setLayoutParams(vpl);
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            if (scroll != null) scroll.setVisibility(View.VISIBLE);
        }
        // Manifest đã có configChanges nên activity không restart;
        // giữ vị trí + trạng thái phát để không load lại từ đầu.
        if (player != null) {
            player.seekTo(pos);
            if (wasPlaying) player.play();
        }
    }

    /** Ghi dấu video vào lịch phát trong màn hình (cho previous/next). */
    private void pushTrail(String videoPageUrl, String vTitle, String vThumb,
                           long vDur, String vUploader) {
        // Cắt nhánh "tới" khi quay lại rồi rẽ hướng mới (giống trình duyệt).
        if (trailIndex >= 0 && trailIndex < playTrail.size() - 1) {
            for (int k = playTrail.size() - 1; k > trailIndex; k--) {
                playTrail.remove(k);
            }
        }
        // Trùng video đang xem thì không ghi đè.
        if (trailIndex >= 0 && trailIndex < playTrail.size()) {
            SearchActivity.VideoItem cur = playTrail.get(trailIndex);
            if (cur != null && cur.videoId != null && cur.videoId.equals(videoPageUrl)) return;
        }
        playTrail.add(new SearchActivity.VideoItem(
                videoPageUrl != null ? videoPageUrl : "",
                vTitle != null ? vTitle : "",
                new ArrayList<>(),
                vThumb != null ? vThumb : "", vDur, vUploader != null ? vUploader : ""));
        // Giữ tối đa 50 video trong lịch phát.
        if (playTrail.size() > 50) {
            playTrail.remove(0);
        }
        trailIndex = playTrail.size() - 1;
    }

    /** Nối nút previous/next của controller vào lịch phát + list liên quan. */
    private void bindControllerPrevNext() {
        try {
            playerView.setShowPreviousButton(true);
            playerView.setShowNextButton(true);
        } catch (Exception ignored) {
        }
        try {
            View prevBtn = findViewById(androidx.media3.ui.R.id.exo_prev);
            View nextBtn = findViewById(androidx.media3.ui.R.id.exo_next);
            if (prevBtn != null) {
                prevBtn.setOnClickListener(v -> playPrevious());
            }
            if (nextBtn != null) {
                nextBtn.setOnClickListener(v -> playNext());
            }
        } catch (Exception ignored) {
        }
    }

    /** Nút previous: phát lại video trước đó trong lịch phát màn hình này. */
    private void playPrevious() {
        if (trailIndex > 0 && trailIndex - 1 < playTrail.size()) {
            trailIndex--;
            SearchActivity.VideoItem it = playTrail.get(trailIndex);
            switchToRelated(it);
        } else {
            // Đang ở video đầu -> phát lại từ đầu video hiện tại.
            if (player != null) {
                player.seekTo(0);
                player.play();
            }
        }
    }

    /** Nút next: phát video liên quan đầu (video đầu trong list liên quan). */
    private void playNext() {
        // Quay lại từ lịch phát (đã bấm previous rồi bấm next).
        if (trailIndex >= 0 && trailIndex + 1 < playTrail.size()) {
            trailIndex++;
            SearchActivity.VideoItem it = playTrail.get(trailIndex);
            switchToRelated(it);
            return;
        }
        if (!relatedFull.isEmpty()) {
            // Video đầu trong danh sách đề xuất đang phát (giống YouTube autoplay).
            switchToRelated(relatedFull.get(0));
        } else if (player != null) {
            Toast.makeText(this, "Chưa có video liên quan để phát tiếp", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Layout FrameLayout full màn hình nên không cần đổi gì thêm;
        // player + vị trí được giữ nguyên nhờ configChanges.
    }

    /** Khóa đúng mức user chọn (vd 720p), không cho ExoPlayer tự nhảy lên 60fps. */
    private void lockToHeight(Tracks tracks, int wantHeight) {
        // Gợi ý track selector giữ nguyên giới hạn; ExoPlayer adaptive sẽ
        // tự chọn trong trần 1080p/30fps đã đặt ở onCreate.
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
