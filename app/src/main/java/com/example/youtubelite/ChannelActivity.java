package com.example.youtubelite;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Trang kenh nguoi dang: header (avatar + ten + subs + mo ta) + 3 tab
 * Trang chu / Video / Danh sach phat (giong anh mau cua user).
 * - Bam ten kenh o man tim kiem / man phat -> mo trang nay.
 * - Bam video -> phat ngay (dung chung SearchActivity.openPlayer).
 * - Bam danh sach phat -> mo video dau cua danh sach do.
 * API 21 OK: khong dung List.sort / stream / Optional.
 */
public class ChannelActivity extends AppCompatActivity {
    public static final String EXTRA_CHANNEL_URL = "channel_url";
    public static final String EXTRA_CHANNEL_NAME = "channel_name";

    private ImageView avatarView;
    private TextView nameView;
    private TextView subsView;
    private TextView descView;
    private TextView stateView;
    private LinearLayout container;
    private Button moreBtn;
    private Button tabHomeBtn;
    private Button tabVideosBtn;
    private Button tabPlaylistsBtn;

    private String channelUrl = "";
    private org.schabi.newpipe.extractor.channel.ChannelInfo channelInfo;
    private final List<RowItem> homeRows = new ArrayList<>();
    private final List<RowItem> videoRows = new ArrayList<>();
    private final List<RowItem> playlistRows = new ArrayList<>();
    private int currentTab = 0; // 0 = home, 1 = videos, 2 = playlists
    private int shown = 20;
    private static final int PAGE = 20;
    // Trang ke de tai them khi bam Xem them.
    private org.schabi.newpipe.extractor.Page nextHomePage;
    private org.schabi.newpipe.extractor.linkhandler.ListLinkHandler nextHomeHandler;
    private org.schabi.newpipe.extractor.Page nextVideoPage;
    private org.schabi.newpipe.extractor.linkhandler.ListLinkHandler nextVideoHandler;
    private org.schabi.newpipe.extractor.Page nextPlaylistPage;
    private org.schabi.newpipe.extractor.linkhandler.ListLinkHandler nextPlaylistHandler;

    /** 1 dong: video hoac danh sach phat. */
    static class RowItem {
        boolean isPlaylist;
        String url;       // video: link phat; playlist: link danh sach
        String title;
        String thumbUrl;
        String meta;      // video: kenh • luot xem • ngay; playlist: N video • kenh
        long durationSec; // video; playlist = so video
        String uploader;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);

        avatarView = findViewById(R.id.channel_avatar);
        nameView = findViewById(R.id.channel_name);
        subsView = findViewById(R.id.channel_subs);
        descView = findViewById(R.id.channel_desc);
        stateView = findViewById(R.id.channel_state);
        container = findViewById(R.id.channel_container);
        moreBtn = findViewById(R.id.channel_more_btn);
        tabHomeBtn = findViewById(R.id.channel_tab_home);
        tabVideosBtn = findViewById(R.id.channel_tab_videos);
        tabPlaylistsBtn = findViewById(R.id.channel_tab_playlists);
        View backBtn = findViewById(R.id.channel_back_btn);

