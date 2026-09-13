package com.leanbitlab.lwidget

import android.content.Context
import android.content.SharedPreferences
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reads the day's figures from Health Connect, which is where Garmin Connect (and Fitbit, Samsung
 * Health, etc.) publish theirs.
 *
 * Values are cached into preferences because the widget is rendered synchronously — including on
 * the main thread for the in-app preview — while Health Connect reads are suspending IPC calls.
 * The widget therefore always draws the last known figures and never blocks waiting for a read.
 *
 * Note: Garmin's proprietary metrics (Body Battery, sleep score, stress, training load) are not
 * published to Health Connect and cannot be read here.
 */
object HealthConnectRepository {

    const val KEY_STEPS = "hc_steps"
    const val KEY_SLEEP_MINUTES = "hc_sleep_minutes"
    const val KEY_RESTING_HR = "hc_resting_hr"
    const val KEY_CALORIES = "hc_calories"
    const val KEY_LAST_SYNC = "hc_last_sync"

    /** Values older than this are refreshed on the next widget update. */
    private val CACHE_TTL = Duration.ofMinutes(15)

    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    )

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return try {
            HealthConnectClient.getOrCreate(context)
                .permissionController
                .getGrantedPermissions()
                .containsAll(PERMISSIONS)
        } catch (e: Exception) {
            false
        }
    }

    /** True when at least one of the four figures has been read at some point. */
    fun hasCachedData(prefs: SharedPreferences): Boolean =
        prefs.getLong(KEY_LAST_SYNC, 0L) > 0L

    /** [maxAgeMs] overrides the default cache lifetime; 0 always re-reads. */
    suspend fun refreshIfStale(context: Context, prefs: SharedPreferences, maxAgeMs: Long? = null) {
        val last = prefs.getLong(KEY_LAST_SYNC, 0L)
        val ttl = maxAgeMs ?: CACHE_TTL.toMillis()
        if (last > 0L && Instant.now().toEpochMilli() - last < ttl) return
        refresh(context, prefs)
    }

    suspend fun refresh(context: Context, prefs: SharedPreferences) {
        if (!isAvailable(context)) return
        val client = try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            android.util.Log.w("LWidget", "Health Connect unavailable: ${e.message}")
            return
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayStart = today.atStartOfDay(zone).toInstant()
        val now = Instant.now()
        // Noon-to-noon spans a normal night's sleep without picking up the coming night.
        val nightStart = today.minusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant()
        val nightEnd = today.atTime(LocalTime.NOON).atZone(zone).toInstant()

        val editor = prefs.edit()
        var any = false

        val aggregatedSteps = aggregateOrNull(client, setOf(StepsRecord.COUNT_TOTAL), dayStart, now)
            ?.get(StepsRecord.COUNT_TOTAL)
        // The aggregate honours Health Connect's per-type source priority list, and a source that
        // isn't on it contributes nothing — which reads as 0 even while the watch is syncing.
        // Fall back to the raw records, taking the largest single source so a phone and a watch
        // counting the same walk aren't added together.
        val steps = aggregatedSteps?.takeIf { it > 0 } ?: largestStepSource(client, dayStart, now)
        if (steps != null) {
            editor.putInt(KEY_STEPS, steps.toInt())
            any = true
        }

        aggregateOrNull(client, setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), nightStart, nightEnd)?.let { result ->
            result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.let {
                editor.putInt(KEY_SLEEP_MINUTES, it.toMinutes().toInt())
                any = true
            }
        }

        // Garmin writes one resting heart rate per day, usually in the morning; widen the window
        // so the figure doesn't vanish before the next one lands.
        aggregateOrNull(client, setOf(RestingHeartRateRecord.BPM_AVG), now.minus(Duration.ofDays(2)), now)?.let { result ->
            result[RestingHeartRateRecord.BPM_AVG]?.let {
                editor.putInt(KEY_RESTING_HR, it.toInt())
                any = true
            }
        }

        aggregateOrNull(client, setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), dayStart, now)?.let { result ->
            result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let {
                editor.putInt(KEY_CALORIES, it.inKilocalories.toInt())
                any = true
            }
        }

        if (any) {
            editor.putLong(KEY_LAST_SYNC, now.toEpochMilli())
            editor.apply()
        }
    }

    private suspend fun largestStepSource(client: HealthConnectClient, start: Instant, end: Instant): Long? = try {
        val totals = HashMap<String, Long>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken
                )
            )
            for (record in response.records) {
                val origin = record.metadata.dataOrigin.packageName
                totals[origin] = (totals[origin] ?: 0L) + record.count
            }
            pageToken = response.pageToken
            pages++
        } while (pageToken != null && pages < 20)
        totals.values.maxOrNull()
    } catch (e: Exception) {
        null
    }

    private suspend fun aggregateOrNull(
        client: HealthConnectClient,
        metrics: Set<androidx.health.connect.client.aggregate.AggregateMetric<*>>,
        start: Instant,
        end: Instant
    ) = try {
        client.aggregate(
            AggregateRequest(
                metrics = metrics,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
    } catch (e: Exception) {
        // Permission not granted for this type, or no provider — leave the cached value alone.
        null
    }
}
