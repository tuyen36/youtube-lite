package com.example.youtubelite;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Man hinh Cai dat (2.9):
 * 1) Hen gio tat phat: Tat / 30 phut / 1 gio / 2 gio (luu vao PlaybackService).
 * 2) Do phan giai mac dinh: 360p / 480p / 720p / 1080p.
 *    Thieu luong cao thi ve 480p (xem AppSettings.pickIndex).
 * Nguoi dung thoat man hinh nay thi cai dat van giu (SharedPreferences).
 */
public class SettingsActivity extends AppCompatActivity {
    private TextView sleepState;
    private TextView qualityState;
    private Button sleepOffBtn;
    private Button sleep30Btn;
    private Button sleep60Btn;
    private Button sleep120Btn;
    private Button q360Btn;
    private Button q480Btn;
    private Button q720Btn;
    private Button q1080Btn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        View backBtn = findViewById(R.id.settings_back_btn);
        sleepState = findViewById(R.id.settings_sleep_state);
        qualityState = findViewById(R.id.settings_quality_state);
        sleepOffBtn = findViewById(R.id.settings_sleep_off);
        sleep30Btn = findViewById(R.id.settings_sleep_30);
        sleep60Btn = findViewById(R.id.settings_sleep_60);
        sleep120Btn = findViewById(R.id.settings_sleep_120);
        q360Btn = findViewById(R.id.settings_q360);
        q480Btn = findViewById(R.id.settings_q480);
        q720Btn = findViewById(R.id.settings_q720);
        q1080Btn = findViewById(R.id.settings_q1080);

        backBtn.setOnClickListener(v -> finish());

        sleepOffBtn.setOnClickListener(v -> setSleep(0));
        sleep30Btn.setOnClickListener(v -> setSleep(30));
        sleep60Btn.setOnClickListener(v -> setSleep(60));
        sleep120Btn.setOnClickListener(v -> setSleep(120));

        q360Btn.setOnClickListener(v -> setQuality(360));
        q480Btn.setOnClickListener(v -> setQuality(480));
        q720Btn.setOnClickListener(v -> setQuality(720));
        q1080Btn.setOnClickListener(v -> setQuality(1080));

        refreshViews();
        // Muc 2 UI/UX: thanh dieu huong chung duoi cung.
        NavBar.bind(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshViews();
    }

    private void setSleep(int minutes) {
        AppSettings.setSleepMinutes(this, minutes);
        // Bao service hen gio that (neu dang phat) + doi thong bao.
        try {
            android.content.Intent si =
                    new android.content.Intent(this, PlaybackService.class);
            si.putExtra(PlaybackService.EXTRA_SLEEP_MINUTES, minutes);
            startService(si);
        } catch (Exception ignored) {
        }
        refreshViews();
        Toast.makeText(this, minutes <= 0 ? "Đã tắt hẹn giờ"
                : "Hẹn giờ tắt sau " + AppSettings.sleepLabel(minutes),
                Toast.LENGTH_SHORT).show();
    }

    private void setQuality(int height) {
        AppSettings.setDefaultHeight(this, height);
        refreshViews();
        Toast.makeText(this, "Mặc định phát " + height + "p"
                + (height >= 720 ? " (thiếu luồng thì về 480p)" : ""),
                Toast.LENGTH_SHORT).show();
    }

    private void refreshViews() {
        int sleep = AppSettings.getSleepMinutes(this);
        int q = AppSettings.getDefaultHeight(this);
        long leftMs = PlaybackService.getSleepLeftMs();
        if (sleepState != null) {
            if (sleep <= 0) {
                sleepState.setText("Đang tắt");
            } else if (leftMs > 0) {
                long m = leftMs / 60000;
                long s = (leftMs % 60000) / 1000;
                sleepState.setText("Đang hẹn " + AppSettings.sleepLabel(sleep)
                        + " — còn " + m + " phút " + s + " giây");
            } else {
                sleepState.setText("Đang hẹn " + AppSettings.sleepLabel(sleep));
            }
        }
        if (qualityState != null) {
            qualityState.setText("Đang dùng: " + q + "p");
        }
        setSelected(sleepOffBtn, sleep == 0);
        setSelected(sleep30Btn, sleep == 30);
        setSelected(sleep60Btn, sleep == 60);
        setSelected(sleep120Btn, sleep == 120);
        setSelected(q360Btn, q == 360);
        setSelected(q480Btn, q == 480);
        setSelected(q720Btn, q == 720);
        setSelected(q1080Btn, q == 1080);
    }

    private static void setSelected(Button b, boolean selected) {
        if (b == null) return;
        try {
            b.setEnabled(!selected);
            b.setAlpha(selected ? 0.5f : 1f);
        } catch (Exception ignored) {
        }
    }
}
