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
 * 1) Thịnh hành tại Việt Nam — thử lần lượt nhiều kiosk (Trending cũ đã bị
 *    YouTube bỏ từ 07/2025 nên hay lỗi "Could not get Now or Videos tab").
 *    Thứ tự: Trending -> trending_music -> trending_gaming
 *             -> trending_movies_and_shows -> live.
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
        public String trendingSource = "";
    }

    private static final String[] KIOSK_TRY_ORDER = {
            "Trending",
            "trending_music",
            "trending_gaming",
            "trending_movies_and_shows",
            "trending_podcasts_episodes",
            "live",
    };

    public static Bundle load(android.content.Context ctx) {
        Bundle out = new Bundle();
        try {
            NewPipeHolder.initIfNeeded();
            StreamingService yt = org.schabi.newpipe.extractor.NewPipe.getService(0);
            // 1) Trending Việt Nam — thử từng kiosk tới khi có kết quả.
            String lastErr = "";
            for (String kioskId : KIOSK_TRY_ORDER) {
                try {
                    KioskExtractor<StreamInfoItem> kiosk =
                            yt.getKioskList().getExtractorById(kioskId, null);
                    kiosk.forceContentCountry(new ContentCountry("VN"));
                    try {
                        java.util.Optional<Localization> vi =
                                Localization.fromLocalizationCode("vi");
                        if (vi.isPresent()) kiosk.forceLocalization(vi.get());
                    } catch (Exception ignored) {
                    }
                    kiosk.fetchPage();
                    ListExtractor.InfoItemsPage<StreamInfoItem> page = kiosk.getInitialPage();
                    List<SearchActivity.VideoItem> got = new ArrayList<>();
                    for (StreamInfoItem s : page.getItems()) {
                        got.add(SearchActivity.itemFromStreamItem(s));
                        if (got.size() >= 50) break;
                    }
                    if (!got.isEmpty()) {
                        out.trending.addAll(got);
                        out.trendingSource = kioskId;
                        break;
                    }
                    lastErr = "kiosk " + kioskId + " trả rỗng";
                } catch (Exception e) {
                    lastErr = "kiosk " + kioskId + ": " + String.valueOf(e.getMessage());
                }
            }
            // Fallback cuối: tìm kiếm "thịnh hành Việt Nam" để luôn có nội dung.
            if (out.trending.isEmpty()) {
                try {
                    org.schabi.newpipe.extractor.search.SearchExtractor se =
                            yt.getSearchExtractor("thịnh hành Việt Nam 2026");
                    se.fetchPage();
                    org.schabi.newpipe.extractor.search.SearchInfo si =
                            org.schabi.newpipe.extractor.search.SearchInfo.getInfo(se);
                    for (org.schabi.newpipe.extractor.InfoItem it : si.getRelatedItems()) {
                        if (!(it instanceof StreamInfoItem)) continue;
                        out.trending.add(SearchActivity.itemFromStreamItem((StreamInfoItem) it));
                        if (out.trending.size() >= 50) break;
                    }
                    if (!out.trending.isEmpty()) {
                        out.trendingSource = "search:thinh-hanh-vn";
                        lastErr = "";
                    }
                } catch (Exception e) {
                    lastErr += " | search fallback: " + String.valueOf(e.getMessage());
                }
            }
            if (out.trending.isEmpty() && !lastErr.isEmpty()) {
                out.trendingError = lastErr;
            }
            // 2) Video liên quan: lấy related của tối đa 10 video xem gần nhất,
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
            if (out.trendingError.isEmpty()) out.trendingError = String.valueOf(e.getMessage());
        }
        return out;
    }
}
