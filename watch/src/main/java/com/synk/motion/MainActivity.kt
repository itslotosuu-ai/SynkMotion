package com.synk.motion

import android.app.Activity
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var gyro: Sensor? = null
    private var accelerometer: Sensor? = null
    private lateinit var gyroText: TextView
    private lateinit var accelText: TextView
    private lateinit var stateText: TextView
    private var calibrated = false
    private var baseline = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 14, 10)
        window.navigationBarColor = Color.BLACK

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(22, 18, 22, 18)
            setBackgroundColor(Color.rgb(8, 14, 10))
        }

        val title = label("SYNK MOTION", 20f, Color.rgb(80, 255, 150))
        val subtitle = label("MOTION SENSOR // WATCH", 10f, Color.LTGRAY)
        stateText = label("INITIALIZING", 12f, Color.WHITE)
        gyroText = label("GYRO  —", 12f, Color.rgb(80, 255, 150))
        accelText = label("ACCEL —", 12f, Color.rgb(150, 190, 160))

        val calibrate = Button(this).apply {
            text = "CALIBRATE"
            setOnClickListener {
                baseline = latestGyro.copyOf()
                calibrated = true
                stateText.text = "BASELINE SET"
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(stateText)
        root.addView(gyroText)
        root.addView(accelText)
        root.addView(calibrate)
        setContentView(root)

        stateText.text = buildString {
            append(if (gyro != null) "GYRO READY" else "NO GYRO")
            append("  /  ")
            append(if (accelerometer != null) "ACCEL READY" else "NO ACCEL")
        }
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(0, 7, 0, 7)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private var latestGyro = FloatArray(3)

    override fun onResume() {
        super.onResume()
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            latestGyro = v.copyOf()
            val x = v[0] - if (calibrated) baseline[0] else 0f
            val y = v[1] - if (calibrated) baseline[1] else 0f
            val z = v[2] - if (calibrated) baseline[2] else 0f
            gyroText.text = String.format(Locale.US, "GYRO  X %.2f  Y %.2f  Z %.2f", x, y, z)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val magnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            accelText.text = String.format(Locale.US, "ACCEL  %.2f m/s²", magnitude)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
