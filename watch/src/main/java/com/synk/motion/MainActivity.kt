package com.synk.motion

import android.app.Activity
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
    private var mouseEnabled = false
    private var activityResumed = false
    private var calibrated = false
    private var baseline = FloatArray(3)
    private var latestGyro = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(4, 10, 7)
        window.navigationBarColor = Color.rgb(4, 10, 7)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val frame = FrameLayout(this)
        animatedBackground = MotionBackgroundView()
        frame.addView(animatedBackground, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
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
                    baseline = latestGyro.copyOf()
                    calibrated = true
                    stateText.text = "●  MOUSE ACTIVE / CALIBRATED"
                }
            }
        }

        content.addView(title)
        content.addView(subtitle)
        val topGap = View(this)
        content.addView(topGap, LinearLayout.LayoutParams(1, 12))
        content.addView(stateText)
        val middleGap = View(this)
        content.addView(middleGap, LinearLayout.LayoutParams(1, 20))
        content.addView(gyroText)
        content.addView(accelText)
        val buttonGap = View(this)
        content.addView(buttonGap, LinearLayout.LayoutParams(1, 12))
        content.addView(mouseToggle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 42
        ))
        content.addView(calibrate, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 38
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
        activityResumed = true
        animatedBackground.start()
        if (mouseEnabled) startSensors()
    }

    override fun onPause() {
        activityResumed = false
        stopSensors()
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
        private val GREEN = Color.rgb(80, 255, 150)
        private val MUTED = Color.rgb(133, 170, 145)
    }
}
