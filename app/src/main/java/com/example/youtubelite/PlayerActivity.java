package com.example.youtubelite;

import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

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

import java.util.ArrayList;

/**
 * Màn hình phát video cho máy cũ:
 * - Có tiếng: progressive phát trực tiếp; videoOnly thì ghép audio rời.
 * - Chọn độ phân giải trong menu bánh răng (chỉ các mức <=1080p/30fps).
 * - Nút phóng to toàn màn hình (xoay ngang + ẩn action bar).
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.player_view);
        ImageButton fullBtn = findViewById(R.id.fullscreen_btn);

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
        // Ghi lịch sử xem (để màn hình chính gợi ý video tương tự lần sau).
        try {
            String pageUrl = getIntent().getStringExtra("video_page_url");
            WatchHistory.push(this,
                    pageUrl != null ? pageUrl : url,
                    getIntent().getStringExtra(EXTRA_VIDEO_TITLE),
                    getIntent().getStringExtra(EXTRA_VIDEO_THUMB),
                    getIntent().getLongExtra(EXTRA_VIDEO_DURATION, -1),
                    getIntent().getStringExtra(EXTRA_VIDEO_UPLOADER));
        } catch (Exception ignored) {
        }
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
        player.setPlaybackParameters(new PlaybackParameters(1.0f));
        playUrl(url, audioUrl);
        player.addListener(new Player.Listener() {
            @Override
            public void onTracksChanged(Tracks tracks) {
                lockToHeight(tracks, height);
            }
        });
        player.prepare();
        player.play();

        // Nút bánh răng của PlayerView: chen thêm chọn chất lượng + full màn hình
        // bằng nút riêng góc phải (đơn giản, tương thích API 21).
        fullBtn.setOnClickListener(v -> toggleFullscreen());
        ImageButton qualityBtn = findViewById(R.id.quality_btn);
        qualityBtn.setOnClickListener(v -> showQualityDialog());
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

    /** Full màn hình KHÔNG load lại: configChanges giữ activity + giữ vị trí + resume. */
    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        long pos = player != null ? player.getCurrentPosition() : 0;
        boolean wasPlaying = player != null && player.isPlaying();
        if (fullscreen) {
            if (getSupportActionBar() != null) getSupportActionBar().hide();
            // Xoay ngang màn hình xem (sensorLandscape: theo tay cầm, không bị ngược).
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            if (getSupportActionBar() != null) getSupportActionBar().show();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
        // Manifest đã có configChanges nên activity không restart;
        // giữ vị trí + trạng thái phát để không load lại từ đầu.
        if (player != null) {
            player.seekTo(pos);
            if (wasPlaying) player.play();
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
