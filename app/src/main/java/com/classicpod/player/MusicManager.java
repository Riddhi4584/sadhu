package com.classicpod.player;

import android.content.ContentUris;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MusicManager implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnInfoListener, AudioManager.OnAudioFocusChangeListener {

    public interface Listener {
        void onPlaybackChanged();

        void onPosition(int positionMs, int durationMs);
    }

    private static final String TAG = "ClassicPod";

    private final Context app;
    private final MusicRepository repo;
    private final Prefs prefs;
    private final AudioManager audio;
    private final ArrayList<Song> queue = new ArrayList<Song>();
    private final ArrayList<Listener> listeners = new ArrayList<Listener>();

    private MediaPlayer player;
    private int index;
    private boolean prepared;
    private boolean playWhenReady;
    private boolean ducked;
    private int resumePos;
    private boolean resumeAfterFocus;
    private boolean buffering;

    public MusicManager(Context context, MusicRepository repo, Prefs prefs) {
        this.app = context.getApplicationContext();
        this.repo = repo;
        this.prefs = prefs;
        this.audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public synchronized List<Song> queue() {
        return new ArrayList<Song>(queue);
    }

    public synchronized Song current() {
        if (index < 0 || index >= queue.size()) {
            return null;
        }
        return queue.get(index);
    }

    public synchronized int index() {
        return index;
    }

    public boolean isPlaying() {
        try {
            return player != null && prepared && player.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    public int position() {
        try {
            if (player != null && prepared) {
                return player.getCurrentPosition();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    public boolean isBuffering() {
        return buffering;
    }

    public boolean isLiveStream() {
        Song s = current();
        if (s == null || !s.isOnline()) {
            return false;
        }
        int d = duration();
        return d <= 0 || d >= 24 * 60 * 60 * 1000 - 1000;
    }

    public int duration() {
        try {
            if (player != null && prepared) {
                return player.getDuration();
            }
        } catch (Exception ignored) {
        }
        Song s = current();
        return s == null ? 0 : (int) s.durationMs;
    }

    public boolean isShuffle() {
        return prefs.isShuffle();
    }

    public int repeatMode() {
        return prefs.getRepeat();
    }

    public void setShuffle(boolean on) {
        prefs.setShuffle(on);
        notifyChanged();
    }

    public void cycleRepeat() {
        prefs.nextRepeat();
        notifyChanged();
    }

    public void playList(List<Song> songs, int startIndex) {
        if (songs == null || songs.isEmpty()) {
            return;
        }
        queue.clear();
        queue.addAll(songs);
        repo.rememberAll(songs);
        if (prefs.isShuffle()) {
            Song first = queue.get(Math.max(0, Math.min(startIndex, queue.size() - 1)));
            Collections.shuffle(queue);
            int ni = queue.indexOf(first);
            index = ni < 0 ? 0 : ni;
        } else {
            index = Math.max(0, Math.min(startIndex, queue.size() - 1));
        }
        playWhenReady = true;
        resumePos = 0;
        startCurrent();
    }

    public void playPause() {
        if (queue.isEmpty()) {
            List<Song> all = repo.allSongs();
            if (all.isEmpty()) {
                return;
            }
            playList(all, 0);
            return;
        }
        if (isPlaying()) {
            pause();
        } else {
            playWhenReady = true;
            requestFocusAndStart();
        }
    }

    public void pause() {
        playWhenReady = false;
        try {
            if (player != null && prepared && player.isPlaying()) {
                player.pause();
            }
        } catch (Exception ignored) {
        }
        persist();
        notifyChanged();
    }

    public void next() {
        if (queue.isEmpty()) {
            return;
        }
        index = (index + 1) % queue.size();
        playWhenReady = true;
        resumePos = 0;
        startCurrent();
    }

    public void previous() {
        if (queue.isEmpty()) {
            return;
        }
        if (position() > 3000) {
            seek(0);
            return;
        }
        index = index - 1;
        if (index < 0) {
            index = queue.size() - 1;
        }
        playWhenReady = true;
        resumePos = 0;
        startCurrent();
    }

    public void seek(int ms) {
        if (isLiveStream()) {
            return;
        }
        try {
            if (player != null && prepared) {
                int d = player.getDuration();
                if (ms < 0) {
                    ms = 0;
                }
                if (d > 0 && ms > d) {
                    ms = d;
                }
                player.seekTo(ms);
                notifyPosition();
            } else {
                resumePos = ms;
            }
        } catch (Exception ignored) {
        }
    }

    public void seekByWheel(int deltaMs) {
        seek(position() + deltaMs);
    }

    public void restoreIfPossible() {
        if (!queue.isEmpty()) {
            return;
        }
        List<Song> restored = repo.fromCsv(prefs.lastQueueCsv());
        if (restored.isEmpty()) {
            restored = repo.allSongs();
        }
        long lastId = prefs.lastSongId();
        int start = 0;
        for (int i = 0; i < restored.size(); i++) {
            if (restored.get(i).id == lastId) {
                start = i;
                break;
            }
        }
        if (restored.isEmpty()) {
            return;
        }
        queue.clear();
        queue.addAll(restored);
        index = start;
        resumePos = prefs.lastPosition();
        playWhenReady = false;
        notifyChanged();
    }

    public void release() {
        persist();
        abandonFocus();
        releasePlayer();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        prepared = true;
        buffering = false;
        try {
            if (resumePos > 0 && !isLiveStream()) {
                mp.seekTo(resumePos);
                resumePos = 0;
            }
            if (playWhenReady) {
                mp.start();
            }
        } catch (Exception e) {
            Log.w(TAG, "start failed", e);
        }
        notifyChanged();
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            buffering = true;
            notifyChanged();
        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            buffering = false;
            notifyChanged();
        }
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        int mode = prefs.getRepeat();
        if (mode == Prefs.REPEAT_ONE) {
            playWhenReady = true;
            resumePos = 0;
            startCurrent();
            return;
        }
        boolean last = index >= queue.size() - 1;
        if (last && mode == Prefs.REPEAT_OFF) {
            playWhenReady = false;
            persist();
            notifyChanged();
            return;
        }
        next();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.w(TAG, "player error " + what + " " + extra);
        prepared = false;
        buffering = false;
        Song song = current();
        if (song != null && song.isOnline() && queue.size() <= 1) {
            playWhenReady = false;
            notifyChanged();
            return true;
        }
        next();
        return true;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            resumeAfterFocus = isPlaying() || playWhenReady;
            pause();
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            ducked = true;
            setVolume(0.2f);
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            if (ducked) {
                ducked = false;
                setVolume(1f);
            }
            if (resumeAfterFocus) {
                resumeAfterFocus = false;
                playWhenReady = true;
                requestFocusAndStart();
            }
        }
    }

    private void startCurrent() {
        Song song = current();
        if (song == null) {
            return;
        }
        repo.recordPlayed(song.id);
        repo.remember(song);
        releasePlayer();
        prepared = false;
        buffering = song.isOnline();
        player = new MediaPlayer();
        player.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
        player.setOnInfoListener(this);
        try {
            if (song.isOnline()) {
                if (!NetworkUtil.isOnline(app)) {
                    throw new IllegalStateException("offline");
                }
                player.setDataSource(song.dataPath);
            } else {
                Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id);
                player.setDataSource(app, uri);
            }
            if (playWhenReady) {
                int result = audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    playWhenReady = false;
                }
            }
            player.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, "data source failed", e);
            buffering = false;
            if (song.isOnline()) {
                playWhenReady = false;
                notifyChanged();
                return;
            }
            next();
        }
        persist();
        notifyChanged();
    }

    private void requestFocusAndStart() {
        int result = audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return;
        }
        try {
            if (player != null && prepared) {
                player.start();
                notifyChanged();
            } else {
                startCurrent();
            }
        } catch (Exception e) {
            startCurrent();
        }
    }

    private void setVolume(float v) {
        try {
            if (player != null) {
                player.setVolume(v, v);
            }
        } catch (Exception ignored) {
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.reset();
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        prepared = false;
    }

    private void abandonFocus() {
        try {
            audio.abandonAudioFocus(this);
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        Song s = current();
        prefs.savePlayback(s == null ? -1L : s.id, position(), repo.queueCsv(queue));
    }

    void notifyChanged() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onPlaybackChanged();
        }
    }

    void notifyPosition() {
        int p = position();
        int d = duration();
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onPosition(p, d);
        }
    }
}
