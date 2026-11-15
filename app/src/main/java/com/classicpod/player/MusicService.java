package com.classicpod.player;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.net.wifi.WifiManager;
import android.widget.Toast;

public final class MusicService extends Service implements MusicManager.Listener {
    public static final String ACTION_PLAY = "com.classicpod.player.PLAY";
    public static final String ACTION_PAUSE = "com.classicpod.player.PAUSE";
    public static final String ACTION_TOGGLE = "com.classicpod.player.TOGGLE";
    public static final String ACTION_NEXT = "com.classicpod.player.NEXT";
    public static final String ACTION_PREV = "com.classicpod.player.PREV";
    public static final String ACTION_STOP = "com.classicpod.player.STOP";

    private static final int NOTIF_ID = 21;
    private static final int PI_CONTENT = 1;
    private static final int PI_PREV = 2;
    private static final int PI_TOGGLE = 3;
    private static final int PI_NEXT = 4;

    private final LocalBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MusicManager manager;
    private MediaSession session;
    private boolean noisyRegistered;
    private boolean ticking;
    private WifiManager.WifiLock wifiLock;

    public final class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    public MusicManager manager() {
        return manager;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ClassicPodApp app = (ClassicPodApp) getApplication();
        manager = new MusicManager(this, app.repo(), app.prefs());
        manager.addListener(this);
        session = new MediaSession(this, "ClassicPod");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (!manager.isPlaying()) {
                    manager.playPause();
                }
            }

            @Override
            public void onPause() {
                manager.pause();
            }

            @Override
            public void onSkipToNext() {
                manager.next();
            }

            @Override
            public void onSkipToPrevious() {
                manager.previous();
            }

            @Override
            public void onSeekTo(long pos) {
                manager.seek((int) pos);
            }
        });
        session.setActive(true);
        IntentFilter filter = new IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(noisy, filter);
        noisyRegistered = true;
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL, "ClassicPodStream");
            wifiLock.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(action) || ACTION_PLAY.equals(action)) {
            if (manager.current() == null) {
                ClassicPodApp app = (ClassicPodApp) getApplication();
                manager.restoreIfPossible();
            }
            if (ACTION_PLAY.equals(action)) {
                if (!manager.isPlaying()) {
                    manager.playPause();
                }
            } else {
                manager.playPause();
            }
        } else if (ACTION_PAUSE.equals(action)) {
            manager.pause();
        } else if (ACTION_NEXT.equals(action)) {
            manager.next();
        } else if (ACTION_PREV.equals(action)) {
            manager.previous();
        } else if (ACTION_STOP.equals(action)) {
            manager.pause();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        updateSessionAndNotification();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        ticking = false;
        handler.removeCallbacks(tick);
        if (noisyRegistered) {
            try {
                unregisterReceiver(noisy);
            } catch (Exception ignored) {
            }
            noisyRegistered = false;
        }
        if (manager != null) {
            manager.removeListener(this);
            manager.release();
        }
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        releaseWifiLock();
        super.onDestroy();
    }

    @Override
    public void onPlaybackChanged() {
        updateWifiLock();
        updateSessionAndNotification();
        if (manager.isPlaying()) {
            startTick();
        }
    }

    private void updateWifiLock() {
        Song song = manager == null ? null : manager.current();
        boolean need = manager != null && manager.isPlaying() && song != null && song.isOnline();
        if (wifiLock == null) {
            return;
        }
        if (need && !wifiLock.isHeld()) {
            wifiLock.acquire();
        } else if (!need && wifiLock.isHeld()) {
            wifiLock.release();
        }
    }

    private void releaseWifiLock() {
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
    }

    @Override
    public void onPosition(int positionMs, int durationMs) {
        updatePlaybackState();
    }

    private void startTick() {
        if (ticking) {
            return;
        }
        ticking = true;
        handler.post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (manager == null || !manager.isPlaying()) {
                ticking = false;
                manager.notifyPosition();
                updateSessionAndNotification();
                return;
            }
            manager.notifyPosition();
            updatePlaybackState();
            handler.postDelayed(this, 500);
        }
    };

    private static final String CHANNEL_ID = "classicpod_playback";

    private void ensureNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_unused),
                    android.app.NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private int getPendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void updateSessionAndNotification() {
        Song song = manager.current();
        if (song == null) {
            stopForeground(false);
            return;
        }
        MediaMetadata.Builder meta = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, song.displayTitle())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, song.displayArtist())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, song.displayAlbum())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, manager.duration());
        Bitmap art = ArtLoader.get(this).peek(song.albumId);
        if (art == null) {
            art = ArtLoader.get(this).loadSync(song.albumId, 256);
        }
        if (art != null) {
            meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        }
        session.setMetadata(meta.build());
        updatePlaybackState();

        ensureNotificationChannel();
        boolean playing = manager.isPlaying();
        PendingIntent content = PendingIntent.getActivity(this, PI_CONTENT,
                new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                getPendingIntentFlags());
        Notification.Builder b;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(playing ? R.drawable.ic_stat_play : R.drawable.ic_stat_pause)
                .setContentTitle(song.displayTitle())
                .setContentText(song.displayArtist())
                .setContentIntent(content)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(false)
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(R.drawable.ic_stat_prev, "Prev", servicePi(PI_PREV, ACTION_PREV))
                .addAction(playing ? R.drawable.ic_stat_pause : R.drawable.ic_stat_play,
                        playing ? "Pause" : "Play",
                        servicePi(PI_TOGGLE, ACTION_TOGGLE))
                .addAction(R.drawable.ic_stat_next, "Next", servicePi(PI_NEXT, ACTION_NEXT));
        if (art != null) {
            b.setLargeIcon(art);
        }
        b.setStyle(new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        Notification n = b.build();
        if (playing) {
            startForeground(NOTIF_ID, n);
        } else {
            stopForeground(false);
            try {
                android.app.NotificationManager nm =
                        (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.notify(NOTIF_ID, n);
            } catch (Exception ignored) {
            }
        }
    }

    private void updatePlaybackState() {
        boolean playing = manager.isPlaying();
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SEEK_TO;
        PlaybackState state = new PlaybackState.Builder()
                .setActions(actions)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        manager.position(), 1f, SystemClock.elapsedRealtime())
                .build();
        session.setPlaybackState(state);
    }

    private PendingIntent servicePi(int code, String action) {
        Intent i = new Intent(this, MusicService.class);
        i.setAction(action);
        return PendingIntent.getService(this, code, i, getPendingIntentFlags());
    }

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (manager != null) {
                manager.pause();
            }
        }
    };

    public static void toast(Context ctx, String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    public static ComponentName component(Context ctx) {
        return new ComponentName(ctx, MusicService.class);
    }
}
