package com.classicpod.player;

public final class Song {
    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final long albumId;
    public final long durationMs;
    public final String dataPath;
    public final boolean online;

    public Song(long id, String title, String artist, String album, long albumId,
                long durationMs, String dataPath) {
        this(id, title, artist, album, albumId, durationMs, dataPath, false);
    }

    public Song(long id, String title, String artist, String album, long albumId,
                long durationMs, String dataPath, boolean online) {
        this.id = id;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.albumId = albumId;
        this.durationMs = durationMs;
        this.dataPath = dataPath == null ? "" : dataPath;
        this.online = online;
    }

    public static Song stream(String title, String artist, String album, String url) {
        long id = idForUrl(url);
        return new Song(id, title, artist, album, 0L, 0L, url, true);
    }

    public static long idForUrl(String url) {
        if (url == null) {
            return -1L;
        }
        int h = url.hashCode();
        return 0x5A00000000000000L | (h & 0xffffffffL);
    }

    public boolean isOnline() {
        return online || dataPath.startsWith("http://") || dataPath.startsWith("https://");
    }

    public String displayTitle() {
        return title.length() == 0 ? "Unknown Title" : title;
    }

    public String displayArtist() {
        return artist.length() == 0 ? "Unknown Artist" : artist;
    }

    public String displayAlbum() {
        if (isOnline()) {
            return album.length() == 0 ? "Online" : album;
        }
        return album.length() == 0 ? "Unknown Album" : album;
    }
}
