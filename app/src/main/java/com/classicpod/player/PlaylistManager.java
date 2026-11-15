package com.classicpod.player;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public final class PlaylistManager {
    private static final String FILE = "playlists.json";
    private final Context app;
    private final ArrayList<Playlist> playlists = new ArrayList<Playlist>();

    public PlaylistManager(Context context) {
        this.app = context.getApplicationContext();
        load();
    }

    public synchronized List<Playlist> all() {
        return new ArrayList<Playlist>(playlists);
    }

    public synchronized Playlist get(int index) {
        if (index < 0 || index >= playlists.size()) {
            return null;
        }
        return playlists.get(index);
    }

    public synchronized Playlist byName(String name) {
        for (int i = 0; i < playlists.size(); i++) {
            if (playlists.get(i).name.equals(name)) {
                return playlists.get(i);
            }
        }
        return null;
    }

    public synchronized Playlist create(String name) {
        if (name == null) {
            name = "";
        }
        name = name.trim();
        if (name.length() == 0) {
            name = "Playlist " + (playlists.size() + 1);
        }
        Playlist p = new Playlist(name);
        playlists.add(p);
        persist();
        return p;
    }

    public synchronized void addSong(Playlist playlist, long songId) {
        if (playlist == null) {
            return;
        }
        Long id = Long.valueOf(songId);
        if (!playlist.songIds.contains(id)) {
            playlist.songIds.add(id);
            persist();
        }
    }

    public synchronized void delete(Playlist playlist) {
        playlists.remove(playlist);
        persist();
    }

    private void load() {
        playlists.clear();
        File f = new File(app.getFilesDir(), FILE);
        if (!f.exists()) {
            return;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int read = in.read(buf);
            if (read <= 0) {
                return;
            }
            JSONArray arr = new JSONArray(new String(buf, 0, read, Charset.forName("UTF-8")));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Playlist p = new Playlist(o.optString("name", "Playlist"));
                JSONArray ids = o.optJSONArray("ids");
                if (ids != null) {
                    for (int j = 0; j < ids.length(); j++) {
                        p.songIds.add(Long.valueOf(ids.getLong(j)));
                    }
                }
                playlists.add(p);
            }
        } catch (Exception ignored) {
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void persist() {
        JSONArray arr = new JSONArray();
        try {
            for (int i = 0; i < playlists.size(); i++) {
                Playlist p = playlists.get(i);
                JSONObject o = new JSONObject();
                o.put("name", p.name);
                JSONArray ids = new JSONArray();
                for (int j = 0; j < p.songIds.size(); j++) {
                    ids.put(p.songIds.get(j).longValue());
                }
                o.put("ids", ids);
                arr.put(o);
            }
            FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), FILE));
            out.write(arr.toString().getBytes(Charset.forName("UTF-8")));
            out.close();
        } catch (Exception ignored) {
        }
    }
}
