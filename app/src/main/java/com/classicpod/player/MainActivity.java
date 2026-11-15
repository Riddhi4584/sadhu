package com.classicpod.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public final class MainActivity extends Activity implements ClickWheelView.Callback,
        MusicManager.Listener {

    private static final int SCREEN_MAIN = 0;
    private static final int SCREEN_MUSIC = 1;
    private static final int SCREEN_ARTISTS = 2;
    private static final int SCREEN_ALBUMS = 3;
    private static final int SCREEN_SONGS = 4;
    private static final int SCREEN_PLAYLISTS = 5;
    private static final int SCREEN_PLAYLIST_SONGS = 6;
    private static final int SCREEN_RECENT = 7;
    private static final int SCREEN_SETTINGS = 8;
    private static final int SCREEN_ABOUT = 9;
    private static final int SCREEN_NOW = 10;
    private static final int SCREEN_SEARCH = 11;
    private static final int SCREEN_PICK_PLAYLIST = 12;
    private static final int SCREEN_ADD_SONGS = 13;
    private static final int SCREEN_ONLINE = 14;
    private static final int SCREEN_RADIO = 15;
    private static final int SCREEN_SAVED = 16;
    private static final int SCREEN_ONLINE_SEARCH = 17;

    private ClassicPodApp app;
    private MusicService service;
    private MusicManager manager;
    private boolean bound;

    private TextView headerTitle;
    private TextView headerStatus;
    private ListView listView;
    private View nowPlaying;
    private View settingsScreen;
    private View aboutScreen;
    private View searchScreen;
    private ListView searchList;
    private EditText searchInput;
    private ImageView npArt;
    private TextView npTitle;
    private TextView npArtist;
    private TextView npAlbum;
    private TextView npElapsed;
    private TextView npDuration;
    private TextView npModes;
    private SeekBar npSeek;
    private ClickWheelView wheel;

    private final MenuAdapter menuAdapter = new MenuAdapter();
    private final SongAdapter songAdapter = new SongAdapter();
    private final SongAdapter searchAdapter = new SongAdapter();
    private final Stack<Nav> stack = new Stack<Nav>();
    private final ArrayList<Song> visibleSongs = new ArrayList<Song>();
    private boolean songMode;
    private boolean seekUser;
    private boolean scanning;
    private Playlist currentPlaylist;
    private Song pendingAdd;
    private final Handler searchHandler = new Handler();
    private int searchGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_main);
        app = (ClassicPodApp) getApplication();
        bindViews();
        stack.push(new Nav(SCREEN_MAIN, getString(R.string.app_name)));
        render();
        scanLibrary(false);
        bindService(new Intent(this, MusicService.class), connection, Context.BIND_AUTO_CREATE);
        startService(new Intent(this, MusicService.class));
    }

    private void bindViews() {
        headerTitle = (TextView) findViewById(R.id.header_title);
        headerStatus = (TextView) findViewById(R.id.header_status);
        listView = (ListView) findViewById(R.id.list_view);
        nowPlaying = findViewById(R.id.now_playing);
        settingsScreen = findViewById(R.id.settings_screen);
        aboutScreen = findViewById(R.id.about_screen);
        searchScreen = findViewById(R.id.search_screen);
        searchList = (ListView) findViewById(R.id.search_list);
        searchInput = (EditText) findViewById(R.id.search_input);
        npArt = (ImageView) findViewById(R.id.np_art);
        npTitle = (TextView) findViewById(R.id.np_title);
        npArtist = (TextView) findViewById(R.id.np_artist);
        npAlbum = (TextView) findViewById(R.id.np_album);
        npElapsed = (TextView) findViewById(R.id.np_elapsed);
        npDuration = (TextView) findViewById(R.id.np_duration);
        npModes = (TextView) findViewById(R.id.np_modes);
        npSeek = (SeekBar) findViewById(R.id.np_seek);
        wheel = (ClickWheelView) findViewById(R.id.click_wheel);
        wheel.setCallback(this);
        listView.setAdapter(menuAdapter);
        searchList.setAdapter(searchAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                current().highlight = position;
                activate();
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                current().highlight = position;
                return handleLongPress();
            }
        });
        searchList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                playSongs(searchAdapter.snapshot(), position);
            }
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (current().screen == SCREEN_ONLINE_SEARCH) {
                    scheduleOnlineSearch(s.toString());
                } else {
                    searchAdapter.setSongs(app.repo().search(s.toString()));
                    highlight(searchList, 0);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        npSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekUser = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekUser = false;
                if (manager != null) {
                    int dur = manager.duration();
                    if (dur > 0) {
                        manager.seek((int) ((seekBar.getProgress() / 1000f) * dur));
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (manager != null) {
            manager.removeListener(this);
        }
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!goBack()) {
            moveTaskToBack(true);
        }
    }

    @Override
    public void onMenu() {
        if (!goBack()) {
            moveTaskToBack(true);
        }
    }

    @Override
    public void onSelect() {
        activate();
    }

    @Override
    public void onPlay() {
        ensureManager();
        if (manager != null) {
            manager.playPause();
        }
    }

    @Override
    public void onNext() {
        ensureManager();
        if (manager != null) {
            manager.next();
        }
    }

    @Override
    public void onPrevious() {
        ensureManager();
        if (manager != null) {
            manager.previous();
        }
    }

    @Override
    public void onWheel(int steps) {
        if (current().screen == SCREEN_NOW) {
            if (manager != null) {
                manager.seekByWheel(steps * 1500);
            }
            return;
        }
        if (current().screen == SCREEN_SEARCH || current().screen == SCREEN_ONLINE_SEARCH) {
            int n = searchAdapter.getCount();
            if (n == 0) {
                return;
            }
            int idx = searchList.getCheckedItemPosition();
            if (idx < 0) {
                idx = 0;
            }
            idx = wrap(idx + steps, n);
            highlight(searchList, idx);
            return;
        }
        int n = listView.getAdapter() == null ? 0 : listView.getAdapter().getCount();
        if (n == 0) {
            return;
        }
        int idx = wrap(current().highlight + steps, n);
        current().highlight = idx;
        highlight(listView, idx);
    }

    @Override
    public void onPlaybackChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshNowPlaying();
                updateStatus();
            }
        });
    }

    @Override
    public void onPosition(final int positionMs, final int durationMs) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (seekUser) {
                    return;
                }
                npElapsed.setText(TimeUtil.format(positionMs));
                npDuration.setText(TimeUtil.format(durationMs));
                if (durationMs > 0) {
                    npSeek.setProgress((int) (1000L * positionMs / durationMs));
                }
            }
        });
    }

    private void activate() {
        Nav nav = current();
        int screen = nav.screen;
        int pos = nav.highlight;
        if (screen == SCREEN_MAIN) {
            if (pos == 0) {
                push(SCREEN_MUSIC, getString(R.string.menu_music));
            } else if (pos == 1) {
                push(SCREEN_ONLINE, getString(R.string.menu_online));
            } else if (pos == 2) {
                push(SCREEN_ARTISTS, getString(R.string.menu_artists));
            } else if (pos == 3) {
                push(SCREEN_ALBUMS, getString(R.string.menu_albums));
            } else if (pos == 4) {
                push(SCREEN_SONGS, getString(R.string.menu_songs));
            } else if (pos == 5) {
                push(SCREEN_PLAYLISTS, getString(R.string.menu_playlists));
            } else if (pos == 6) {
                push(SCREEN_RECENT, getString(R.string.menu_recent));
            } else if (pos == 7) {
                push(SCREEN_SETTINGS, getString(R.string.menu_settings));
            } else if (pos == 8) {
                push(SCREEN_ABOUT, getString(R.string.menu_about));
            }
        } else if (screen == SCREEN_ONLINE) {
            if (pos == 0) {
                push(SCREEN_RADIO, getString(R.string.menu_radio));
            } else if (pos == 1) {
                push(SCREEN_ONLINE_SEARCH, getString(R.string.menu_search_radio));
            } else if (pos == 2) {
                push(SCREEN_SAVED, getString(R.string.menu_saved_stations));
            } else if (pos == 3) {
                promptAddStream();
            }
        } else if (screen == SCREEN_MUSIC) {
            if (pos == 0) {
                push(SCREEN_ARTISTS, getString(R.string.menu_artists));
            } else if (pos == 1) {
                push(SCREEN_ALBUMS, getString(R.string.menu_albums));
            } else if (pos == 2) {
                push(SCREEN_SONGS, getString(R.string.menu_songs));
            } else if (pos == 3) {
                push(SCREEN_PLAYLISTS, getString(R.string.menu_playlists));
            } else if (pos == 4) {
                push(SCREEN_SEARCH, getString(R.string.menu_search));
            } else if (pos == 5) {
                push(SCREEN_NOW, getString(R.string.menu_now_playing));
            } else if (pos == 6) {
                push(SCREEN_ONLINE, getString(R.string.menu_online));
            }
        } else if (screen == SCREEN_ARTISTS) {
            List<Artist> artists = app.repo().allArtists();
            if (pos >= 0 && pos < artists.size()) {
                Nav n = new Nav(SCREEN_SONGS, artists.get(pos).name);
                n.artist = artists.get(pos).name;
                stack.push(n);
                render();
            }
        } else if (screen == SCREEN_ALBUMS) {
            List<Album> albums = app.repo().allAlbums();
            if (pos >= 0 && pos < albums.size()) {
                Nav n = new Nav(SCREEN_SONGS, albums.get(pos).name);
                n.albumId = albums.get(pos).albumId;
                n.filterAlbum = true;
                stack.push(n);
                render();
            }
        } else if (screen == SCREEN_PLAYLIST_SONGS) {
            if (visibleSongs.isEmpty()) {
                push(SCREEN_ADD_SONGS, "Add Songs");
                return;
            }
            playSongs(new ArrayList<Song>(visibleSongs), pos);
        } else if (screen == SCREEN_SONGS || screen == SCREEN_RECENT
                || screen == SCREEN_RADIO || screen == SCREEN_SAVED) {
            if (visibleSongs.isEmpty()) {
                return;
            }
            playSongs(new ArrayList<Song>(visibleSongs), pos);
        } else if (screen == SCREEN_PLAYLISTS) {
            if (pos == 0) {
                promptNewPlaylist();
                return;
            }
            List<Playlist> all = app.playlists().all();
            int idx = pos - 1;
            if (idx >= 0 && idx < all.size()) {
                currentPlaylist = all.get(idx);
                Nav n = new Nav(SCREEN_PLAYLIST_SONGS, currentPlaylist.name);
                stack.push(n);
                render();
            }
        } else if (screen == SCREEN_SETTINGS) {
            handleSetting(pos);
        } else if (screen == SCREEN_NOW) {
            onPlay();
        } else if (screen == SCREEN_SEARCH || screen == SCREEN_ONLINE_SEARCH) {
            int sp = searchList.getCheckedItemPosition();
            if (sp >= 0) {
                playSongs(searchAdapter.snapshot(), sp);
            }
        } else if (screen == SCREEN_PICK_PLAYLIST) {
            List<Playlist> all = app.playlists().all();
            if (pos == 0) {
                promptNewPlaylist();
                return;
            }
            int idx = pos - 1;
            if (idx >= 0 && idx < all.size() && pendingAdd != null) {
                app.playlists().addSong(all.get(idx), pendingAdd.id);
                Toast.makeText(this, "Added to " + all.get(idx).name, Toast.LENGTH_SHORT).show();
                pendingAdd = null;
                goBack();
            }
        } else if (screen == SCREEN_ADD_SONGS) {
            Song s = songAdapter.getSong(pos);
            if (s != null && currentPlaylist != null) {
                app.playlists().addSong(currentPlaylist, s.id);
                Toast.makeText(this, "Added " + s.displayTitle(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean handleLongPress() {
        Nav nav = current();
        if (nav.screen == SCREEN_PLAYLIST_SONGS) {
            push(SCREEN_ADD_SONGS, "Add Songs");
            return true;
        }
        if (nav.screen == SCREEN_SONGS || nav.screen == SCREEN_RECENT) {
            pendingAdd = songAdapter.getSong(nav.highlight);
            if (pendingAdd != null) {
                push(SCREEN_PICK_PLAYLIST, getString(R.string.playlist_add));
                return true;
            }
        }
        if (nav.screen == SCREEN_RADIO || nav.screen == SCREEN_ONLINE_SEARCH) {
            Song station = nav.screen == SCREEN_RADIO
                    ? songAdapter.getSong(nav.highlight)
                    : searchAdapter.getSong(searchList.getCheckedItemPosition());
            if (station != null && station.isOnline()) {
                app.streams().add(station);
                app.repo().remember(station);
                Toast.makeText(this, R.string.station_saved, Toast.LENGTH_SHORT).show();
                return true;
            }
        }
        if (nav.screen == SCREEN_SAVED) {
            final Song station = songAdapter.getSong(nav.highlight);
            if (station != null) {
                new AlertDialog.Builder(this)
                        .setTitle(station.displayTitle())
                        .setMessage("Remove this saved station?")
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                app.streams().remove(station);
                                render();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return true;
            }
        }
        if (nav.screen == SCREEN_PLAYLISTS && nav.highlight > 0) {
            List<Playlist> all = app.playlists().all();
            int idx = nav.highlight - 1;
            if (idx >= 0 && idx < all.size()) {
                final Playlist p = all.get(idx);
                new AlertDialog.Builder(this)
                        .setTitle(p.name)
                        .setMessage("Delete this playlist?")
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                app.playlists().delete(p);
                                render();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return true;
            }
        }
        return false;
    }

    private void handleSetting(int pos) {
        if (pos == 0) {
            app.prefs().setShuffle(!app.prefs().isShuffle());
            if (manager != null) {
                manager.setShuffle(app.prefs().isShuffle());
            }
            render();
        } else if (pos == 1) {
            if (manager != null) {
                manager.cycleRepeat();
            } else {
                app.prefs().nextRepeat();
            }
            render();
        } else if (pos == 2) {
            scanLibrary(true);
        } else if (pos == 3) {
            push(SCREEN_SEARCH, getString(R.string.menu_search));
        } else if (pos == 4) {
            push(SCREEN_NOW, getString(R.string.menu_now_playing));
        } else if (pos == 5 || pos == 8) {
            openHomePicker();
        } else if (pos == 6) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Settings unavailable", Toast.LENGTH_SHORT).show();
            }
        } else if (pos == 7) {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")));
            } catch (Exception e) {
                Toast.makeText(this, "Dialer unavailable", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void playSongs(List<Song> songs, int index) {
        if (songs == null || songs.isEmpty()) {
            Toast.makeText(this, R.string.no_song, Toast.LENGTH_SHORT).show();
            return;
        }
        Song first = songs.get(Math.max(0, Math.min(index, songs.size() - 1)));
        if (first.isOnline() && !NetworkUtil.isOnline(this)) {
            Toast.makeText(this, R.string.need_network, Toast.LENGTH_LONG).show();
            return;
        }
        ensureManager();
        if (manager == null) {
            return;
        }
        manager.playList(songs, index);
        push(SCREEN_NOW, getString(R.string.menu_now_playing));
    }

    private void ensureManager() {
        if (manager == null && service != null) {
            manager = service.manager();
            if (manager != null) {
                manager.addListener(this);
            }
        }
    }

    private void push(int screen, String title) {
        stack.push(new Nav(screen, title));
        render();
    }

    private boolean goBack() {
        if (stack.size() <= 1) {
            return false;
        }
        hideKeyboard();
        stack.pop();
        render();
        return true;
    }

    private Nav current() {
        return stack.peek();
    }

    private void animateTransition(View view, boolean show) {
        if (show) {
            if (view.getVisibility() != View.VISIBLE) {
                view.setAlpha(0f);
                view.setVisibility(View.VISIBLE);
                view.animate().alpha(1f).setDuration(160).start();
            }
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private void render() {
        Nav nav = current();
        headerTitle.setText(nav.title);
        int screen = nav.screen;
        boolean search = screen == SCREEN_SEARCH || screen == SCREEN_ONLINE_SEARCH;
        boolean list = screen != SCREEN_NOW && screen != SCREEN_ABOUT && !search;
        animateTransition(listView, list);
        animateTransition(nowPlaying, screen == SCREEN_NOW);
        animateTransition(aboutScreen, screen == SCREEN_ABOUT);
        animateTransition(searchScreen, search);
        settingsScreen.setVisibility(View.GONE);
        songMode = screen == SCREEN_SONGS || screen == SCREEN_RECENT
                || screen == SCREEN_PLAYLIST_SONGS || screen == SCREEN_ADD_SONGS
                || screen == SCREEN_RADIO || screen == SCREEN_SAVED;
        if (screen == SCREEN_SEARCH) {
            searchInput.setHint(R.string.search_hint);
            searchAdapter.setSongs(app.repo().search(searchInput.getText().toString()));
            highlight(searchList, 0);
        } else if (screen == SCREEN_ONLINE_SEARCH) {
            searchInput.setHint(R.string.search_radio_hint);
            if (!NetworkUtil.isOnline(this)) {
                Toast.makeText(this, R.string.offline_now, Toast.LENGTH_SHORT).show();
            }
            scheduleOnlineSearch(searchInput.getText().toString());
        } else if (songMode) {
            fillSongs(nav);
            listView.setAdapter(songAdapter);
            highlight(listView, nav.highlight);
        } else if (screen != SCREEN_NOW && screen != SCREEN_ABOUT) {
            listView.setAdapter(menuAdapter);
            menuAdapter.setItems(menuItems(nav));
            highlight(listView, nav.highlight);
        }
        if (screen == SCREEN_NOW) {
            refreshNowPlaying();
        }
        updateStatus();
    }

    private void fillSongs(Nav nav) {
        visibleSongs.clear();
        if (nav.screen == SCREEN_RADIO) {
            List<Song> radio = RadioCatalog.builtIn();
            app.repo().rememberAll(radio);
            visibleSongs.addAll(radio);
        } else if (nav.screen == SCREEN_SAVED) {
            List<Song> saved = app.streams().saved();
            app.repo().rememberAll(saved);
            visibleSongs.addAll(saved);
        } else if (nav.screen == SCREEN_RECENT) {
            visibleSongs.addAll(app.repo().recentlyPlayed());
        } else if (nav.screen == SCREEN_PLAYLIST_SONGS && currentPlaylist != null) {
            visibleSongs.addAll(app.repo().byIds(currentPlaylist.songIds));
        } else if (nav.artist != null) {
            visibleSongs.addAll(app.repo().byArtist(nav.artist));
        } else if (nav.filterAlbum) {
            visibleSongs.addAll(app.repo().byAlbum(nav.albumId));
        } else if (nav.screen == SCREEN_ADD_SONGS) {
            visibleSongs.addAll(app.repo().allSongs());
        } else {
            visibleSongs.addAll(app.repo().allSongs());
        }
        songAdapter.setSongs(visibleSongs);
    }

    private List<MenuAdapter.Item> menuItems(Nav nav) {
        ArrayList<MenuAdapter.Item> items = new ArrayList<MenuAdapter.Item>();
        if (nav.screen == SCREEN_MAIN) {
            items.add(new MenuAdapter.Item(getString(R.string.menu_music), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_online),
                    NetworkUtil.isOnline(this) ? "Net" : "Off", true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_artists), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_albums), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_songs), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_playlists), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_recent), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_settings), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_about), null, true));
        } else if (nav.screen == SCREEN_MUSIC) {
            items.add(new MenuAdapter.Item(getString(R.string.menu_artists), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_albums), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_songs), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_playlists), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_search), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_now_playing), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_online), null, true));
        } else if (nav.screen == SCREEN_ONLINE) {
            items.add(new MenuAdapter.Item(getString(R.string.menu_radio), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_search_radio), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_saved_stations),
                    String.valueOf(app.streams().saved().size()), true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_add_stream), null, false));
        } else if (nav.screen == SCREEN_ARTISTS) {
            List<Artist> artists = app.repo().allArtists();
            if (artists.isEmpty()) {
                items.add(new MenuAdapter.Item(getString(R.string.empty_library), null, false));
            }
            for (int i = 0; i < artists.size(); i++) {
                Artist a = artists.get(i);
                items.add(new MenuAdapter.Item(a.name, String.valueOf(a.songCount), true));
            }
        } else if (nav.screen == SCREEN_ALBUMS) {
            List<Album> albums = app.repo().allAlbums();
            if (albums.isEmpty()) {
                items.add(new MenuAdapter.Item(getString(R.string.empty_library), null, false));
            }
            for (int i = 0; i < albums.size(); i++) {
                Album a = albums.get(i);
                items.add(new MenuAdapter.Item(a.name, a.artist, true));
            }
        } else if (nav.screen == SCREEN_PLAYLISTS || nav.screen == SCREEN_PICK_PLAYLIST) {
            items.add(new MenuAdapter.Item(getString(R.string.playlist_new), null, false));
            List<Playlist> all = app.playlists().all();
            for (int i = 0; i < all.size(); i++) {
                Playlist p = all.get(i);
                items.add(new MenuAdapter.Item(p.name, String.valueOf(p.songIds.size()), true));
            }
        } else if (nav.screen == SCREEN_SETTINGS) {
            items.add(new MenuAdapter.Item(getString(R.string.settings_shuffle),
                    app.prefs().isShuffle() ? getString(R.string.on) : getString(R.string.off), false));
            items.add(new MenuAdapter.Item(getString(R.string.settings_repeat), repeatLabel(), false));
            items.add(new MenuAdapter.Item(getString(R.string.settings_scan), null, false));
            items.add(new MenuAdapter.Item(getString(R.string.menu_search), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.menu_now_playing), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.settings_home), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.settings_android), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.settings_dial), null, true));
            items.add(new MenuAdapter.Item(getString(R.string.settings_restore_home), null, true));
        }
        return items;
    }

    private String repeatLabel() {
        int r = app.prefs().getRepeat();
        if (r == Prefs.REPEAT_ALL) {
            return getString(R.string.repeat_all);
        }
        if (r == Prefs.REPEAT_ONE) {
            return getString(R.string.repeat_one);
        }
        return getString(R.string.repeat_off);
    }

    private void refreshNowPlaying() {
        if (manager == null) {
            return;
        }
        Song s = manager.current();
        if (s == null) {
            npTitle.setText(R.string.no_song);
            npArtist.setText("");
            npAlbum.setText("");
            npArt.setImageResource(R.drawable.artwork_placeholder);
            return;
        }
        npTitle.setText(s.displayTitle());
        npArtist.setText(s.displayArtist());
        if (s.isOnline()) {
            npAlbum.setText(manager.isLiveStream() ? getString(R.string.live) : s.displayAlbum());
        } else {
            npAlbum.setText(s.displayAlbum());
        }
        ArtLoader.get(this).bind(npArt, s.albumId, npArt.getWidth() > 0 ? npArt.getWidth() : 320);
        npElapsed.setText(TimeUtil.format(manager.position()));
        npDuration.setText(TimeUtil.format(manager.duration()));
        int dur = manager.duration();
        if (dur > 0) {
            npSeek.setProgress((int) (1000L * manager.position() / dur));
        }
        StringBuilder modes = new StringBuilder();
        if (app.prefs().isShuffle()) {
            modes.append("Shuffle  ");
        }
        modes.append("Repeat ").append(repeatLabel());
        if (manager.isBuffering()) {
            modes.append("  ").append(getString(R.string.buffering));
        } else if (manager.isPlaying()) {
            modes.append("  ▶");
        }
        if (s.isOnline()) {
            modes.append("  Online");
        }
        npModes.setText(modes.toString());
        npSeek.setEnabled(!manager.isLiveStream());
    }

    private void updateStatus() {
        if (manager != null && manager.current() != null) {
            if (manager.isBuffering()) {
                headerStatus.setText("…");
            } else {
                headerStatus.setText(manager.isPlaying() ? "▶" : "❚❚");
            }
        } else if (!NetworkUtil.isOnline(this)) {
            headerStatus.setText("Off");
        } else {
            headerStatus.setText("");
        }
    }

    private void highlight(ListView lv, int index) {
        if (lv.getAdapter() == null || lv.getAdapter().getCount() == 0) {
            return;
        }
        if (index < 0) {
            index = 0;
        }
        if (index >= lv.getAdapter().getCount()) {
            index = lv.getAdapter().getCount() - 1;
        }
        lv.setItemChecked(index, true);
        lv.setSelection(index);
        lv.smoothScrollToPosition(index);
        if (lv == listView) {
            current().highlight = index;
        }
    }

    private static int wrap(int v, int n) {
        if (n <= 0) {
            return 0;
        }
        int r = v % n;
        if (r < 0) {
            r += n;
        }
        return r;
    }

    private void scanLibrary(final boolean toast) {
        if (scanning) {
            return;
        }
        scanning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                app.repo().scan();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        if (manager != null) {
                            manager.restoreIfPossible();
                        }
                        render();
                        if (toast) {
                            Toast.makeText(MainActivity.this, R.string.scanned, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void promptNewPlaylist() {
        View v = getLayoutInflater().inflate(R.layout.dialog_playlist_name, null);
        final EditText input = (EditText) v.findViewById(R.id.playlist_name_input);
        new AlertDialog.Builder(this)
                .setTitle(R.string.playlist_new)
                .setView(v)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Playlist created = app.playlists().create(input.getText().toString());
                        if (pendingAdd != null) {
                            app.playlists().addSong(created, pendingAdd.id);
                            pendingAdd = null;
                            goBack();
                        } else {
                            currentPlaylist = created;
                            render();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private Runnable searchRunnable;

    private void scheduleOnlineSearch(final String query) {
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        if (query == null || query.trim().length() == 0) {
            searchAdapter.setSongs(new ArrayList<Song>());
            return;
        }
        searchRunnable = new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final List<Song> results = RadioCatalog.search(query);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (current().screen == SCREEN_ONLINE_SEARCH) {
                                        searchAdapter.setSongs(results);
                                        highlight(searchList, 0);
                                    }
                                }
                            });
                        } catch (Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (current().screen == SCREEN_ONLINE_SEARCH) {
                                        Toast.makeText(MainActivity.this, R.string.search_failed, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    }
                }).start();
            }
        };
        searchHandler.postDelayed(searchRunnable, 500);
    }

    private void promptAddStream() {
        View v = getLayoutInflater().inflate(R.layout.dialog_stream, null);
        final EditText nameInput = (EditText) v.findViewById(R.id.stream_name_input);
        final EditText urlInput = (EditText) v.findViewById(R.id.stream_url_input);
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_add_stream)
                .setView(v)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = nameInput.getText().toString().trim();
                        String url = urlInput.getText().toString().trim();
                        if (name.length() > 0 && url.length() > 0) {
                            Song streamSong = Song.stream(name, "Custom Stream", "Saved Station", url);
                            app.streams().add(streamSong);
                            Toast.makeText(MainActivity.this, R.string.station_saved, Toast.LENGTH_SHORT).show();
                            render();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openHomePicker() {
        Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
        try {
            startActivity(intent);
            Toast.makeText(this, R.string.home_help, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(home, getString(R.string.settings_home)));
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && searchInput != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            bound = true;
            service = ((MusicService.LocalBinder) binder).getService();
            manager = service.manager();
            manager.addListener(MainActivity.this);
            manager.restoreIfPossible();
            if (manager.current() != null && !manager.isPlaying()) {
                manager.playPause();
            }
            refreshNowPlaying();
            updateStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
            manager = null;
        }
    };

    private static final class Nav {
        final int screen;
        final String title;
        int highlight;
        String artist;
        long albumId;
        boolean filterAlbum;

        Nav(int screen, String title) {
            this.screen = screen;
            this.title = title;
        }
    }
}
