# Youtube Lite — app xem YouTube cho máy phụ Android 5.0+

App native Java, tối ưu máy cũ RAM 1GB, đúng yêu cầu của bạn:
- Chạy từ **Android 5.0 (API 21)**, chỉ build chip `armeabi-v7a` cho APK nhẹ.
- **Trần 1080p, TẮT 60fps** (lọc trong `QualityPolicy.java`: bỏ mọi stream
  `height > 1080` và `fps > 30`, ưu tiên H.264 có giải mã cứng).
- Mặc định mở ở **480p** cho mượt, user tự chuyển lên 720p/1080p trong PlayerView.
- Buffer nhỏ 5–15s, không tràn RAM máy 1GB.
- Lấy link stream bằng **NewPipeExtractor** (không cần API key YouTube).

## Cấu trúc

```
youtube-lite/
├── settings.gradle, build.gradle
└── app/
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/youtubelite/
        │   ├── QualityPolicy.java   ← trần 1080p + tắt 60fps (đã xong)
        │   ├── NewPipeHolder.java   ← khởi tạo NewPipeExtractor + OkHttp (đã xong)
        │   ├── PlayerActivity.java  ← ExoPlayer + buffer nhỏ + khóa track
        │   └── SearchActivity.java  ← tìm kiếm thật + lọc 1080p/30fps (đã xong)
        └── res/layout/values/
```

## Cài 1 lần trên LUNA36 (Windows 11)

1. Cài **Android Studio Ladybug** trở lên (tự kèm JDK 17 + Gradle).
2. Mở Android Studio → **Open** → chọn thư mục `youtube-lite/`.
3. Đợi **Gradle Sync** tải: ExoPlayer Media3 1.4.1, NewPipeExtractor v0.24.5,
   OkHttp 3.14.9, Glide 4.16.0 (cần mạng, ~5–10 phút lần đầu).
4. Cắm máy phụ qua USB (bật USB Debugging) hoặc tạo giả lập **API 21**.
5. **Build → Build APK(s)** → file ra ở
   `app/build/outputs/apk/debug/app-debug.apk` → copy sang máy phụ cài.

## Đã nối tìm kiếm thật (không còn việc dở)

`SearchActivity.doSearch()` đã tìm thật bằng NewPipeExtractor:
tìm kiếm → lấy `StreamInfo` từng video → map sang `QualityPolicy.Stream`
(`mapVideoStream()`: parse `720p`/`1080p60`, fps, codec, progressive)
→ `QualityPolicy.filter()` loại >1080p + 60fps → tối đa 25 kết quả, chạy
trên luồng nền, lỗi hiện Toast tiếng Việt.

## Test trên máy cũ

1. Cài APK, mở app, tìm 1 từ khóa, bấm 1 video → phải phát ở 480p.
2. Bấm bánh răng trong player → chuyển 720p / 1080p → xem có giật không.
3. Video 60fps (vd "60fps test") → app phải tự rớt về bản 30fps cùng độ phân giải.
4. Tắt mạng giữa chừng → phải buffer lại trong ~5s, không crash.

## Lưu ý

- Dùng gia đình/nội bộ: vô tư. Đăng Play Store tên "Youtube..." sẽ bị gỡ
  (nhãn hiệu + ToS YouTube).
- Máy bạn chưa có Java/Gradle/SDK — cài Android Studio là có hết 1 lần.
- Giả lập API 21 trên LUNA36 (8GB) sẽ chậm — nên test trên máy phụ thật.
