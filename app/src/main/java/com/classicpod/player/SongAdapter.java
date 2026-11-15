package com.classicpod.player;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class SongAdapter extends BaseAdapter {
    private final ArrayList<Song> songs = new ArrayList<Song>();
    private ArtLoader art;
    private int thumbPx;

    public void setSongs(List<Song> next) {
        songs.clear();
        if (next != null) {
            songs.addAll(next);
        }
        notifyDataSetChanged();
    }

    public Song getSong(int position) {
        if (position < 0 || position >= songs.size()) {
            return null;
        }
        return songs.get(position);
    }

    public List<Song> snapshot() {
        return new ArrayList<Song>(songs);
    }

    @Override
    public int getCount() {
        return songs.size();
    }

    @Override
    public Object getItem(int position) {
        return songs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return songs.get(position).id;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        Holder h;
        if (v == null) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false);
            h = new Holder();
            h.art = (ImageView) v.findViewById(R.id.item_art);
            h.title = (TextView) v.findViewById(R.id.item_title);
            h.sub = (TextView) v.findViewById(R.id.item_subtitle);
            v.setTag(h);
        } else {
            h = (Holder) v.getTag();
        }
        if (art == null) {
            art = ArtLoader.get(parent.getContext());
            thumbPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36,
                    parent.getResources().getDisplayMetrics());
        }
        Song s = songs.get(position);
        h.title.setText(s.displayTitle());
        if (s.isOnline()) {
            h.sub.setText(s.displayArtist() + " — " + s.displayAlbum());
            h.art.setImageResource(R.drawable.artwork_placeholder);
            h.art.setTag(Long.valueOf(0L));
        } else {
            h.sub.setText(s.displayArtist() + " — " + s.displayAlbum());
            art.bind(h.art, s.albumId, thumbPx);
        }
        return v;
    }

    private static final class Holder {
        ImageView art;
        TextView title;
        TextView sub;
    }
}
