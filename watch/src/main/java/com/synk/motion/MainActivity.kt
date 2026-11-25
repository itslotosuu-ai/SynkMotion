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
    private lateinit var mouseToggle: Button
    private var mouseEnabled = false
    private var activityResumed = false
    private var calibrated = false
    private var baseline = FloatArray(3)
    private var latestGyro = FloatArray(3)

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
        stateText = label("MOUSE DISABLED", 12f, Color.WHITE)
        gyroText = label("GYRO  —", 12f, Color.rgb(80, 255, 150))
        accelText = label("ACCEL —", 12f, Color.rgb(150, 190, 160))

        mouseToggle = Button(this).apply {
            setOnClickListener { setMouseEnabled(!mouseEnabled) }
        }
        updateMouseButton()

        val calibrate = Button(this).apply {
            text = "CALIBRATE"
            setOnClickListener {
                if (mouseEnabled) {
                    baseline = latestGyro.copyOf()
                    calibrated = true
                    stateText.text = "MOUSE ON / BASELINE SET"
                }
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(stateText)
        root.addView(gyroText)
        root.addView(accelText)
        root.addView(mouseToggle)
        root.addView(calibrate)
        setContentView(root)

        if (gyro == null || accelerometer == null) {
            stateText.text = "SENSOR UNAVAILABLE"
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

    private fun updateMouseButton() {
        if (!::mouseToggle.isInitialized) return
        mouseToggle.text = if (mouseEnabled) "TURN MOUSE OFF" else "TURN MOUSE ON"
    }

    private fun setMouseEnabled(enabled: Boolean) {
        mouseEnabled = enabled
        if (activityResumed) {
            if (mouseEnabled) startSensors() else stopSensors()
        }
        updateMouseButton()
        stateText.text = if (mouseEnabled) "MOUSE ENABLED" else "MOUSE DISABLED"
        if (!mouseEnabled) {
            gyroText.text = "GYRO  PAUSED"
            accelText.text = "ACCEL PAUSED"
        }
    }

    private fun startSensors() {
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun stopSensors() {
        sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (mouseEnabled) startSensors()
    }

    override fun onPause() {
        activityResumed = false
        stopSensors()
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!mouseEnabled) return
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
