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
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }
        SearchActivity.VideoItem item = items.get(position);
        h.title.setText(item.title != null ? item.title : "");
        String meta = "";
        if (item.uploader != null && !item.uploader.isEmpty()) meta += item.uploader;
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
        return convertView;
    }
}
