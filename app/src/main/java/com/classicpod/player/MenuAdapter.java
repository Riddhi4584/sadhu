package com.classicpod.player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class MenuAdapter extends BaseAdapter {
    public static final class Item {
        public final String title;
        public final String extra;
        public final boolean chevron;

        public Item(String title, String extra, boolean chevron) {
            this.title = title;
            this.extra = extra;
            this.chevron = chevron;
        }
    }

    private final ArrayList<Item> items = new ArrayList<Item>();

    public void setItems(List<Item> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    public Item getItemAt(int position) {
        if (position < 0 || position >= items.size()) {
            return null;
        }
        return items.get(position);
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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_menu, parent, false);
        }
        Item item = items.get(position);
        TextView title = (TextView) v.findViewById(R.id.item_title);
        TextView arrow = (TextView) v.findViewById(R.id.item_arrow);
        if (item.extra != null && item.extra.length() > 0) {
            title.setText(item.title + "  " + item.extra);
        } else {
            title.setText(item.title);
        }
        arrow.setVisibility(item.chevron ? View.VISIBLE : View.GONE);
        return v;
    }
}
