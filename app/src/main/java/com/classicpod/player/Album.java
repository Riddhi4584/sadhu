package com.classicpod.player;

public final class Album {
    public final long albumId;
    public final String name;
    public final String artist;
    public final int songCount;

    public Album(long albumId, String name, String artist, int songCount) {
        this.albumId = albumId;
        this.name = name;
        this.artist = artist;
        this.songCount = songCount;
    }
}
