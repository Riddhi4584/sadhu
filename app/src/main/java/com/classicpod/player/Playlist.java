package com.classicpod.player;

import java.util.ArrayList;

public final class Playlist {
    public String name;
    public final ArrayList<Long> songIds = new ArrayList<Long>();

    public Playlist(String name) {
        this.name = name;
    }
}
