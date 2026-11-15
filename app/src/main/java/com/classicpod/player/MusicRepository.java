package com.classicpod.player;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public final class MusicRepository {
    private final Context app;
    private final ArrayList<Song> songs = new ArrayList<Song>();
    private final ArrayList<Artist> artists = new ArrayList<Artist>();
    private final ArrayList<Album> albums = new ArrayList<Album>();
    private final ArrayList<Long> recentIds = new ArrayList<Long>();
    private final HashMap<Long, Song> extras = new HashMap<Long, Song>();
    private StreamStore streams;

    public MusicRepository(Context context) {
        this.app = context.getApplicationContext();
    }

    public void setStreamStore(StreamStore streams) {
        this.streams = streams;
        if (streams != null) {
            rememberAll(streams.saved());
            rememberAll(RadioCatalog.builtIn());
        }
    }

    public synchronized void remember(Song song) {
        if (song != null) {
            extras.put(Long.valueOf(song.id), song);
        }
    }

    public synchronized void rememberAll(List<Song> list) {
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            remember(list.get(i));
        }
    }

    public synchronized void scan() {
        songs.clear();
        Cursor c = null;
        try {
            String[] cols = new String[] {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA
            };
            c = app.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    cols,
                    MediaStore.Audio.Media.IS_MUSIC + "!=0",
                    null,
                    MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
            if (c != null) {
                int iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                while (c.moveToNext()) {
                    songs.add(new Song(
                            c.getLong(iId),
                            c.getString(iTitle),
                            c.getString(iArtist),
                            c.getString(iAlbum),
                            c.getLong(iAlbumId),
                            c.getLong(iDur),
                            c.getString(iData)));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
        rebuildAggregates();
    }

    private void rebuildAggregates() {
        artists.clear();
        albums.clear();
        HashMap<String, Integer> artistCount = new HashMap<String, Integer>();
        HashMap<Long, AlbumAcc> albumMap = new HashMap<Long, AlbumAcc>();
        for (int i = 0; i < songs.size(); i++) {
            Song s = songs.get(i);
            String an = s.displayArtist();
            Integer n = artistCount.get(an);
            artistCount.put(an, n == null ? 1 : n.intValue() + 1);
            AlbumAcc acc = albumMap.get(Long.valueOf(s.albumId));
            if (acc == null) {
                acc = new AlbumAcc();
                acc.id = s.albumId;
                acc.name = s.displayAlbum();
                acc.artist = s.displayArtist();
                acc.count = 1;
                albumMap.put(Long.valueOf(s.albumId), acc);
            } else {
                acc.count++;
            }
        }
        ArrayList<String> names = new ArrayList<String>(artistCount.keySet());
        Collections.sort(names, STRING_CMP);
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            artists.add(new Artist(name, artistCount.get(name).intValue()));
        }
        ArrayList<AlbumAcc> accs = new ArrayList<AlbumAcc>(albumMap.values());
        Collections.sort(accs, new Comparator<AlbumAcc>() {
            @Override
            public int compare(AlbumAcc a, AlbumAcc b) {
                return STRING_CMP.compare(a.name, b.name);
            }
        });
        for (int i = 0; i < accs.size(); i++) {
            AlbumAcc a = accs.get(i);
            albums.add(new Album(a.id, a.name, a.artist, a.count));
        }
    }

    public synchronized List<Song> allSongs() {
        return new ArrayList<Song>(songs);
    }

    public synchronized List<Artist> allArtists() {
        return new ArrayList<Artist>(artists);
    }

    public synchronized List<Album> allAlbums() {
        return new ArrayList<Album>(albums);
    }

    public synchronized Song byId(long id) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id == id) {
                return songs.get(i);
            }
        }
        Song extra = extras.get(Long.valueOf(id));
        if (extra != null) {
            return extra;
        }
        if (streams != null) {
            return streams.byId(id);
        }
        return null;
    }

    public synchronized List<Song> byArtist(String artist) {
        ArrayList<Song> out = new ArrayList<Song>();
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).displayArtist().equals(artist)) {
                out.add(songs.get(i));
            }
        }
        return out;
    }

    public synchronized List<Song> byAlbum(long albumId) {
        ArrayList<Song> out = new ArrayList<Song>();
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).albumId == albumId) {
                out.add(songs.get(i));
            }
        }
        return out;
    }

    public synchronized List<Song> byIds(List<Long> ids) {
        ArrayList<Song> out = new ArrayList<Song>();
        if (ids == null) {
            return out;
        }
        for (int i = 0; i < ids.size(); i++) {
            Song s = byId(ids.get(i).longValue());
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    public synchronized List<Song> search(String q) {
        ArrayList<Song> out = new ArrayList<Song>();
        if (q == null) {
            return out;
        }
        String n = q.toLowerCase(Locale.US).trim();
        if (n.length() == 0) {
            return allSongs();
        }
        for (int i = 0; i < songs.size(); i++) {
            Song s = songs.get(i);
            if (s.displayTitle().toLowerCase(Locale.US).contains(n)
                    || s.displayArtist().toLowerCase(Locale.US).contains(n)
                    || s.displayAlbum().toLowerCase(Locale.US).contains(n)) {
                out.add(s);
            }
        }
        return out;
    }

    public synchronized void recordPlayed(long songId) {
        recentIds.remove(Long.valueOf(songId));
        recentIds.add(0, Long.valueOf(songId));
        while (recentIds.size() > 80) {
            recentIds.remove(recentIds.size() - 1);
        }
    }

    public synchronized List<Song> recentlyPlayed() {
        return byIds(recentIds);
    }

    public synchronized String queueCsv(List<Song> queue) {
        if (queue == null || queue.isEmpty()) {
            return "";
        }
        ArrayList<String> ids = new ArrayList<String>(queue.size());
        for (int i = 0; i < queue.size(); i++) {
            ids.add(String.valueOf(queue.get(i).id));
        }
        return TextUtils.join(",", ids);
    }

    public synchronized List<Song> fromCsv(String csv) {
        ArrayList<Long> ids = new ArrayList<Long>();
        if (csv != null && csv.length() > 0) {
            String[] parts = csv.split(",");
            for (int i = 0; i < parts.length; i++) {
                try {
                    ids.add(Long.valueOf(Long.parseLong(parts[i].trim())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return byIds(ids);
    }

    private static final Comparator<String> STRING_CMP = new Comparator<String>() {
        @Override
        public int compare(String a, String b) {
            return a.compareToIgnoreCase(b);
        }
    };

    private static final class AlbumAcc {
        long id;
        String name;
        String artist;
        int count;
    }
}
