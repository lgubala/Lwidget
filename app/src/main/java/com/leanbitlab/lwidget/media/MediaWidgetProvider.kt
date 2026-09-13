package com.leanbitlab.lwidget.media

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import com.leanbitlab.lwidget.ColorResolver
import com.leanbitlab.lwidget.R

/** A 4×1 Blueprint-styled player for whatever app is currently playing media. */
class MediaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in CONTROL_ACTIONS) return

        activeController(context)?.let { controller ->
            val controls = controller.transportControls
            when (action) {
                ACTION_PLAY_PAUSE ->
                    if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
                ACTION_NEXT -> controls.skipToNext()
                ACTION_PREVIOUS -> controls.skipToPrevious()
            }
        }
        updateAll(context)
    }

    companion object {
        private const val ACTION_PLAY_PAUSE = "com.leanbitlab.lwidget.media.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.leanbitlab.lwidget.media.NEXT"
        private const val ACTION_PREVIOUS = "com.leanbitlab.lwidget.media.PREVIOUS"
        private val CONTROL_ACTIONS = setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS)

        private const val PROGRESS_MAX = 1000

        fun hasAccess(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun hasWidgets(context: Context): Boolean = widgetIds(context).isNotEmpty()

        private fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, MediaWidgetProvider::class.java))

        /** The session that's playing, or else the most recently active one. */
        fun activeController(context: Context): MediaController? {
            if (!hasAccess(context)) return null
            return try {
                val sessions = context.getSystemService(MediaSessionManager::class.java)
                    ?.getActiveSessions(ComponentName(context, MediaListenerService::class.java))
                    ?: return null
                sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
            } catch (e: SecurityException) {
                null
            }
        }

        fun updateAll(context: Context) {
            val ids = widgetIds(context)
            if (ids.isEmpty()) return
            AppWidgetManager.getInstance(context).updateAppWidget(ids, build(context))
        }

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_media_blueprint)

            // Follow the main widget's colours and theme so the two read as a set. Its settings are
            // saved per widget, so borrow the first placed Lwidget's, falling back to the defaults.
            val globalPrefs = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
            val mainWidgetId = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, com.leanbitlab.lwidget.AwidgetProvider::class.java))
                .firstOrNull()
            val prefs = if (mainWidgetId != null) {
                com.leanbitlab.lwidget.FallbackPreferences(
                    context.getSharedPreferences("com.leanbitlab.lwidget.PREFS_$mainWidgetId", Context.MODE_PRIVATE),
                    globalPrefs
                )
            } else globalPrefs
            val systemNight = (android.content.res.Resources.getSystem().configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isLight = when (prefs.getInt("theme_mode", if (prefs.getBoolean("use_system_theme", true)) 0 else 2)) {
                1 -> true
                2 -> false
                else -> !systemNight
            }
            val dynamic = prefs.getBoolean("use_dynamic_colors", true)
            val primary = ColorResolver.resolveColor(context, prefs, dynamic, prefs.getInt("text_color_primary_idx", 0), true, isLight)
            val secondary = ColorResolver.resolveColor(context, prefs, dynamic, prefs.getInt("text_color_secondary_idx", 0), false, isLight)
            val label = android.graphics.Color.argb(
                (android.graphics.Color.alpha(secondary) * 0.6f).toInt(),
                android.graphics.Color.red(secondary), android.graphics.Color.green(secondary), android.graphics.Color.blue(secondary)
            )

            for (id in listOf(R.id.media_corner_tl, R.id.media_corner_tr, R.id.media_corner_bl, R.id.media_corner_br)) {
                views.setInt(id, "setColorFilter", primary)
                views.setInt(id, "setImageAlpha", 210)
            }
            views.setInt(R.id.media_grid, "setBackgroundResource", if (isLight) R.drawable.bp_grid_tile_dark else R.drawable.bp_grid_tile)
            views.setTextColor(R.id.media_app, label)
            views.setInt(R.id.media_app_icon, "setColorFilter", label)
            views.setTextColor(R.id.media_title, primary)
            views.setTextColor(R.id.media_artist, secondary)
            for (id in listOf(R.id.media_prev, R.id.media_play, R.id.media_next)) {
                views.setInt(id, "setColorFilter", primary)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(R.id.media_progress, "setProgressTintList", ColorStateList.valueOf(primary))
            }

            if (!hasAccess(context)) {
                views.setTextViewText(R.id.media_app, context.getString(R.string.media_label))
                views.setTextViewText(R.id.media_title, context.getString(R.string.media_needs_access_title))
                views.setTextViewText(R.id.media_artist, context.getString(R.string.media_needs_access_body))
                views.setViewVisibility(R.id.media_controls, View.GONE)
                views.setViewVisibility(R.id.media_progress, View.GONE)
                views.setOnClickPendingIntent(R.id.media_root, openActivity(context, listenerSettingsIntent(context), 0))
                return views
            }

            views.setViewVisibility(R.id.media_controls, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.media_prev, controlIntent(context, ACTION_PREVIOUS, 1))
            views.setOnClickPendingIntent(R.id.media_play, controlIntent(context, ACTION_PLAY_PAUSE, 2))
            views.setOnClickPendingIntent(R.id.media_next, controlIntent(context, ACTION_NEXT, 3))

            val controller = activeController(context)
            val metadata = controller?.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            if (controller == null || title.isNullOrBlank()) {
                views.setTextViewText(R.id.media_app, context.getString(R.string.media_label))
                views.setTextViewText(R.id.media_title, context.getString(R.string.media_nothing_playing))
                views.setTextViewText(R.id.media_artist, "")
                views.setImageViewResource(R.id.media_play, R.drawable.ic_media_play)
                views.setViewVisibility(R.id.media_progress, View.GONE)
                controller?.packageName?.let { pkg ->
                    context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                        views.setOnClickPendingIntent(R.id.media_text, openActivity(context, it, 4))
                    }
                }
                return views
            }

            val appName = try {
                val info = context.packageManager.getApplicationInfo(controller.packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                controller.packageName
            }
            views.setTextViewText(R.id.media_app, appName.uppercase())
            views.setTextViewText(R.id.media_title, title)
            views.setTextViewText(
                R.id.media_artist,
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
                    ?: ""
            )

            val state = controller.playbackState
            val playing = state?.state == PlaybackState.STATE_PLAYING
            views.setImageViewResource(R.id.media_play, if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play)

            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            if (state != null && duration > 0) {
                // The reported position is a snapshot; project it forward to now while playing
                var position = state.position
                if (playing && state.lastPositionUpdateTime > 0) {
                    position += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
                }
                val progress = (position.coerceIn(0, duration) * PROGRESS_MAX / duration).toInt()
                views.setViewVisibility(R.id.media_progress, View.VISIBLE)
                views.setProgressBar(R.id.media_progress, PROGRESS_MAX, progress, false)
            } else {
                views.setViewVisibility(R.id.media_progress, View.GONE)
            }

            val open = controller.sessionActivity
                ?: context.packageManager.getLaunchIntentForPackage(controller.packageName)?.let { openActivity(context, it, 4) }
            open?.let { views.setOnClickPendingIntent(R.id.media_text, it) }
            return views
        }

        private fun controlIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, MediaWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        private fun openActivity(context: Context, intent: Intent, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode,
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        /** Straight to Lwidget's toggle where the system supports it, else the full list. */
        fun listenerSettingsIntent(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, MediaListenerService::class.java).flattenToString()
                )
            } else {
                Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
    }
}
