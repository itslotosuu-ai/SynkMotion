package com.synk.motion

import android.app.Activity
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.content.res.ColorStateList
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var gyro: Sensor? = null
    private var accelerometer: Sensor? = null
    private lateinit var gyroText: TextView
    private lateinit var accelText: TextView
    private lateinit var stateText: TextView
    private lateinit var mouseToggle: Button
    private lateinit var animatedBackground: MotionBackgroundView
    private lateinit var homePanel: LinearLayout
    private lateinit var devicesPanel: LinearLayout
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var selectedDeviceAddress: String? = null
    private var selectedDeviceName: String? = null
    private var mouseEnabled = false
    private var activityResumed = false
    private var calibrated = false
    private var hid: BluetoothMouseHid? = null
    private var lastMotionAt = 0L
    private var motionSensitivity = 22f
    private var baseline = FloatArray(3)
    private var latestGyro = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(4, 10, 7)
        window.navigationBarColor = Color.rgb(4, 10, 7)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        val savedDevice = getSharedPreferences("synk_motion", MODE_PRIVATE)
        selectedDeviceAddress = savedDevice.getString("selected_device_address", null)
        selectedDeviceName = savedDevice.getString("selected_device_name", null)

        val frame = FrameLayout(this)
        animatedBackground = MotionBackgroundView()
        frame.addView(animatedBackground, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
        }
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        homePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(10, 12, 10, 12)
        }
        devicesPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            visibility = View.GONE
        }

        val title = label("SYNK MOTION", 19f, GREEN).apply {
            letterSpacing = 0.14f
        }
        val subtitle = label("MOTION CONTROL SYSTEM", 8f, MUTED).apply {
            letterSpacing = 0.18f
        }
        stateText = label("●  MOUSE STANDBY", 10f, Color.WHITE).apply {
            setPadding(0, 12, 0, 10)
            letterSpacing = 0.08f
        }
        gyroText = label("GYRO  X —  Y —  Z —", 9f, GREEN)
        accelText = label("ACCEL  — m/s²", 9f, MUTED)

        mouseToggle = Button(this).apply {
            textSize = 10f
            isAllCaps = false
            letterSpacing = 0.08f
            setTextColor(Color.rgb(3, 18, 10))
            backgroundTintList = ColorStateList.valueOf(GREEN)
            setPadding(12, 0, 12, 0)
            setOnClickListener { setMouseEnabled(!mouseEnabled) }
        }
        updateMouseButton()

        val calibrate = Button(this).apply {
            text = "Calibrate"
            textSize = 10f
            isAllCaps = false
            letterSpacing = 0.08f
            setTextColor(GREEN)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(24, 42, 32))
            setOnClickListener {
                if (mouseEnabled) {
                    this@MainActivity.baseline = latestGyro.copyOf()
                    calibrated = true
                    stateText.text = "●  MOUSE ACTIVE / CALIBRATED"
                }
            }
        }

        homePanel.addView(title)
        homePanel.addView(subtitle)
        val topGap = View(this)
        homePanel.addView(topGap, LinearLayout.LayoutParams(1, 12))
        homePanel.addView(stateText)
        val middleGap = View(this)
        homePanel.addView(middleGap, LinearLayout.LayoutParams(1, 20))
        homePanel.addView(gyroText)
        homePanel.addView(accelText)
        val buttonGap = View(this)
        homePanel.addView(buttonGap, LinearLayout.LayoutParams(1, 12))
        homePanel.addView(mouseToggle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 42
        ))
        homePanel.addView(calibrate, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 38
        ))

        val homeTab = tabButton("MOTION")
        val devicesTab = tabButton("DEVICES")
        homeTab.setOnClickListener {
            homePanel.visibility = View.VISIBLE
            devicesPanel.visibility = View.GONE
        }
        devicesTab.setOnClickListener {
            homePanel.visibility = View.GONE
            devicesPanel.visibility = View.VISIBLE
            refreshDevicePanel()
        }
        tabs.addView(homeTab, LinearLayout.LayoutParams(0, 42, 1f))
        tabs.addView(devicesTab, LinearLayout.LayoutParams(0, 42, 1f))
        content.addView(tabs)
        content.addView(homePanel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        content.addView(devicesPanel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        frame.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(frame)

        if (gyro == null || accelerometer == null) {
            stateText.text = "●  SENSOR UNAVAILABLE"
        }
    }

    private fun startHid() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        if (hid != null) return
        hid = BluetoothMouseHid(this) { status ->
            runOnUiThread {
                if (::stateText.isInitialized) {
                    stateText.text = "●  $status"
                    stateText.setTextColor(if (status.startsWith("CONNECTED") || status.startsWith("HID READY")) GREEN else Color.WHITE)
                }
            }
        }
        hid?.start()
    }

    private fun tabButton(value: String) = Button(this).apply {
        text = value
        textSize = 10f
        isAllCaps = false
        setTextColor(GREEN)
        backgroundTintList = ColorStateList.valueOf(Color.rgb(18, 38, 26))
    }

    private fun deviceActionButton(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        textSize = 10f
        isAllCaps = false
        setTextColor(GREEN)
        backgroundTintList = ColorStateList.valueOf(Color.rgb(24, 42, 32))
        setOnClickListener { action() }
    }

    private fun refreshDevicePanel() {
        if (!::devicesPanel.isInitialized) return
        devicesPanel.removeAllViews()
        devicesPanel.addView(label("BLUETOOTH HID MOUSE", 14f, GREEN))
        devicesPanel.addView(label("The watch acts as a mouse peripheral.", 9f, MUTED))
        devicesPanel.addView(label("1. Enable Bluetooth on the watch.", 9f, Color.WHITE))
        devicesPanel.addView(label("2. Open Bluetooth settings on your PC.", 9f, Color.WHITE))
        devicesPanel.addView(label("3. Pair with SYNK MOTION.", 9f, Color.WHITE))
        devicesPanel.addView(label("4. Return here and enable the mouse.", 9f, Color.WHITE))
        devicesPanel.addView(deviceActionButton("OPEN BLUETOOTH SETTINGS") {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        })
        devicesPanel.addView(label(
            "HID status is shown on the MOTION screen. Pairing is initiated from the PC, not by selecting a paired device on the watch.",
            8f, MUTED
        ))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startHid()
            refreshDevicePanel()
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) refreshDevicePanel()
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(0, 4, 0, 4)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun updateMouseButton() {
        if (!::mouseToggle.isInitialized) return
        mouseToggle.text = if (mouseEnabled) "DISABLE MOUSE" else "ENABLE MOUSE"
    }

    private fun setMouseEnabled(enabled: Boolean) {
        mouseEnabled = enabled
        if (activityResumed) {
            if (mouseEnabled) startSensors() else stopSensors()
        }
        lastMotionAt = 0L
        updateMouseButton()
        stateText.text = if (mouseEnabled) "●  MOUSE ACTIVE" else "●  MOUSE STANDBY"
        stateText.setTextColor(if (mouseEnabled) GREEN else Color.WHITE)
        animatedBackground.active = enabled
        if (!mouseEnabled) {
            gyroText.text = "GYRO  PAUSED"
            accelText.text = "ACCEL  PAUSED"
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
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activityResumed = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_PERMISSION)
        } else {
            startHid()
        }
        if (::devicesPanel.isInitialized && devicesPanel.visibility == View.VISIBLE) refreshDevicePanel()
        animatedBackground.start()
        if (mouseEnabled) startSensors()
    }

    override fun onPause() {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activityResumed = false
        stopSensors()
        hid?.stop()
        hid = null
        animatedBackground.stop()
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
            val now = event.timestamp
            if (mouseEnabled && lastMotionAt != 0L) {
                val dt = ((now - lastMotionAt) / 1_000_000_000f).coerceIn(0f, 0.05f)
                val dx = (-y * dt * motionSensitivity).toInt()
                val dy = (x * dt * motionSensitivity).toInt()
                if (dx != 0 || dy != 0) hid?.send(dx, dy)
            }
            lastMotionAt = now
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val magnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            accelText.text = String.format(Locale.US, "ACCEL  %.2f m/s²", magnitude)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private inner class MotionBackgroundView : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arcRect = RectF()
        private var started = false
        var active = false

        fun start() {
            started = true
            invalidate()
        }

        fun stop() {
            started = false
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val radius = minOf(w, h) * 0.39f
            val time = if (started) SystemClock.uptimeMillis() / 1000f else 0f
            val speed = if (active) 1.0f else 0.32f

            canvas.drawColor(Color.rgb(4, 10, 7))
            paint.shader = RadialGradient(
                cx, cy, radius * 1.8f,
                intArrayOf(Color.rgb(15, 48, 29), Color.rgb(7, 19, 12), Color.rgb(4, 10, 7)),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = Color.argb(38, 80, 255, 150)
            canvas.drawCircle(cx, cy, radius * 1.24f, paint)
            canvas.drawCircle(cx, cy, radius * 0.76f, paint)

            paint.strokeWidth = 2f
            paint.color = Color.argb(if (active) 190 else 90, 80, 255, 150)
            arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(arcRect, time * 35f * speed, 92f, false, paint)
            canvas.drawArc(arcRect, 180f - time * 22f * speed, 54f, false, paint)

            paint.strokeWidth = 1.5f
            paint.color = Color.argb(95, 80, 255, 150)
            arcRect.set(cx - radius * 0.88f, cy - radius * 0.88f, cx + radius * 0.88f, cy + radius * 0.88f)
            canvas.drawArc(arcRect, -time * 48f * speed, 38f, false, paint)
            canvas.drawArc(arcRect, 140f + time * 48f * speed, 70f, false, paint)

            paint.strokeWidth = 1f
            paint.color = Color.argb(65, 130, 255, 175)
            for (i in 0 until 48) {
                val angle = Math.toRadians(i * 7.5)
                val inner = radius * if (i % 4 == 0) 1.08f else 1.14f
                val outer = radius * 1.20f
                canvas.drawLine(
                    cx + cos(angle).toFloat() * inner,
                    cy + sin(angle).toFloat() * inner,
                    cx + cos(angle).toFloat() * outer,
                    cy + sin(angle).toFloat() * outer,
                    paint
                )
            }

            val orbitAngle = time * 0.8f * speed
            val dotX = cx + cos(orbitAngle.toDouble()).toFloat() * radius * 1.02f
            val dotY = cy + sin(orbitAngle.toDouble()).toFloat() * radius * 1.02f
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(if (active) 255 else 145, 80, 255, 150)
            canvas.drawCircle(dotX, dotY, if (active) 4f else 2.5f, paint)

            val pulse = 1f + (sin(time * 2.2f).toFloat() + 1f) * 0.035f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = Color.argb(if (active) 125 else 55, 80, 255, 150)
            canvas.drawCircle(cx, cy, radius * 0.48f * pulse, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(if (active) 24 else 12, 80, 255, 150)
            canvas.drawCircle(cx, cy, radius * 0.42f * pulse, paint)

            if (started) postInvalidateDelayed(32)
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSION = 701
        private const val REQUEST_ENABLE_BT = 702
        private val GREEN = Color.rgb(80, 255, 150)
        private val MUTED = Color.rgb(133, 170, 145)
    }
}
