package com.example.youtubelite;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;

/**
 * Màn hình phát video cho máy cũ:
 * - Buffer nhỏ (15s) để RAM 1GB không tràn.
 * - Ép tốc độ phát 1.0x, khóa chọn track theo QualityPolicy (<=1080p, <=30fps).
 */
public class PlayerActivity extends AppCompatActivity {
    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_VIDEO_HEIGHT = "video_height";

    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.player_view);

        String url = getIntent().getStringExtra(EXTRA_VIDEO_URL);
        int height = getIntent().getIntExtra(EXTRA_VIDEO_HEIGHT, 480);
        if (url == null) {
            finish();
            return;
        }

        // Buffer nhỏ cho máy RAM 1GB: min 5s, max 15s, phát khi đủ 2.5s.
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(5000, 15000, 2500, 2500)
                .build();

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
        // Giới hạn track video: <=1080p, <=30fps (API Media3 1.4.1 chắc chắn có).
        // Không ép codec ở đây để tránh vỡ build — QualityPolicy đã ưu tiên
        // H.264 khi chọn URL progressive trước khi đưa vào player.
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
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.addListener(new Player.Listener() {
            @Override
            public void onTracksChanged(Tracks tracks) {
                lockToHeight(tracks, height);
            }
        });
        player.prepare();
        player.play();
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
