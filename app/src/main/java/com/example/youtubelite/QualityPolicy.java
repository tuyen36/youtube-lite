package com.example.youtubelite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Chính sách chất lượng cho máy phụ cũ:
 * - Trần 1080p (1920x1080), không lấy 1440p/4K.
 * - TẮT 60fps: chỉ nhận <= 30fps (máy cũ giải mã 60fps giật + nóng máy).
 * - Ưu tiên codec H.264 (avc1) vì chip cũ có giải mã cứng; VP9/AV1 để cuối.
 */
public final class QualityPolicy {
    private QualityPolicy() {}

    public static final int MAX_HEIGHT = 1080;
    public static final int MAX_FPS = 30;

    public static class Stream {
        public final String url;
        public final int height;      // 144 / 240 / 360 / 480 / 720 / 1080
        public final int fps;         // 24 / 25 / 30 / 60
        public final String codec;    // "avc1" (H.264), "vp9", "av01" (AV1)
        public final boolean isProgressive; // true = file mp4 trực tiếp (máy yếu nên ưu tiên)

        public Stream(String url, int height, int fps, String codec, boolean isProgressive) {
            this.url = url;
            this.height = height;
            this.fps = fps;
            this.codec = codec;
            this.isProgressive = isProgressive;
        }

        public String label() {
            return height + "p" + (fps > 30 ? fps : "");
        }
    }

    /** Lọc bỏ >1080p và >30fps, sắp xếp: progressive trước, H.264 trước, cao->thấp.
     * Dùng Collections.sort (API 21 OK). KHÔNG dùng List.sort vì cần API 24+.
     * K016: loai VP9/AV1 videoOnly (chip Atom khong co giai ma cung -> giat),
     * chi giu H.264 videoOnly + moi progressive (ke ca VP9 144/240/360p nhe). */
    public static List<Stream> filter(List<Stream> input) {
        List<Stream> ok = new ArrayList<>();
        for (Stream s : input) {
            if (s.height > MAX_HEIGHT) continue;
            if (s.fps > MAX_FPS) continue; // tắt 60fps ở đây
            if (!s.isProgressive && !isAvc(s.codec)) continue; // K016: videoOnly chi nhan H.264
            ok.add(s);
        }
        Collections.sort(ok, new Comparator<Stream>() {
            @Override
            public int compare(Stream a, Stream b) {
                if (a.isProgressive != b.isProgressive) return a.isProgressive ? -1 : 1;
                int ca = codecRank(a.codec), cb = codecRank(b.codec);
                if (ca != cb) return Integer.compare(ca, cb);
                return Integer.compare(b.height, a.height);
            }
        });
        return ok;
    }

    private static int codecRank(String codec) {
        if (codec == null) return 99;
        String c = codec.toLowerCase();
        if (c.contains("avc")) return 0;   // H.264 — máy cũ có giải mã cứng
        if (c.contains("vp9")) return 1;
        if (c.contains("av01") || c.contains("av1")) return 2;
        return 3;
    }

    /** Chọn mặc định cho máy phụ K016: 360p progressive nếu có (nhe + co tieng),
     * khong thi 360p, khong thi 480p, khong thi muc thap nhat con lai. */
    public static Stream pickDefault(List<Stream> filtered) {
        if (filtered.isEmpty()) return null;
        for (Stream s : filtered) {
            if (s.height == 360 && s.isProgressive) return s;
        }
        for (Stream s : filtered) {
            if (s.height == 360) return s;
        }
        for (Stream s : filtered) {
            if (s.height == 480) return s;
        }
        return filtered.get(filtered.size() - 1);
    }

    static boolean isAvc(String codec) {
        return codec != null && codec.toLowerCase().contains("avc");
    }
}
