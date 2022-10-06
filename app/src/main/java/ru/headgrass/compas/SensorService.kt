package ru.headgrass.compas

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlin.math.round

const val NUM_OF_AXES = 3
const val ROT_MATRIX_SIZE = 9

class SensorService : Service(), SensorEventListener{

    private lateinit var sensorManager: SensorManager

    private val accelerometerReading = FloatArray(NUM_OF_AXES)
    private val magnetometerReading = FloatArray(NUM_OF_AXES)

    private val rotationMatrix = FloatArray(ROT_MATRIX_SIZE)
    private val orientationAngles = FloatArray(NUM_OF_AXES)

    private var background = false
    private val notificationActivityRequestCode = 0
    private val notificationId = 1
    private val notificationStopRequestCode = 2

    companion object {
        val KEY_ANGLE = "angle"
        val KEY_DIRECTION = "direction"
        val KEY_BACKGROUND = "background"
        val KEY_NOTIFIFCATION_ID = "notificationId"
        val KEY_ON_SENSOR_CHANGED_ACTION = "ru.headgrass.compas.ON_SENSOR_CHANGED"
        val KEY_NOTIFICATION_STOP_ACTION = "ru.headgrass.compas.NOTIFICATION_STOP"
    }

    override fun onCreate() {
        super.onCreate()

        sensorManager = getSystemService(AppCompatActivity.SENSOR_SERVICE) as SensorManager

        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL,
            SensorManager.SENSOR_DELAY_UI)
        }

        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD).also { magneticField ->
            sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_NORMAL,
            SensorManager.SENSOR_DELAY_UI)
        }

       val notification = createNotification(getString(R.string.not_available), 0.0)
       startForeground(notificationId, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.let {
            background = it.getBooleanExtra(KEY_BACKGROUND, false)
        }

       // updateOrientationAngles()
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event == null) {
            return
        }

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER){
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }
        updateOrientationAngles()
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // nothing to do
    }

    private fun updateOrientationAngles() {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)

        val orientation = SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val degrees = (Math.toDegrees(orientation.get(0).toDouble()) + 360.0) % 360.0
        val angle = round(degrees * 100) / 100

        val direction = getDirection(degrees)

        val intent = Intent()
        intent.putExtra(KEY_ANGLE, angle)
        intent.putExtra(KEY_DIRECTION, direction)
        intent.action = KEY_ON_SENSOR_CHANGED_ACTION

        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        if(background) {
            val notification = createNotification(direction, angle)
            startForeground(notificationId, notification)
        } else {
            stopForeground(true)
        }
    }

    private fun getDirection(angle: Double): String {
        var direction = ""

        if (angle >= 350 || angle <= 10)
            direction = "N"
        if (angle < 350 && angle > 280)
            direction = "NW"
        if (angle <= 280 && angle > 260)
            direction = "W"
        if (angle <= 260 && angle > 190)
            direction = "SW"
        if (angle <= 190 && angle > 170)
            direction = "S"
        if (angle <= 170 && angle > 100)
            direction = "SE"
        if (angle <= 100 && angle > 80)
            direction = "E"
        if (angle <= 80 && angle > 10)
            direction = "NE"

        return direction
    }

    class ActionListener : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent != null && intent.action != null) {
                if(intent.action.equals(KEY_NOTIFICATION_STOP_ACTION)) {
                    context?.let {
                        val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                        val locatyIntent = Intent(context, SensorService::class.java)

                        context.stopService(locatyIntent)

                        val notificationId = intent.getIntExtra(KEY_NOTIFIFCATION_ID, -1)
                        if(notificationId != -1) {
                            notificationManager.cancel(notificationId)
                        }
                    }
                }
            }
        }
    }

    private fun createNotification(direction: String, angle: Double): Notification {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                application.packageName,
                "Notifications", NotificationManager.IMPORTANCE_DEFAULT
            )

            // Configure the notification channel.
            notificationChannel.enableLights(false)
            notificationChannel.setSound(null, null)
            notificationChannel.enableVibration(false)
            notificationChannel.vibrationPattern = longArrayOf(0L)
            notificationChannel.setShowBadge(false)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notificationBuilder = NotificationCompat.Builder(baseContext, application.packageName)

        val contentIntent = PendingIntent.getActivity(
            this, notificationActivityRequestCode,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT)

        val stopNotificationIntent = Intent(this, ActionListener::class.java)
        stopNotificationIntent.action = KEY_NOTIFICATION_STOP_ACTION
        stopNotificationIntent.putExtra(KEY_NOTIFIFCATION_ID, notificationId)
        val pendingStopNotificationId =
            PendingIntent.getBroadcast(this, notificationActivityRequestCode,
                stopNotificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        notificationBuilder.setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentTitle(resources.getString(R.string.app_name))
            .setContentText("Ва обращены к $direction под углом $angle")
            .setWhen(System.currentTimeMillis())
            .setDefaults(0)
            .setVibrate(longArrayOf(0L))
            .setSound(null)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(contentIntent)
            .addAction(R.mipmap.ic_launcher_round, getString(R.string.stop_notifications), pendingStopNotificationId)
        return notificationBuilder.build()
    }
}