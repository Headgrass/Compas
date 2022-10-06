package ru.headgrass.compas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ru.headgrass.compas.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val direction = intent.getStringExtra(SensorService.KEY_DIRECTION)
            val angle = intent.getDoubleExtra(SensorService.KEY_ANGLE, 0.0)

            val angleWithDirection = "$angle  $direction"

            binding.textView.text = angleWithDirection
            binding.strelka.rotation = angle.toFloat() * -1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                broadcastReceiver,
                IntentFilter(SensorService.KEY_ON_SENSOR_CHANGED_ACTION)
            )
    }

    override fun onResume() {
        super.onResume()
        startForegroundServiceForSensors(false)

    }

    private fun startForegroundServiceForSensors(background: Boolean) {
        val intent = Intent(this, SensorService::class.java)
        intent.putExtra(SensorService.KEY_BACKGROUND, background)

        ContextCompat.startForegroundService(this, intent)
    }

    override fun onPause() {
        super.onPause()
        startForegroundServiceForSensors(true)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }
}