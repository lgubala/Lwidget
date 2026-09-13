package com.leanbitlab.lwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color

/**
 * Colours for the companion widgets (media player, fold desk), taken from the main Lwidget so
 * every widget on the home screen follows the same palette and light/dark choice.
 */
data class WidgetPalette(
    val primary: Int,
    val secondary: Int,
    /** Secondary at reduced strength, for small caps labels. */
    val label: Int,
    val isLight: Boolean
) {
    companion object {
        fun resolve(context: Context): WidgetPalette {
            val prefs = mainWidgetPrefs(context)
            val systemNight = (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val isLight = when (prefs.getInt("theme_mode", if (prefs.getBoolean("use_system_theme", true)) 0 else 2)) {
                1 -> true
                2 -> false
                else -> !systemNight
            }
            val dynamic = prefs.getBoolean("use_dynamic_colors", true)
            val primary = ColorResolver.resolveColor(context, prefs, dynamic, prefs.getInt("text_color_primary_idx", 0), true, isLight)
            val secondary = ColorResolver.resolveColor(context, prefs, dynamic, prefs.getInt("text_color_secondary_idx", 0), false, isLight)
            val label = Color.argb(
                (Color.alpha(secondary) * 0.6f).toInt(),
                Color.red(secondary), Color.green(secondary), Color.blue(secondary)
            )
            return WidgetPalette(primary, secondary, label, isLight)
        }

        /** Settings are saved per widget; borrow the first placed Lwidget's, else the defaults. */
        fun mainWidgetPrefs(context: Context): SharedPreferences {
            val global = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
            val id = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, AwidgetProvider::class.java))
                .firstOrNull() ?: return global
            return FallbackPreferences(
                context.getSharedPreferences("com.leanbitlab.lwidget.PREFS_$id", Context.MODE_PRIVATE),
                global
            )
        }
    }
}
