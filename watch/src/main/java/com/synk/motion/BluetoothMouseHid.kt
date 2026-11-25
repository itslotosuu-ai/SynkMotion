package com.synk.motion

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Bluetooth Classic HID mouse peripheral.
 * Based on the Android Bluetooth HID Device API pattern used by
 * Google LLC's Apache-2.0 WearMouse project; independently implemented.
 */
class BluetoothMouseHid(
    private val context: Context,
    private val onStatus: (String) -> Unit
) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
    private var proxy: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var registered = false
    private val main = Handler(Looper.getMainLooper())

    private val descriptor = byteArrayOf(
        0x05,0x01, 0x09,0x02, 0xA1.toByte(),0x01,
        0x85.toByte(),0x01, 0x09,0x01, 0xA1.toByte(),0x00,
        0x05,0x09, 0x19,0x01, 0x29,0x03, 0x15,0x00, 0x25,0x01,
        0x95.toByte(),0x03, 0x75,0x01, 0x81.toByte(),0x02,
        0x95.toByte(),0x01, 0x75,0x05, 0x81.toByte(),0x01,
        0x05,0x01, 0x09,0x30, 0x09,0x31, 0x09,0x38,
        0x15,0x81.toByte(), 0x25,0x7F, 0x75,0x08, 0x95.toByte(),0x03,
        0x81.toByte(),0x06, 0xC0.toByte(),0xC0.toByte()
    )

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
            registered = isRegistered
            onStatus(if (isRegistered) "HID READY — PAIR FROM HOST" else "HID NOT REGISTERED")
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                host = device
                onStatus("CONNECTED — ${safeName(device)}")
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (host == device) host = null
                onStatus(if (registered) "HID READY — PAIR FROM HOST" else "DISCONNECTED")
            }
        }
    }

    fun start(): Boolean {
        if (Build.VERSION.SDK_INT < 28) {
            onStatus("HID REQUIRES ANDROID 9+")
            return false
        }
        val a = adapter
        if (a == null || !a.isEnabled) {
            onStatus("TURN BLUETOOTH ON")
            return false
        }
        return try {
            onStatus("STARTING BLUETOOTH HID…")
            a.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, service: BluetoothProfile) {
                    proxy = service as BluetoothHidDevice
                    val sdp = BluetoothHidDeviceAppSdpSettings(
                        "SYNK MOTION", "SYNK Bluetooth Mouse", "SYNK",
                        BluetoothHidDevice.SUBCLASS1_MOUSE, descriptor
                    )
                    val ok = proxy?.registerApp(sdp, null, null, { command -> main.post(command) }, callback) ?: false
                    if (!ok) onStatus("HID REGISTRATION FAILED")
                }
                override fun onServiceDisconnected(profile: Int) {
                    proxy = null
                    registered = false
                    host = null
                    onStatus("HID SERVICE LOST")
                }
            }, BluetoothProfile.HID_DEVICE)
            true
        } catch (_: SecurityException) {
            onStatus("BLUETOOTH PERMISSION REQUIRED")
            false
        } catch (_: Exception) {
            onStatus("HID NOT SUPPORTED BY THIS WATCH")
            false
        }
    }

    fun send(dx: Int, dy: Int, buttons: Int = 0) {
        val p = proxy ?: return
        val d = host ?: return
        if (!registered) return
        try {
            p.sendReport(d, 1, byteArrayOf(
                (buttons and 0x07).toByte(),
                dx.coerceIn(-127, 127).toByte(),
                dy.coerceIn(-127, 127).toByte(),
                0
            ))
        } catch (_: SecurityException) {
            onStatus("BLUETOOTH PERMISSION REQUIRED")
        } catch (_: Exception) {
            onStatus("REPORT SEND FAILED")
        }
    }

    fun stop() {
        try {
            val p = proxy
            if (p != null) {
                if (registered) p.unregisterApp()
                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, p)
            }
        } catch (_: Exception) { }
        proxy = null
        host = null
        registered = false
    }

    private fun safeName(device: BluetoothDevice): String =
        try { device.name ?: "HOST" } catch (_: Exception) { "HOST" }
}
