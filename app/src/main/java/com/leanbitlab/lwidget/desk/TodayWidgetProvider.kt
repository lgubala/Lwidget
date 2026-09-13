package com.leanbitlab.lwidget.desk

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.leanbitlab.lwidget.AwidgetProvider
import com.leanbitlab.lwidget.R
import com.leanbitlab.lwidget.WidgetPalette
import com.leanbitlab.lwidget.weather.BreezyWeatherFetcher
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** Left page of the fold desk: clock, date, weather, forecast and what's coming up. */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, build(context))
    }

    companion object {
        private val DAY_CELLS = listOf(
            AgendaRow(R.id.today_row_0, R.id.today_dow_0, R.id.today_day_0, R.id.today_event_0, R.id.today_rule_0),
            AgendaRow(R.id.today_row_1, R.id.today_dow_1, R.id.today_day_1, R.id.today_event_1, R.id.today_rule_1),
            AgendaRow(R.id.today_row_2, R.id.today_dow_2, R.id.today_day_2, R.id.today_event_2, R.id.today_rule_2),
            AgendaRow(R.id.today_row_3, R.id.today_dow_3, R.id.today_day_3, R.id.today_event_3, R.id.today_rule_3)
        )

        private val FORECAST_CELLS = listOf(
            Triple(R.id.today_fc_label_0, R.id.today_fc_icon_0, R.id.today_fc_temp_0),
            Triple(R.id.today_fc_label_1, R.id.today_fc_icon_1, R.id.today_fc_temp_1),
            Triple(R.id.today_fc_label_2, R.id.today_fc_icon_2, R.id.today_fc_temp_2),
            Triple(R.id.today_fc_label_3, R.id.today_fc_icon_3, R.id.today_fc_temp_3),
            Triple(R.id.today_fc_label_4, R.id.today_fc_icon_4, R.id.today_fc_temp_4)
        )

        private val RAIN = listOf(500, 501, 502, 503, 504, 511, 520, 521, 522, 531)
        private val SNOW = listOf(600, 601, 602, 611, 612, 615, 616, 620, 621, 622)
        private val STORM = listOf(210, 211, 212, 221, 230, 231, 232)

        private data class AgendaRow(val row: Int, val dow: Int, val day: Int, val event: Int, val rule: Int)

        fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_desk_today)
            val palette = WidgetPalette.resolve(context)
            val prefs = WidgetPalette.mainWidgetPrefs(context)

            views.setTextColor(R.id.today_date, palette.label)
            views.setTextColor(R.id.today_clock, palette.primary)
            views.setTextColor(R.id.today_forecast_label, palette.label)
            views.setTextColor(R.id.today_agenda_label, palette.label)

            // Tapping the clock opens the alarms, like the cover widget
            views.setOnClickPendingIntent(
                R.id.today_clock,
                DeskWidgets.activity(context, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS), 100)
            )

            bindWeather(context, views, palette, prefs.getInt("temp_unit_idx", 0) == 1)
            bindAgenda(context, views, palette)
            return views
        }

        private fun bindWeather(context: Context, views: RemoteViews, palette: WidgetPalette, fahrenheit: Boolean) {
            val weather = BreezyWeatherFetcher.fetchLocalWeather(context)
            if (weather == null) {
                views.setViewVisibility(R.id.today_weather, View.GONE)
                views.setViewVisibility(R.id.today_forecast, View.GONE)
                return
            }

            val forecasts = weather.forecasts ?: emptyList()
            val air = AwidgetProvider.displayTemp(weather.currentTemp, fahrenheit)
            // The forecast strip shows the week, but one short hint about the next wet day is worth keeping here
            val nextWet = forecasts.take(4).withIndex().firstOrNull { (_, f) ->
                f.conditionCode in RAIN || f.conditionCode in SNOW || f.conditionCode in STORM
            }
            val hint = nextWet?.let { (i, f) ->
                val kind = when (f.conditionCode) {
                    in SNOW -> "snow"
                    in STORM -> "storm"
                    else -> "rain"
                }
                val day = if (i == 0) "tomorrow" else LocalDate.now().plusDays(i + 1L)
                    .dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                " · $kind $day"
            } ?: ""

            val line = listOfNotNull(air?.let { "$it°" }, weather.currentCondition).joinToString(" ") + hint
            views.setViewVisibility(R.id.today_weather, View.VISIBLE)
            views.setTextViewText(R.id.today_weather_text, line)
            views.setTextColor(R.id.today_weather_text, palette.secondary)
            views.setImageViewResource(R.id.today_weather_icon, AwidgetProvider.weatherIconFor(weather.currentConditionCode))
            views.setInt(R.id.today_weather_icon, "setColorFilter", palette.secondary)

            val days = listOf(Triple(weather.currentConditionCode, weather.todayMaxTemp, weather.todayMinTemp)) +
                forecasts.map { Triple(it.conditionCode, it.maxTemp, it.minTemp) }
            views.setViewVisibility(R.id.today_forecast, if (days.size >= 2) View.VISIBLE else View.GONE)
            val today = LocalDate.now()
            FORECAST_CELLS.forEachIndexed { i, (labelId, iconId, tempId) ->
                val day = days.getOrNull(i)
                if (day == null) {
                    views.setTextViewText(labelId, "")
                    views.setTextViewText(tempId, "")
                    views.setViewVisibility(iconId, View.INVISIBLE)
                    return@forEachIndexed
                }
                val (code, max, min) = day
                views.setTextViewText(
                    labelId,
                    if (i == 0) context.getString(R.string.forecast_today)
                    else today.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase()
                )
                views.setTextColor(labelId, palette.label)
                views.setViewVisibility(iconId, View.VISIBLE)
                views.setImageViewResource(iconId, AwidgetProvider.weatherIconFor(code))
                views.setInt(iconId, "setColorFilter", palette.secondary)
                val hi = AwidgetProvider.displayTemp(max, fahrenheit)
                val lo = AwidgetProvider.displayTemp(min, fahrenheit)
                views.setTextViewText(tempId, if (hi != null && lo != null) "$hi/$lo" else hi?.toString() ?: "")
                views.setTextColor(tempId, palette.primary)
            }

            context.packageManager.getLaunchIntentForPackage("org.breezyweather")?.let {
                val open = DeskWidgets.activity(context, it, 101)
                views.setOnClickPendingIntent(R.id.today_weather, open)
                views.setOnClickPendingIntent(R.id.today_forecast, open)
            }
        }

        private fun bindAgenda(context: Context, views: RemoteViews, palette: WidgetPalette) {
            val calendarIntent = DeskWidgets.firstAvailable(
                context,
                listOf("org.fossify.calendar", "com.simplemobiletools.calendar", "com.samsung.android.calendar", "com.google.android.calendar", "com.android.calendar"),
                Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("content://com.android.calendar/time"))
            )
            calendarIntent?.let { views.setOnClickPendingIntent(R.id.today_agenda, DeskWidgets.activity(context, it, 102)) }

            val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val now = System.currentTimeMillis()
            val today = LocalDate.now()
            val events = if (hasPermission) {
                try {
                    AwidgetProvider.fetchCalendarEvents(context)
                        .filter { AwidgetProvider.isEventRelevant(it, now, today) }
                        .take(DAY_CELLS.size)
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()

            views.setTextViewText(
                R.id.today_agenda_empty,
                context.getString(if (hasPermission) R.string.desk_nothing_coming_up else R.string.desk_calendar_permission)
            )
            views.setTextColor(R.id.today_agenda_empty, palette.secondary)
            views.setViewVisibility(R.id.today_agenda_empty, if (events.isEmpty()) View.VISIBLE else View.GONE)

            val is24h = android.text.format.DateFormat.is24HourFormat(context)
            val timeFormat = java.time.format.DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm a")
            var previousDate: LocalDate? = null

            DAY_CELLS.forEachIndexed { i, cell ->
                val event = events.getOrNull(i)
                views.setViewVisibility(cell.row, if (event == null) View.GONE else View.VISIBLE)
                views.setViewVisibility(cell.rule, if (event == null || i == 0) View.GONE else View.VISIBLE)
                if (event == null) return@forEachIndexed

                // All-day events are stored at UTC midnight; timed ones in local time
                val date = if (event.isAllDay) Instant.ofEpochMilli(event.begin).atZone(ZoneOffset.UTC).toLocalDate()
                else Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault()).toLocalDate()
                val sameDay = date == previousDate
                previousDate = date

                views.setTextViewText(cell.dow, if (sameDay) "" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase())
                views.setTextViewText(cell.day, if (sameDay) "" else date.dayOfMonth.toString())
                views.setTextColor(cell.dow, palette.label)
                views.setTextColor(cell.day, palette.primary)

                val text = SpannableStringBuilder()
                if (!event.isAllDay) {
                    val time = Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault()).format(timeFormat)
                    text.append(time)
                    text.setSpan(ForegroundColorSpan(palette.secondary), 0, time.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text.append("  ")
                }
                text.append(event.title)
                views.setTextViewText(cell.event, text)
                views.setTextColor(cell.event, palette.primary)
            }
        }
    }
}
