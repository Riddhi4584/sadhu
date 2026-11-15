package com.classicpod.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Registered because some firmware offers BOOT_COMPLETED hooks. ClassicPod does not
 * bypass the lock screen. After unlock, Android shows the default Home app — which is
 * ClassicPod only if the user chose it. Music is not auto-started on boot (saves RAM/battery).
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            return;
        }
        // No activity launch here. Home launcher resume is handled by the system.
    }
}
