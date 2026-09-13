package com.leanbitlab.lwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock

/**
 * Refreshes the main widget when the phone is unlocked, which is when it's actually looked at.
 *
 * Nothing announces that Garmin has synced new figures into Health Connect, so waiting for the
 * timer can leave the widget half an hour behind. Unlock broadcasts can't be declared in the
 * manifest, so this runs inside services that are already alive (the media listener and, when
 * enabled, the step service) rather than adding one of its own. Unlocks closer together than
 * [MIN_INTERVAL_MS] are ignored.
 */
object UnlockRefresher {

    private const val MIN_INTERVAL_MS = 3 * 60_000L
    @Volatile private var lastRun = 0L

    fun newReceiver(): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onUnlock(context)
    }

    fun register(context: Context, receiver: BroadcastReceiver) {
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    private fun onUnlock(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (lastRun != 0L && now - lastRun < MIN_INTERVAL_MS) return
        lastRun = now
        context.sendBroadcast(
            Intent(context, AwidgetProvider::class.java).setAction(AwidgetProvider.ACTION_REFRESH_NOW)
        )
    }
}
