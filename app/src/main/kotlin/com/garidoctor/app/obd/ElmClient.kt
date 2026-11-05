package com.garidoctor.app.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.garidoctor.core.obd.DiagnosticTroubleCode
import com.garidoctor.core.obd.DtcInfo
import com.garidoctor.core.obd.LiveData
import com.garidoctor.core.obd.ObdDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.util.UUID

/**
 * A byte pipe to an ELM327 adapter. Bluetooth (SPP socket) and USB (serial cable via OTG) both
 * implement it, so the ELM327 conversation in [ElmClient] is written once.
 */
interface ElmLink : Closeable {
    fun write(bytes: ByteArray)
    /** Reads what has arrived, waiting at most [timeoutMs]. Returns 0 when nothing came. */
    fun read(buffer: ByteArray, timeoutMs: Int): Int
}

private class BluetoothLink(private val socket: BluetoothSocket) : ElmLink {
    override fun write(bytes: ByteArray) { socket.outputStream.write(bytes); socket.outputStream.flush() }
    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        // available() instead of a blocking read, so a silent dongle can't hang the coroutine.
        val n = socket.inputStream.available()
        return if (n > 0) socket.inputStream.read(buffer, 0, minOf(n, buffer.size)) else 0
    }
    override fun close() = socket.close()
}

/**
 * ELM327 client over Bluetooth Classic or USB. All parsing lives in [ObdDecoder] (:core).
 * Bluetooth callers must hold BLUETOOTH_CONNECT (API 31+); USB callers need the device permission
 * from [UsbElm.requestPermission].
 */
@SuppressLint("MissingPermission")
class ElmClient(private val context: Context) : Closeable {
    private var link: ElmLink? = null

    private val adapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun bondedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.toList().orEmpty()
        .sortedByDescending { (it.name ?: "").contains("OBD", true) || (it.name ?: "").contains("ELM", true) || (it.name ?: "").contains("V-LINK", true) }

    val isEnabled: Boolean get() = adapter?.isEnabled == true

    suspend fun connect(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        close()
        adapter?.cancelDiscovery()
        val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
        s.connect()
        link = BluetoothLink(s)
        initialise()
    }

    /** Opens a USB cable, finding its baud rate (38400 is the ELM327 default; clones vary). */
    suspend fun connect(usb: UsbElm.Adapter) = withContext(Dispatchers.IO) {
        close()
        var lastError: Throwable? = null
        for (baud in UsbElm.BAUD_RATES) {
            val l = runCatching { UsbElm.open(context, usb, baud) }.getOrElse { lastError = it; null } ?: continue
            link = l
            val reply = runCatching { send("ATZ", 2500) }.getOrDefault("")
            if (reply.contains("ELM", ignoreCase = true)) { initialise(); return@withContext }
            close()
        }
        throw lastError ?: IllegalStateException("No reply from the adapter. Turn the ignition ON and try again.")
    }

    private suspend fun initialise() = ObdDecoder.INIT_COMMANDS.forEach { send(it) }

    suspend fun send(cmd: String, timeoutMs: Long = 4000): String = withContext(Dispatchers.IO) {
        val l = link ?: error("not connected")
        withTimeout(timeoutMs) {
            l.write("$cmd\r".toByteArray())
            val sb = StringBuilder()
            val buf = ByteArray(1024) // usb-serial needs at least one max-size USB packet (512 on high-speed chips)
            var done = false
            while (isActive && !done) {
                val n = l.read(buf, 50)
                if (n <= 0) { delay(10); continue }
                for (i in 0 until n) {
                    val c = (buf[i].toInt() and 0xFF).toChar()
                    if (c == '>') { done = true; break }
                    sb.append(c)
                }
            }
            sb.toString().trim()
        }
    }

    suspend fun readCodes(dict: Map<String, DtcInfo>): List<DiagnosticTroubleCode> = ObdDecoder.decodeDtcs(send("03", 8000), dict)
    suspend fun clearCodes() { send("04") }
    suspend fun readLive(): List<LiveData> = ObdDecoder.LIVE_PIDS.mapNotNull { pid -> runCatching { ObdDecoder.decodeLive(send(pid)) }.getOrNull() }

    override fun close() { runCatching { link?.close() }; link = null }

    companion object { val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") }
}
