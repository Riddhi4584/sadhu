package com.classicpod.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fallback if AUDIO_BECOMING_NOISY is delivered to the app. MusicService also
 * registers this action dynamically while it is alive.
 */
public final class BecomingNoisyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
            Intent i = new Intent(context, MusicService.class);
            i.setAction(MusicService.ACTION_PAUSE);
            context.startService(i);
        }
    }
}
