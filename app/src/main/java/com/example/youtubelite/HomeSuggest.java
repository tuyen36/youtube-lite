package com.example.youtubelite;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Nguồn đề xuất cho màn hình chính (2.8: chỉ còn "Gợi ý cho bạn").
 * - Đã bỏ tải "Thịnh hành tại Việt Nam" cho đỡ tốn mạng/pin máy cũ.
 * - Chỉ lấy video liên quan tới lịch sử xem (related của tối đa 10 video gần nhất),
 *   gộp + khử trùng, mục tiêu 40 gợi ý.
 * Tất cả chạy trên luồng nền, gọi từ HomeActivity.
 */
public final class HomeSuggest {
    private HomeSuggest() {}

    public static class Bundle {
        public final List<SearchActivity.VideoItem> related = new ArrayList<>();
        public String relatedError = "";
    }

    public static Bundle load(android.content.Context ctx) {
        Bundle out = new Bundle();
        try {
            NewPipeHolder.initIfNeeded();
            StreamingService yt = org.schabi.newpipe.extractor.NewPipe.getService(0);
            // Video liên quan: lấy related của tối đa 10 video xem gần nhất,
            // gộp + khử trùng, mục tiêu 40 gợi ý.
            try {
                List<WatchHistory.Entry> hist = WatchHistory.list(ctx);
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (WatchHistory.Entry h : hist) {
                    if (out.related.size() >= 40) break;
                    if (h.url == null || h.url.isEmpty() || !seen.add(h.url)) continue;
                    try {
                        org.schabi.newpipe.extractor.stream.StreamInfo detail =
                                org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(yt, h.url);
                        List<InfoItem> rel = detail.getRelatedItems();
                        if (rel == null) continue;
                        for (InfoItem it : rel) {
                            if (!(it instanceof StreamInfoItem)) continue;
                            StreamInfoItem s = (StreamInfoItem) it;
                            if (s.getUrl() == null || !seen.add(s.getUrl())) continue;
                            out.related.add(SearchActivity.itemFromStreamItem(s));
                            if (out.related.size() >= 40) break;
                        }
                    } catch (Exception ignored) {
                        // Video cũ bị xoá/giới hạn thì bỏ qua, lấy video tiếp theo.
                    }
                }
            } catch (Exception e) {
                out.relatedError = String.valueOf(e.getMessage());
            }
        } catch (Exception e) {
            if (out.relatedError.isEmpty()) out.relatedError = String.valueOf(e.getMessage());
        }
        return out;
    }
}
