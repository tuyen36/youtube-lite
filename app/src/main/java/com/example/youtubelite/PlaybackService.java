package com.example.youtubelite;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.media3.common.C;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

/**
 * Service chay nen giu app song khi tat man (K016 Android 5.0).
 * GIU PLAYER CHUNG o day: Activity chet / tat man player van song
 * (ban 2.1 player nam trong Activity nen Activity chet la mat nhac + mo lai tu dau).
 * Foreground + thong bao thuong truc -> he thong khong giet process.
 * Service giu CPU + wifi lock. API 21 OK.
 */
public class PlaybackService extends Service {
    private static final int NOTIF_ID = 1001;
    private static final String CHANNEL_ID = "playback";
    public static final String EXTRA_TITLE = "title";

    private static ExoPlayer sharedPlayer;
    private static String lastTitle = "";

    private android.os.PowerManager.WakeLock cpuLock;
    private android.net.wifi.WifiManager.WifiLock wifiLock;

    /** Lay player chung, tao moi neu chua co (cau hinh san cho K016). */
    public static synchronized ExoPlayer getOrCreatePlayer(Context ctx) {
        if (sharedPlayer == null) {
            Context app = ctx.getApplicationContext();
            DefaultLoadControl lc = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15000, 60000, 5000, 5000)
                    .build();
            DefaultTrackSelector ts = new DefaultTrackSelector(app);
            try {
                ts.setParameters(ts.buildUponParameters()
                        .setMaxVideoSize(1920, QualityPolicy.MAX_HEIGHT)
                        .setMaxVideoFrameRate(QualityPolicy.MAX_FPS));
            } catch (Exception ignored) {
            }
            ExoPlayer p = new ExoPlayer.Builder(app)
                    .setLoadControl(lc)
                    .setTrackSelector(ts)
                    .build();
            try {
                p.setWakeMode(C.WAKE_MODE_LOCAL);
            } catch (Exception ignored) {
            }
            try {
                p.setHandleAudioBecomingNoisy(true);
            } catch (Exception ignored) {
            }
            sharedPlayer = p;
        }
        return sharedPlayer;
    }

    public static synchronized void releasePlayer() {
        try {
            if (sharedPlayer != null) sharedPlayer.release();
        } catch (Exception ignored) {
        }
        sharedPlayer = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String t = intent.getStringExtra(EXTRA_TITLE);
            if (t != null && !t.isEmpty()) {
                lastTitle = t;
                try {
                    getSharedPreferences("playback", MODE_PRIVATE)
                            .edit().putString("last_title", t).apply();
                } catch (Exception ignored) {
                }
            }
        }
        if (lastTitle.isEmpty()) {
            try {
                String s = getSharedPreferences("playback", MODE_PRIVATE)
                        .getString("last_title", "");
                if (s != null) lastTitle = s;
            } catch (Exception ignored) {
            }
        }
        try {
            startForeground(NOTIF_ID, buildNotification(lastTitle));
        } catch (Exception ignored) {
        }
        acquireLocks();
        return START_STICKY;
    }

    private void acquireLocks() {
        try {
            if (cpuLock == null) {
                Object pmObj = getSystemService(Context.POWER_SERVICE);
                if (pmObj instanceof android.os.PowerManager) {
                    cpuLock = ((android.os.PowerManager) pmObj).newWakeLock(
                            android.os.PowerManager.PARTIAL_WAKE_LOCK, "YoutubeLite:cpu");
                    cpuLock.setReferenceCounted(false);
                }
            }
            if (cpuLock != null && !cpuLock.isHeld()) cpuLock.acquire();
        } catch (Exception ignored) {
        }
        try {
            if (wifiLock == null) {
                Object wmObj = getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wmObj instanceof android.net.wifi.WifiManager) {
                    wifiLock = ((android.net.wifi.WifiManager) wmObj).createWifiLock(
                            android.net.wifi.WifiManager.WIFI_MODE_FULL, "YoutubeLite:wifi");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
        } catch (Exception ignored) {
        }
    }

    private void releaseLocks() {
        try {
            if (cpuLock != null && cpuLock.isHeld()) cpuLock.release();
        } catch (Exception ignored) {
        }
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification(String title) {
        if (Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    CHANNEL_ID, "Dang phat", android.app.NotificationManager.IMPORTANCE_LOW);
            Object nmObj = getSystemService(Context.NOTIFICATION_SERVICE);
            if (nmObj instanceof android.app.NotificationManager) {
                try {
                    ((android.app.NotificationManager) nmObj).createNotificationChannel(ch);
                } catch (Exception ignored) {
                }
            }
        }
        Intent open = new Intent(this, PlayerActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("Youtube Lite dang phat")
                .setContentText(title != null && !title.isEmpty() ? title : "Nhan de mo lai")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pi)
                .setOngoing(true);
        return b.build();
    }

    @Override
    public void onDestroy() {
        releaseLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
