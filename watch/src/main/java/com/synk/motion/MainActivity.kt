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
            "HID status is shown on the MOTION screen. Host pairing is initiated from the PC, not selected from the watch's paired-device list.",
            8f, MUTED
        ))
    }

