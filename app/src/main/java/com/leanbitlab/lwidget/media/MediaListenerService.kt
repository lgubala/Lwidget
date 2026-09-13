package com.leanbitlab.lwidget.media

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService

/**
 * Android only reveals other apps' media sessions to an enabled notification listener, so this
 * service exists to watch them. It never reads notifications themselves.
 *
 * It redraws the media widget when a track or play state changes, and while something is playing
 * nudges the progress bar every few seconds — only when the screen is on and a widget is placed.
 */
class MediaListenerService : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private val controllers = mutableListOf<MediaController>()
    private val handler = Handler(Looper.getMainLooper())

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        track(sessions ?: emptyList())
        refresh()
    }

    // The ticker stops while the screen is off; pick it back up when it comes on
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }
    private var screenReceiverRegistered = false
    private val unlockReceiver = com.leanbitlab.lwidget.UnlockRefresher.newReceiver()

    private val progressTicker = object : Runnable {
        override fun run() {
            if (shouldTick()) {
                MediaWidgetProvider.updateAll(this@MediaListenerService)
                handler.postDelayed(this, TICK_MS)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessionManager = manager
        val component = ComponentName(this, MediaListenerService::class.java)
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            track(manager.getActiveSessions(component))
        } catch (e: SecurityException) {
            // Access was revoked between binding and connecting
        }
        if (!screenReceiverRegistered) {
            registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
            com.leanbitlab.lwidget.UnlockRefresher.register(this, unlockReceiver)
            screenReceiverRegistered = true
        }
        refresh()
    }

    override fun onListenerDisconnected() {
        release()
        MediaWidgetProvider.updateAll(this)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun track(sessions: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers.clear()
        sessions.forEach {
            it.registerCallback(controllerCallback, handler)
            controllers.add(it)
        }
    }

    private fun release() {
        handler.removeCallbacks(progressTicker)
        if (screenReceiverRegistered) {
            unregisterReceiver(screenOnReceiver)
            unregisterReceiver(unlockReceiver)
            screenReceiverRegistered = false
        }
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers.clear()
    }

    private fun refresh() {
        MediaWidgetProvider.updateAll(this)
        handler.removeCallbacks(progressTicker)
        if (shouldTick()) handler.postDelayed(progressTicker, TICK_MS)
    }

    private fun shouldTick(): Boolean {
        val playing = controllers.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        return playing && screenOn && MediaWidgetProvider.hasWidgets(this)
    }

    companion object {
        private const val TICK_MS = 5_000L
    }
}
