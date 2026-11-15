package com.classicpod.player;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class StreamStore {
    private static final String FILE = "streams.json";
    private final Context app;
    private final ArrayList<Song> saved = new ArrayList<Song>();

    public StreamStore(Context context) {
        this.app = context.getApplicationContext();
        load();
    }

    public synchronized List<Song> saved() {
        return new ArrayList<Song>(saved);
    }

    public synchronized void add(Song song) {
        if (song == null || !song.isOnline()) {
            return;
        }
        for (int i = 0; i < saved.size(); i++) {
            if (saved.get(i).id == song.id || saved.get(i).dataPath.equals(song.dataPath)) {
                return;
            }
        }
        saved.add(song);
        persist();
    }

    public synchronized void remove(Song song) {
        if (song == null) {
            return;
        }
        for (int i = saved.size() - 1; i >= 0; i--) {
            if (saved.get(i).id == song.id) {
                saved.remove(i);
            }
        }
        persist();
    }

    public synchronized Song byId(long id) {
        for (int i = 0; i < saved.size(); i++) {
            if (saved.get(i).id == id) {
                return saved.get(i);
            }
        }
        List<Song> built = RadioCatalog.builtIn();
        for (int i = 0; i < built.size(); i++) {
            if (built.get(i).id == id) {
                return built.get(i);
            }
        }
        return null;
    }

    private void load() {
        saved.clear();
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
            JSONArray arr = new JSONArray(new String(buf, 0, read, "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                saved.add(Song.stream(
                        o.optString("title"),
                        o.optString("artist"),
                        o.optString("album", "Saved Station"),
                        o.optString("url")));
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
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < saved.size(); i++) {
                Song s = saved.get(i);
                JSONObject o = new JSONObject();
                o.put("title", s.title);
                o.put("artist", s.artist);
                o.put("album", s.album);
                o.put("url", s.dataPath);
                arr.put(o);
            }
            FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), FILE));
            out.write(arr.toString().getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
        }
    }
}
