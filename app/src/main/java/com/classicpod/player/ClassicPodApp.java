package com.classicpod.player;

import android.app.Application;

public final class ClassicPodApp extends Application {
    private MusicRepository repository;
    private PlaylistManager playlists;
    private StreamStore streams;
    private Prefs prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new MusicRepository(this);
        playlists = new PlaylistManager(this);
        streams = new StreamStore(this);
        prefs = new Prefs(this);
        repository.setStreamStore(streams);
    }

    public MusicRepository repo() {
        return repository;
    }

    public PlaylistManager playlists() {
        return playlists;
    }

    public Prefs prefs() {
        return prefs;
    }

    public StreamStore streams() {
        return streams;
    }
}
