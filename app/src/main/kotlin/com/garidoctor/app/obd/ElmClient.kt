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
 * Bluetooth Classic SPP client for ELM327 dongles. All parsing lives in [ObdDecoder] (:core).
 * Callers must hold BLUETOOTH_CONNECT (API 31+) before using this class.
 */
@SuppressLint("MissingPermission")
class ElmClient(private val context: Context) : Closeable {
    private var socket: BluetoothSocket? = null

    private val adapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun bondedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.toList().orEmpty()
        .sortedByDescending { (it.name ?: "").contains("OBD", true) || (it.name ?: "").contains("ELM", true) || (it.name ?: "").contains("V-LINK", true) }

    val isEnabled: Boolean get() = adapter?.isEnabled == true

    suspend fun connect(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        close()
        adapter?.cancelDiscovery()
        val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
        s.connect()
        socket = s
        ObdDecoder.INIT_COMMANDS.forEach { send(it) }
    }

    suspend fun send(cmd: String, timeoutMs: Long = 4000): String = withContext(Dispatchers.IO) {
        val s = socket ?: error("not connected")
        withTimeout(timeoutMs) {
            s.outputStream.write("$cmd\r".toByteArray())
            s.outputStream.flush()
            val sb = StringBuilder()
            val input = s.inputStream
            // Poll instead of a blocking read so withTimeout can actually cancel a silent dongle.
            while (isActive) {
                if (input.available() <= 0) { delay(10); continue }
                val b = input.read()
                if (b < 0 || b.toChar() == '>') break
                sb.append(b.toChar())
            }
            sb.toString().trim()
        }
    }

    suspend fun readCodes(dict: Map<String, DtcInfo>): List<DiagnosticTroubleCode> = ObdDecoder.decodeDtcs(send("03", 8000), dict)
    suspend fun clearCodes() { send("04") }
    suspend fun readLive(): List<LiveData> = ObdDecoder.LIVE_PIDS.mapNotNull { pid -> runCatching { ObdDecoder.decodeLive(send(pid)) }.getOrNull() }

    override fun close() { runCatching { socket?.close() }; socket = null }

    companion object { val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") }
}