        channelUrl = getIntent().getStringExtra(EXTRA_CHANNEL_URL);
        String hintName = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);
        if (hintName != null && !hintName.isEmpty()) nameView.setText(hintName);
        if (channelUrl == null || channelUrl.isEmpty()) {
            finish();
            return;
        }

        backBtn.setOnClickListener(v -> finish());
        descView.setOnClickListener(v -> {
            try {
                if (descView.getMaxLines() <= 2) {
                    descView.setMaxLines(20);
                } else {
                    descView.setMaxLines(2);
                }
            } catch (Exception ignored) {
            }
        });
        tabHomeBtn.setOnClickListener(v -> switchTab(0));
        tabVideosBtn.setOnClickListener(v -> switchTab(1));
        tabPlaylistsBtn.setOnClickListener(v -> switchTab(2));
        moreBtn.setOnClickListener(v -> {
            shown += PAGE;
            loadMoreCurrentTab();
        });

        loadChannel();
    }

    private void switchTab(int tab) {
        currentTab = tab;
        shown = PAGE;
        updateTabStyle();
        renderCurrent();
        // Tai them nen neu chua co du lieu tab nay.
        if (tab == 1 && videoRows.isEmpty()) loadTabVideos();
        else if (tab == 2 && playlistRows.isEmpty()) loadTabPlaylists();
        else if (tab == 0 && homeRows.isEmpty()) loadChannel();
    }

    private void updateTabStyle() {
        try {
            paintTab(tabHomeBtn, currentTab == 0);
            paintTab(tabVideosBtn, currentTab == 1);
            paintTab(tabPlaylistsBtn, currentTab == 2);
        } catch (Exception ignored) {
        }
        refreshTabLabels();
    }

    /** Muc 3 UI/UX: tab dang chon nen do chu trang, tab nghi nen xam. */
    private static void paintTab(Button b, boolean selected) {
        if (b == null) return;
        try {
            b.setEnabled(!selected);
            if (selected) {
                b.setBackgroundColor(0xFFCC0000);
                b.setTextColor(0xFFFFFFFF);
            } else {
                b.setBackgroundColor(0xFF333333);
                b.setTextColor(0xFFCCCCCC);
            }
        } catch (Exception ignored) {
        }
    }

    /** Muc 3 UI/UX: ten tab kem so luong (Video (40), Danh sach phat (5)). */
    private void refreshTabLabels() {
        try {
            tabHomeBtn.setText(homeRows.isEmpty() ? "Trang chủ"
                    : "Trang chủ (" + homeRows.size() + ")");
        } catch (Exception ignored) {
        }
        try {
            tabVideosBtn.setText(videoRows.isEmpty() ? "Video"
                    : "Video (" + videoRows.size() + ")");
        } catch (Exception ignored) {
        }
        try {
            tabPlaylistsBtn.setText(playlistRows.isEmpty() ? "Danh sách phát"
                    : "Danh sách (" + playlistRows.size() + ")");
        } catch (Exception ignored) {
        }
    }

    /** Tai thong tin kenh + tab Trang chu (video moi nhat). */
    private void loadChannel() {
        stateView.setText("Đang tải kênh...");
        stateView.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                NewPipeHolder.initIfNeeded();
                org.schabi.newpipe.extractor.StreamingService yt =
                        org.schabi.newpipe.extractor.NewPipe.getService(0);
                org.schabi.newpipe.extractor.channel.ChannelInfo info =
                        org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(yt, channelUrl);
                channelInfo = info;
                // Header.
                String name = info.getName();
                long subs = -1;
                try {
                    subs = info.getSubscriberCount();
                } catch (Exception ignored) {
                }
                String desc = "";
                try {
                    desc = info.getDescription();
                } catch (Exception ignored) {
                }
                String avatar = "";
                try {
                    if (info.getAvatars() != null && !info.getAvatars().isEmpty()) {
                        avatar = info.getAvatars().get(info.getAvatars().size() - 1).getUrl();
                    }
                } catch (Exception ignored) {
                }
                final String fName = name, fDesc = desc, fAvatar = avatar;
                final long fSubs = subs;
                // Tab Trang chu: lay video tab videos (gioi han 20).
                List<RowItem> home = new ArrayList<>();
                org.schabi.newpipe.extractor.Page homeNext = null;
                org.schabi.newpipe.extractor.linkhandler.ListLinkHandler homeHandler = null;
                try {
                    org.schabi.newpipe.extractor.linkhandler.ListLinkHandler videosHandler =
                            findTab(info, org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS);
                    if (videosHandler != null) {
                        org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo tab =
                                org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(
                                        yt, videosHandler);
                        if (tab.getRelatedItems() != null) {
                            for (org.schabi.newpipe.extractor.InfoItem it : tab.getRelatedItems()) {
                                RowItem r = rowFromInfo(it);
                                if (r != null) home.add(r);
                                if (home.size() >= 40) break;
                            }
                        }
                        try {
                            homeNext = tab.getNextPage();
                        } catch (Exception ignored) {
                        }
                        homeHandler = videosHandler;
                    }
                } catch (Exception ignored) {
                }
                final List<RowItem> fHome = home;
                final org.schabi.newpipe.extractor.Page fHomeNext = homeNext;
                final org.schabi.newpipe.extractor.linkhandler.ListLinkHandler fHomeHandler = homeHandler;
                runOnUiThread(() -> {
                    if (fName != null && !fName.isEmpty()) nameView.setText(fName);
                    subsView.setText(subsLine(fSubs));
                    subsView.setVisibility(View.VISIBLE);
                    if (fDesc != null && !fDesc.isEmpty()) {
                        descView.setText(fDesc);
                        descView.setVisibility(View.VISIBLE);
                    } else {
                        descView.setVisibility(View.GONE);
                    }
                    if (fAvatar != null && !fAvatar.isEmpty()) {
                        Glide.with(this).load(fAvatar).centerCrop().into(avatarView);
                    }
                    homeRows.clear();
                    homeRows.addAll(fHome);
                    nextHomePage = fHomeNext;
                    nextHomeHandler = fHomeHandler;
                    // Mac dinh mo tab Trang chu.
                    currentTab = 0;
                    shown = PAGE;
                    updateTabStyle();
                    renderCurrent();
                    if (fHome.isEmpty()) {
                        stateView.setText("Kênh này chưa có video hiển thị");
                        stateView.setVisibility(View.VISIBLE);
                    } else {
                        stateView.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    stateView.setText("Không tải được kênh: " + e.getMessage());
                    stateView.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    /** Tai tab Video (toan bo video cua kenh). */
    private void loadTabVideos() {
        if (channelInfo == null) {
            loadChannel();
            return;
        }
        stateView.setText("Đang tải video...");
        stateView.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                NewPipeHolder.initIfNeeded();
                org.schabi.newpipe.extractor.StreamingService yt =
                        org.schabi.newpipe.extractor.NewPipe.getService(0);
                org.schabi.newpipe.extractor.linkhandler.ListLinkHandler h =
                        findTab(channelInfo,
                                org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS);
                List<RowItem> got = new ArrayList<>();
                org.schabi.newpipe.extractor.Page next = null;
                if (h != null) {
                    org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo tab =
                            org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(yt, h);
                    if (tab.getRelatedItems() != null) {
                        for (org.schabi.newpipe.extractor.InfoItem it : tab.getRelatedItems()) {
                            RowItem r = rowFromInfo(it);
                            if (r != null) got.add(r);
                            if (got.size() >= 60) break;
                        }
                    }
                    try {
                        next = tab.getNextPage();
                    } catch (Exception ignored) {
                    }
                }
                final List<RowItem> fGot = got;
                final org.schabi.newpipe.extractor.Page fNext = next;
                final org.schabi.newpipe.extractor.linkhandler.ListLinkHandler fH = h;
                runOnUiThread(() -> {
                    videoRows.clear();
                    videoRows.addAll(fGot);
                    nextVideoPage = fNext;
                    nextVideoHandler = fH;
                    shown = PAGE;
                    updateTabStyle();
                    if (currentTab == 1) renderCurrent();
                    stateView.setVisibility(fGot.isEmpty() ? View.VISIBLE : View.GONE);
                    if (fGot.isEmpty()) stateView.setText("Kênh chưa có video");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    stateView.setText("Không tải được video: " + e.getMessage());
                    stateView.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    /** Tai tab Danh sach phat. */
    private void loadTabPlaylists() {
        if (channelInfo == null) {
            loadChannel();
            return;
        }
        stateView.setText("Đang tải danh sách phát...");
        stateView.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                NewPipeHolder.initIfNeeded();
                org.schabi.newpipe.extractor.StreamingService yt =
                        org.schabi.newpipe.extractor.NewPipe.getService(0);
                org.schabi.newpipe.extractor.linkhandler.ListLinkHandler h =
                        findTab(channelInfo,
                                org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.PLAYLISTS);
                List<RowItem> got = new ArrayList<>();
                org.schabi.newpipe.extractor.Page next = null;
                if (h != null) {
                    org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo tab =
                            org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(yt, h);
                    if (tab.getRelatedItems() != null) {
                        for (org.schabi.newpipe.extractor.InfoItem it : tab.getRelatedItems()) {
                            RowItem r = rowFromInfo(it);
                            if (r != null) got.add(r);
                            if (got.size() >= 60) break;
                        }
                    }
                    try {
                        next = tab.getNextPage();
                    } catch (Exception ignored) {
                    }
                }
                final List<RowItem> fGot = got;
                final org.schabi.newpipe.extractor.Page fNext = next;
                final org.schabi.newpipe.extractor.linkhandler.ListLinkHandler fH = h;
                runOnUiThread(() -> {
                    playlistRows.clear();
                    playlistRows.addAll(fGot);
                    nextPlaylistPage = fNext;
                    nextPlaylistHandler = fH;
                    shown = PAGE;
                    updateTabStyle();
                    if (currentTab == 2) renderCurrent();
                    stateView.setVisibility(fGot.isEmpty() ? View.VISIBLE : View.GONE);
                    if (fGot.isEmpty()) stateView.setText("Kênh chưa có danh sách phát");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    stateView.setText("Không tải được danh sách phát: " + e.getMessage());
                    stateView.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    /** Bam Xem them: hien them 20 dong, neu het thi tai trang ke. */
    private void loadMoreCurrentTab() {
        List<RowItem> rows = rowsCurrent();
        if (shown < rows.size()) {
            renderCurrent();
            return;
        }
        // Het dong co san -> tai trang ke cua tab hien tai.
        stateView.setText("Đang tải thêm...");
        stateView.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                NewPipeHolder.initIfNeeded();
                org.schabi.newpipe.extractor.StreamingService yt =
                        org.schabi.newpipe.extractor.NewPipe.getService(0);
                org.schabi.newpipe.extractor.Page page = null;
                org.schabi.newpipe.extractor.linkhandler.ListLinkHandler handler = null;
                if (currentTab == 1) {
                    page = nextVideoPage;
                    handler = nextVideoHandler;
                } else if (currentTab == 2) {
                    page = nextPlaylistPage;
                    handler = nextPlaylistHandler;
                } else {
                    page = nextHomePage;
                    handler = nextHomeHandler;
                }
                if (page == null || handler == null) {
                    runOnUiThread(() -> {
                        stateView.setText("Đã hết");
                        stateView.setVisibility(View.VISIBLE);
                    });
                    return;
                }
                org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage<
                        org.schabi.newpipe.extractor.InfoItem> more =
                        org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getMoreItems(
                                yt, handler, page);
                List<RowItem> got = new ArrayList<>();
                if (more.getItems() != null) {
                    for (org.schabi.newpipe.extractor.InfoItem it : more.getItems()) {
                        RowItem r = rowFromInfo(it);
                        if (r != null) got.add(r);
                        if (got.size() >= 40) break;
                    }
                }
                org.schabi.newpipe.extractor.Page following = null;
                try {
                    following = more.getNextPage();
                } catch (Exception ignored) {
                }
                final List<RowItem> fGot = got;
                final org.schabi.newpipe.extractor.Page fFollowing = following;
                runOnUiThread(() -> {
                    rows.addAll(fGot);
                    if (currentTab == 1) nextVideoPage = fFollowing;
                    else if (currentTab == 2) nextPlaylistPage = fFollowing;
                    else nextHomePage = fFollowing;
                    renderCurrent();
                    // Muc 3 UI/UX: cap nhat so luong tren tab khi tai them.
                    updateTabStyle();
                    stateView.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    stateView.setText("Tải thêm lỗi: " + e.getMessage());
                    stateView.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private List<RowItem> rowsCurrent() {
        if (currentTab == 1) return videoRows;
        if (currentTab == 2) return playlistRows;
        return homeRows;
    }

    private void renderCurrent() {
        List<RowItem> rows = rowsCurrent();
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int n = Math.min(shown, rows.size());
        for (int i = 0; i < n; i++) {
            RowItem r = rows.get(i);
            View row = inflater.inflate(R.layout.item_video, container, false);
            ImageView thumb = row.findViewById(R.id.video_thumb);
            TextView title = row.findViewById(R.id.video_title);
            TextView meta = row.findViewById(R.id.video_meta);
            TextView duration = row.findViewById(R.id.video_duration);
            title.setText(r.title != null ? r.title : "");
            meta.setText(r.meta != null ? r.meta : "");
            meta.setVisibility((r.meta == null || r.meta.isEmpty()) ? View.GONE : View.VISIBLE);
            String dur = durationLabel(r);
            duration.setText(dur);
            duration.setVisibility(dur.isEmpty() ? View.GONE : View.VISIBLE);
            if (r.thumbUrl != null && !r.thumbUrl.isEmpty()) {
                Glide.with(this).load(r.thumbUrl).centerCrop().into(thumb);
            } else {
                thumb.setImageResource(android.R.color.darker_gray);
            }
            final RowItem fr = r;
            row.setOnClickListener(v -> openRow(fr));
            container.addView(row);
        }
        moreBtn.setVisibility(shown < rows.size() ? View.VISIBLE : View.GONE);
        // Neu chua co du lieu ma khong dang tai thi giu trang thai.
        if (rows.isEmpty()) {
            moreBtn.setVisibility(View.GONE);
        }
    }

    private String durationLabel(RowItem r) {
        if (r == null) return "";
        if (r.isPlaylist) {
            if (r.durationSec > 0) return r.durationSec + " video";
            return "";
        }
        long d = r.durationSec;
        if (d < 0) return "";
        long m = d / 60, s = d % 60;
        if (m >= 60) return String.format("%d:%02d:%02d", m / 60, m % 60, s);
        return String.format("%d:%02d", m, s);
    }

    /** Bam dong: video -> phat ngay; playlist -> mo video dau cua danh sach. */
    private void openRow(RowItem r) {
        if (r == null || r.url == null || r.url.isEmpty()) {
            Toast.makeText(this, "Mục này thiếu link", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!r.isPlaylist) {
            openVideoByUrl(r.url);
            return;
        }
        Toast.makeText(this, "Đang mở danh sách phát...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                NewPipeHolder.initIfNeeded();
                org.schabi.newpipe.extractor.StreamingService yt =
                        org.schabi.newpipe.extractor.NewPipe.getService(0);
                org.schabi.newpipe.extractor.playlist.PlaylistInfo pl =
                        org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(yt, r.url);
                String firstUrl = "";
                if (pl.getRelatedItems() != null && !pl.getRelatedItems().isEmpty()) {
                    for (org.schabi.newpipe.extractor.InfoItem it : pl.getRelatedItems()) {
                        if (it instanceof org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                            firstUrl = it.getUrl();
                            break;
                        }
                    }
                }
                final String fUrl = firstUrl;
                runOnUiThread(() -> {
                    if (fUrl == null || fUrl.isEmpty()) {
                        Toast.makeText(this, "Danh sách này chưa có video xem được",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    openVideoByUrl(fUrl);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Không mở được danh sách phát: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /** Mo man hinh phat tu link video (dung chung cach mo cua SearchActivity). */
    private void openVideoByUrl(String videoUrl) {
        Toast.makeText(this, "Đang lấy link phát...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                NewPipeHolder.initIfNeeded();
                org.schabi.newpipe.extractor.StreamingService yt =
                        org.schabi.newpipe.extractor.NewPipe.getService(0);
                org.schabi.newpipe.extractor.stream.StreamInfo detail =
                        org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(yt, videoUrl);
                String bestAudio = null;
                try {
                    if (detail.getAudioStreams() != null && !detail.getAudioStreams().isEmpty()) {
                        bestAudio = detail.getAudioStreams().get(0).getContent();
                    }
                } catch (Exception ignored) {
                    bestAudio = null;
                }
                // 2.11: link DASH thich ung cho 720p/1080p (het khung nhu YouTube goc).
                String dashMpd = "";
                try {
                    dashMpd = detail.getDashMpdUrl();
                } catch (Exception ignored) {
                    dashMpd = "";
                }
                final String fDashChan = dashMpd != null ? dashMpd : "";
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
                ArrayList<String> allUrls = new ArrayList<>();
                ArrayList<String> allLabels = new ArrayList<>();
                ArrayList<String> allAudios = new ArrayList<>();
                java.util.Set<Integer> seen = new java.util.HashSet<>();
                for (QualityPolicy.Stream s : prog) {
                    if (seen.contains(s.height)) continue;
                    seen.add(s.height);
                    allUrls.add(s.url);
                    allLabels.add(s.label() + " \u266A");
                    allAudios.add(null);
                }
                for (QualityPolicy.Stream s : only) {
                    if (seen.contains(s.height)) continue;
                    seen.add(s.height);
                    allUrls.add(s.url);
                    allLabels.add(s.label());
                    allAudios.add(bestAudio);
                }
                // Tieu de + kenh + thumb de man hinh phat hien ngay.
                String vTitle = "";
                String vUploader = "";
                String vThumb = "";
                long vDur = -1;
                try {
                    vTitle = detail.getName();
                } catch (Exception ignored) {
                }
                try {
                    vUploader = detail.getUploaderName();
                } catch (Exception ignored) {
                }
                try {
                    if (detail.getThumbnails() != null && !detail.getThumbnails().isEmpty()) {
                        vThumb = detail.getThumbnails().get(0).getUrl();
                    }
                } catch (Exception ignored) {
                }
                try {
                    vDur = detail.getDuration();
                } catch (Exception ignored) {
                }
                String channelUrl = "";
                try {
                    channelUrl = detail.getUploaderUrl();
                } catch (Exception ignored) {
                }
                final String fTitle = vTitle, fUploader = vUploader, fThumb = vThumb;
                final long fDur = vDur;
                final String fChannelUrl = channelUrl;
                runOnUiThread(() -> {
                    if (allUrls.isEmpty()) {
                        Toast.makeText(this, "Không có định dạng phù hợp máy này",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 2.9: muc mac dinh lay tu Cai dat (thieu luong cao ve 480p).
                    int defIdx = PlayerActivity.pickDefaultIndex(this, allLabels);
                    int h0 = AppSettings.getDefaultHeight(this);
                    try {
                        String d = allLabels.get(defIdx).replaceAll("[^0-9]", "");
                        if (d.length() > 4) d = d.substring(0, 4);
                        h0 = Integer.parseInt(d);
                    } catch (Exception ignored) {
                        h0 = 360;
                    }
                    SearchActivity.VideoItem item = new SearchActivity.VideoItem(
                            videoUrl, fTitle, new ArrayList<>(), fThumb, fDur,
                            fUploader, "", fChannelUrl);
                    SearchActivity.openPlayer(this, item,
                            allUrls.get(defIdx), h0, allAudios.get(defIdx),
                            allUrls, allLabels, allAudios, fDashChan);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Không lấy được link phát: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static org.schabi.newpipe.extractor.linkhandler.ListLinkHandler findTab(
            org.schabi.newpipe.extractor.channel.ChannelInfo info, String tabName) {
        try {
            if (info.getTabs() != null) {
                for (org.schabi.newpipe.extractor.linkhandler.ListLinkHandler h : info.getTabs()) {
                    if (h == null || h.getContentFilters() == null) continue;
                    for (String f : h.getContentFilters()) {
                        if (tabName.equals(f)) return h;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static RowItem rowFromInfo(org.schabi.newpipe.extractor.InfoItem it) {
        try {
            if (it instanceof org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                org.schabi.newpipe.extractor.stream.StreamInfoItem s =
                        (org.schabi.newpipe.extractor.stream.StreamInfoItem) it;
                RowItem r = new RowItem();
                r.isPlaylist = false;
                r.url = s.getUrl();
                r.title = s.getName() != null ? s.getName() : r.url;
                try {
                    if (s.getThumbnails() != null && !s.getThumbnails().isEmpty()) {
                        r.thumbUrl = s.getThumbnails().get(0).getUrl();
                    }
                } catch (Exception ignored) {
                }
                try {
                    r.durationSec = s.getDuration();
                } catch (Exception ignored) {
                    r.durationSec = -1;
                }
                StringBuilder m = new StringBuilder();
                try {
                    if (s.getUploaderName() != null && !s.getUploaderName().isEmpty()) {
                        m.append(s.getUploaderName());
                    }
                } catch (Exception ignored) {
                }
                try {
                    long views = s.getViewCount();
                    if (views >= 0) {
                        if (m.length() > 0) m.append(" • ");
                        m.append(formatCount(views)).append(" lượt xem");
                    }
                } catch (Exception ignored) {
                }
                try {
                    String date = s.getTextualUploadDate();
                    if (date != null && !date.isEmpty()) {
                        if (m.length() > 0) m.append(" • ");
                        m.append(date);
                    }
                } catch (Exception ignored) {
                }
                r.meta = m.toString();
                try {
                    r.uploader = s.getUploaderName();
                } catch (Exception ignored) {
                }
                return r;
            }
            if (it instanceof org.schabi.newpipe.extractor.playlist.PlaylistInfoItem) {
                org.schabi.newpipe.extractor.playlist.PlaylistInfoItem p =
                        (org.schabi.newpipe.extractor.playlist.PlaylistInfoItem) it;
                RowItem r = new RowItem();
                r.isPlaylist = true;
                r.url = p.getUrl();
                r.title = p.getName() != null ? p.getName() : r.url;
                try {
                    if (p.getThumbnails() != null && !p.getThumbnails().isEmpty()) {
                        r.thumbUrl = p.getThumbnails().get(0).getUrl();
                    }
                } catch (Exception ignored) {
                }
                try {
                    r.durationSec = p.getStreamCount();
                } catch (Exception ignored) {
                    r.durationSec = -1;
                }
                StringBuilder m = new StringBuilder();
                m.append("Danh sách phát");
                try {
                    long count = p.getStreamCount();
                    if (count > 0) {
                        m.append(" • ").append(count).append(" video");
                    }
                } catch (Exception ignored) {
                }
                try {
                    if (p.getUploaderName() != null && !p.getUploaderName().isEmpty()) {
                        m.append(" • ").append(p.getUploaderName());
                    }
                } catch (Exception ignored) {
                }
                r.meta = m.toString();
                return r;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String subsLine(long subs) {
        if (subs < 0) return "";
        if (subs >= 1000000) return String.format("%.1f Tr người đăng ký", subs / 1000000.0);
        if (subs >= 1000) return String.format("%.1f N người đăng ký", subs / 1000.0);
        return subs + " người đăng ký";
    }

    private static String formatCount(long v) {
        if (v >= 1000000000) return String.format("%.1f T", v / 1000000000.0);
        if (v >= 1000000) return String.format("%.1f Tr", v / 1000000.0);
        if (v >= 1000) return String.format("%.1f N", v / 1000.0);
        return String.valueOf(v);
    }

    /** Mo trang kenh tu noi khac (man phat / tim kiem). */
    public static void open(android.content.Context ctx, String channelUrl, String channelName) {
        if (channelUrl == null || channelUrl.isEmpty()) {
            android.widget.Toast.makeText(ctx, "Kênh này chưa có link để mở",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.Intent i = new android.content.Intent(ctx, ChannelActivity.class);
        i.putExtra(EXTRA_CHANNEL_URL, channelUrl);
        if (channelName != null) i.putExtra(EXTRA_CHANNEL_NAME, channelName);
        if (!(ctx instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
}
