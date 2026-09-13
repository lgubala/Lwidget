package com.leanbitlab.lwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class StepCounterService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    
    private lateinit var prefs: SharedPreferences
    private var lastTotalSteps: Float = 0f
    private var baselineSteps: Float = 0f
    private var stepDate: String = ""

    private var lastBatteryPct = -1
    private val unlockReceiver = UnlockRefresher.newReceiver()

    private val updateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    // This fires on every voltage/temperature tick — several times a minute — and
                    // each one used to rebuild the whole widget. Only the percentage is displayed,
                    // so redraw when that actually moves.
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                    if (level < 0 || scale <= 0) return
                    val pct = level * 100 / scale
                    if (pct == lastBatteryPct) return
                    lastBatteryPct = pct
                }
                Intent.ACTION_TIME_TICK -> {}
                else -> return
            }
            val updateIntent = Intent(context, AwidgetProvider::class.java).apply {
                this.action = AwidgetProvider.ACTION_BATTERY_UPDATE
            }
            context.sendBroadcast(updateIntent)
        }
    }
    
    companion object {
        const val CHANNEL_ID = "StepCounterChannel"
        const val NOTIFICATION_ID = 42100
        const val ACTION_STEP_UPDATE = "com.leanbitlab.lwidget.ACTION_STEP_UPDATE"

        /**
         * The service is only worth running for Keep Alive, or for a widget that counts steps with
         * the phone's own sensor. Steps read from Health Connect come from the watch instead.
         * Settings are per widget, so every placed widget is checked.
         */
        fun isNeeded(context: Context): Boolean {
            val global = context.getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
            val ids = android.appwidget.AppWidgetManager.getInstance(context)
                .getAppWidgetIds(android.content.ComponentName(context, AwidgetProvider::class.java))
            val scopes: List<SharedPreferences> = if (ids.isEmpty()) {
                listOf(global)
            } else {
                ids.map { id ->
                    FallbackPreferences(
                        context.getSharedPreferences("com.leanbitlab.lwidget.PREFS_" + id, Context.MODE_PRIVATE),
                        global
                    )
                }
            }
            return scopes.any {
                it.getBoolean("keep_alive", false) ||
                    (it.getBoolean("show_steps", false) && !it.getBoolean("use_health_connect", false))
            }
        }

        /** Starts or stops the service to match the current settings. */
        fun sync(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            // FOREGROUND_SERVICE_TYPE_HEALTH requires ACTIVITY_RECOGNITION at runtime
            val hasActivityPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (isNeeded(context) && hasActivityPerm) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    android.util.Log.e("LWidget", "Failed to start StepCounterService: " + e.message)
                }
            } else {
                context.stopService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)
        lastTotalSteps = prefs.getFloat("last_total_steps", 0f)
        baselineSteps = prefs.getFloat("step_baseline", 0f)
        stepDate = prefs.getString("step_date", "") ?: ""

        createNotificationChannel()

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            android.util.Log.e("LWidget", "Cannot start foreground service", e)
            stopSelf()
            return
        }

        // Started from a stale path, or restarted as sticky, after the settings changed
        if (!isNeeded(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        
        val showSteps = prefs.getBoolean("show_steps", false)
        val keepAlive = prefs.getBoolean("keep_alive", false)
        
        // If steps is explicitly enabled, require ACTIVITY_RECOGNITION
        if (showSteps && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // If keep_alive is also on, continue without steps; otherwise stop
            if (!keepAlive) {
                stopSelf()
                return
            }
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        
        // Always register the step sensor if available and permission is granted
        // (even in keep-alive-only mode, counting in background doesn't hurt)
        val hasActivityPerm = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasActivityPerm) {
            stepSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
        UnlockRefresher.register(this, unlockReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sticky so it restarts if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(updateReceiver)
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            // Receiver might not have been registered if initialization stopped early
        }
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
        }
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        val totalSteps = event.values[0]

        // Hardware rebooted and reset the total steps to 0
        if (totalSteps < lastTotalSteps) {
            baselineSteps = totalSteps - (lastTotalSteps - baselineSteps)
            prefs.edit().putFloat("step_baseline", baselineSteps).apply()
        }
        
        lastTotalSteps = totalSteps
        prefs.edit().putFloat("last_total_steps", totalSteps).apply()

        // Daily reset logic
        val today = java.time.LocalDate.now().toString()

        if (stepDate != today) {
            stepDate = today
            baselineSteps = totalSteps
            prefs.edit()
                .putString("step_date", today)
                .putFloat("step_baseline", totalSteps)
                .apply()
        }
        
        // Notify widget provider to update
        val updateIntent = Intent(this, AwidgetProvider::class.java).apply {
            action = ACTION_STEP_UPDATE
        }
        sendBroadcast(updateIntent)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun createNotification(): Notification {
        val settingsIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, settingsIntent, PendingIntent.FLAG_IMMUTABLE)

        val showSteps = prefs.getBoolean("show_steps", false)
        
        val title = if (showSteps) "Lwidget Step Counter" else "Lwidget Keep Alive"
        val text = if (showSteps) "Counting steps in the background" else "Keeping widget updated"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_steps) 
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            
        // Use a less intrusive priority
        builder.setPriority(NotificationCompat.PRIORITY_MIN)
            
        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Step Counter Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps the step counter running in the background."
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
