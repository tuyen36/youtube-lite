package com.example.youtubelite;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Nguồn đề xuất cho màn hình chính:
 * 1) Trending Việt Nam (Kiosk "Trending", ép country VN + ngôn ngữ vi).
 * 2) Video liên quan tới lịch sử xem (StreamInfo.getRelatedItems của video gần nhất).
 * Tất cả chạy trên luồng nền, gọi từ HomeActivity.
 */
public final class HomeSuggest {
    private HomeSuggest() {}

    public static class Bundle {
        public final List<SearchActivity.VideoItem> trending = new ArrayList<>();
        public final List<SearchActivity.VideoItem> related = new ArrayList<>();
        public String trendingError = "";
        public String relatedError = "";
    }

    public static Bundle load(android.content.Context ctx) {
        Bundle out = new Bundle();
        try {
            NewPipeHolder.initIfNeeded();
            StreamingService yt = org.schabi.newpipe.extractor.NewPipe.getService(0);
            // Trending Việt Nam.
            try {
                KioskExtractor<StreamInfoItem> kiosk =
                        yt.getKioskList().getExtractorById("Trending", null);
                kiosk.forceContentCountry(new ContentCountry("VN"));
                java.util.Optional<Localization> vi =
                        Localization.fromLocalizationCode("vi");
                if (vi.isPresent()) kiosk.forceLocalization(vi.get());
                kiosk.fetchPage();
                ListExtractor.InfoItemsPage<StreamInfoItem> page = kiosk.getInitialPage();
                for (StreamInfoItem s : page.getItems()) {
                    out.trending.add(SearchActivity.itemFromStreamItem(s));
                    if (out.trending.size() >= 20) break;
                }
            } catch (Exception e) {
                out.trendingError = String.valueOf(e.getMessage());
            }
            // Video liên quan: lấy related của 3 video xem gần nhất, gộp + khử trùng.
            try {
                List<WatchHistory.Entry> hist = WatchHistory.list(ctx);
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (WatchHistory.Entry h : hist) {
                    if (out.related.size() >= 20) break;
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
                            if (out.related.size() >= 20) break;
                        }
                    } catch (Exception ignored) {
                        // Video cũ bị xoá/giới hạn thì bỏ qua, lấy video tiếp theo.
                    }
                }
            } catch (Exception e) {
                out.relatedError = String.valueOf(e.getMessage());
            }
        } catch (Exception e) {
            if (out.trendingError.isEmpty()) out.trendingError = String.valueOf(e.getMessage());
        }
        return out;
    }

    /** Tải thêm trending (nếu kiosk có trang kế). Hiện YouTube trending thường 1 trang. */
    public static List<SearchActivity.VideoItem> moreTrending(Page next,
                                                             KioskExtractor<StreamInfoItem> kiosk) {
        List<SearchActivity.VideoItem> more = new ArrayList<>();
        if (next == null || kiosk == null) return more;
        try {
            ListExtractor.InfoItemsPage<StreamInfoItem> p = kiosk.getPage(next);
            for (StreamInfoItem s : p.getItems()) {
                more.add(SearchActivity.itemFromStreamItem(s));
                if (more.size() >= 20) break;
            }
        } catch (Exception ignored) {
        }
        return more;
    }
}
