package com.garidoctor.app.obd

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * USB ELM327 cables plugged in through an OTG adapter. They are USB-serial chips (FTDI FT232,
 * CH340/CH341, CP210x, PL2303 or CDC-ACM), all handled by usb-serial-for-android's default prober.
 */
object UsbElm {
    /** ELM327 ships at 38400; many clones are set to 115200, a few old ones to 9600. */
    val BAUD_RATES = listOf(38400, 115200, 9600)
    private const val ACTION_PERMISSION = "com.garidoctor.app.USB_PERMISSION"

    data class Adapter(val driver: UsbSerialDriver) {
        val device: UsbDevice get() = driver.device
        val key: String get() = device.deviceName
        val chip: String get() = when (device.vendorId) {
            0x0403 -> "FTDI"; 0x1A86 -> "CH340"; 0x10C4 -> "CP210x"; 0x067B -> "PL2303"; else -> "USB serial"
        }
        val title: String get() = device.productName?.takeIf { it.isNotBlank() } ?: "ELM327 USB ($chip)"
    }

    fun isSupported(context: Context) = context.packageManager.hasSystemFeature("android.hardware.usb.host")

    fun adapters(context: Context): List<Adapter> {
        val usb = context.getSystemService(UsbManager::class.java) ?: return emptyList()
        return runCatching { UsbSerialProber.getDefaultProber().findAllDrivers(usb).map(::Adapter) }.getOrDefault(emptyList())
    }

    fun hasPermission(context: Context, a: Adapter) = context.getSystemService(UsbManager::class.java)?.hasPermission(a.device) == true

    /** Shows Android's "Allow GariDoctor to access the USB device?" dialog. Resumes with the answer. */
    suspend fun requestPermission(context: Context, a: Adapter): Boolean {
        val usb = context.getSystemService(UsbManager::class.java) ?: return false
        if (usb.hasPermission(a.device)) return true
        val app = context.applicationContext
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (intent.action != ACTION_PERMISSION) return
                    runCatching { app.unregisterReceiver(this) }
                    if (cont.isActive) cont.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) || usb.hasPermission(a.device))
                }
            }
            ContextCompat.registerReceiver(app, receiver, IntentFilter(ACTION_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
            cont.invokeOnCancellation { runCatching { app.unregisterReceiver(receiver) } }
            // The system fills in extras, so the intent must be mutable; it is explicit (setPackage) as API 34 requires.
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(app, 0, Intent(ACTION_PERMISSION).setPackage(app.packageName), flags)
            usb.requestPermission(a.device, pi)
        }
    }

    fun open(context: Context, a: Adapter, baud: Int): ElmLink {
        val usb = context.getSystemService(UsbManager::class.java) ?: error("USB not available")
        val connection = usb.openDevice(a.device) ?: error("USB permission not granted")
        val port = a.driver.ports.firstOrNull() ?: run { connection.close(); error("No serial port on this device") }
        try {
            port.open(connection)
            port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            runCatching { port.setDTR(true); port.setRTS(true) } // some cables only talk once DTR/RTS are raised
        } catch (e: Exception) {
            runCatching { port.close() }; runCatching { connection.close() }
            throw e
        }
        return object : ElmLink {
            override fun write(bytes: ByteArray) = port.write(bytes, 1000)
            override fun read(buffer: ByteArray, timeoutMs: Int): Int = port.read(buffer, timeoutMs)
            override fun close() { runCatching { port.close() } }
        }
    }
}
