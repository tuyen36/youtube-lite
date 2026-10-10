package com.example.youtubelite;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;

/**
 * Adapter hiện thumbnail + tiêu đề + kênh + độ dài video.
 */
public class VideoAdapter extends BaseAdapter {
    private final Context ctx;
    private final List<SearchActivity.VideoItem> items;

    public VideoAdapter(Context ctx, List<SearchActivity.VideoItem> items) {
        this.ctx = ctx;
        this.items = items;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    static class Holder {
        ImageView thumb;
        TextView title;
        TextView meta;
        TextView duration;
        TextView channelLink;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(ctx).inflate(R.layout.item_video, parent, false);
            h = new Holder();
            h.thumb = convertView.findViewById(R.id.video_thumb);
            h.title = convertView.findViewById(R.id.video_title);
            h.meta = convertView.findViewById(R.id.video_meta);
            h.duration = convertView.findViewById(R.id.video_duration);
            h.channelLink = convertView.findViewById(R.id.video_channel_link);
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }
        SearchActivity.VideoItem item = items.get(position);
        h.title.setText(item.title != null ? item.title : "");
        String meta = "";
        if (item.uploader != null && !item.uploader.isEmpty()) meta += item.uploader;
        // Ngày đăng: "kênh • 3 ngày trước" (giống YouTube).
        if (item.uploadDate != null && !item.uploadDate.isEmpty()) {
            if (!meta.isEmpty()) meta += " • ";
            meta += item.uploadDate;
        }
        h.meta.setText(meta);
        h.meta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
        String dur = item.durationLabel();
        h.duration.setText(dur);
        h.duration.setVisibility(dur.isEmpty() ? View.GONE : View.VISIBLE);
        if (item.thumbUrl != null && !item.thumbUrl.isEmpty()) {
            Glide.with(ctx).load(item.thumbUrl).centerCrop().into(h.thumb);
        } else {
            h.thumb.setImageResource(android.R.color.darker_gray);
        }
        // Muc 1 UI/UX: nut Xem kenh rieng trong tung dong (de thay hon giu lau).
        if (h.channelLink != null) {
            if (item.uploaderUrl != null && !item.uploaderUrl.isEmpty()) {
                h.channelLink.setVisibility(View.VISIBLE);
                h.channelLink.setOnClickListener(v ->
                        ChannelActivity.open(ctx, item.uploaderUrl, item.uploader));
            } else {
                h.channelLink.setVisibility(View.GONE);
                h.channelLink.setOnClickListener(null);
            }
        }
        return convertView;
    }
}
