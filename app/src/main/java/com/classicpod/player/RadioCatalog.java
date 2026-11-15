package com.classicpod.player;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class RadioCatalog {
    private RadioCatalog() {
    }

    public static List<Song> builtIn() {
        ArrayList<Song> list = new ArrayList<Song>();
        add(list, "Groove Salad", "SomaFM", "http://ice1.somafm.com/groovesalad-128-mp3");
        add(list, "Drone Zone", "SomaFM", "http://ice1.somafm.com/dronezone-128-mp3");
        add(list, "Space Station", "SomaFM", "http://ice1.somafm.com/spacestation-128-mp3");
        add(list, "Indie Pop Rocks", "SomaFM", "http://ice1.somafm.com/indiepop-128-mp3");
        add(list, "Left Coast 70s", "SomaFM", "http://ice1.somafm.com/seventies-128-mp3");
        add(list, "Folk Forward", "SomaFM", "http://ice1.somafm.com/folkfwd-128-mp3");
        add(list, "Beat Blender", "SomaFM", "http://ice1.somafm.com/beatblender-128-mp3");
        add(list, "Secret Agent", "SomaFM", "http://ice1.somafm.com/secretagent-128-mp3");
        add(list, "Lush", "SomaFM", "http://ice1.somafm.com/lush-128-mp3");
        add(list, "Radio Paradise", "Radio Paradise", "http://stream.radioparadise.com/mp3-128");
        return list;
    }

    public static List<Song> search(String query) throws Exception {
        ArrayList<Song> out = new ArrayList<Song>();
        String q = query == null ? "" : query.trim();
        if (q.length() == 0) {
            return out;
        }
        String encoded = Uri.encode(q);
        String url = "https://all.api.radio-browser.info/json/stations/search?name="
                + encoded
                + "&limit=30&hidebroken=true&order=clickcount&reverse=true";
        String body = HttpUtil.get(url);
        JSONArray arr = new JSONArray(body);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String stream = o.optString("url_resolved", "");
            if (stream.length() == 0) {
                stream = o.optString("url", "");
            }
            if (stream.length() == 0) {
                continue;
            }
            String name = o.optString("name", "Station");
            String country = o.optString("country", "Internet");
            String codec = o.optString("codec", "");
            String artist = country;
            if (codec.length() > 0) {
                artist = country + " · " + codec;
            }
            out.add(Song.stream(name, artist, "Online Radio", stream));
        }
        return out;
    }

    private static void add(ArrayList<Song> list, String title, String artist, String url) {
        list.add(Song.stream(title, artist, "Internet Radio", url));
    }
}
