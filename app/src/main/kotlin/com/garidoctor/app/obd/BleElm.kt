package com.garidoctor.app.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Bluetooth LE ELM327 adapters (Vgate iCar Pro BLE, OBDLink CX, "IOS-Vlink" and most "for iPhone"
 * dongles). They expose a serial-like service: one characteristic we write commands to and one
 * that notifies replies. Callers hold BLUETOOTH_SCAN + BLUETOOTH_CONNECT (API 31+) or location (older).
 */
@SuppressLint("MissingPermission")
object BleElm {
    data class Found(val device: BluetoothDevice, val name: String, val rssi: Int) {
        val looksLikeObd: Boolean get() = OBD_NAMES.any { name.contains(it, ignoreCase = true) }
    }

    private val OBD_NAMES = listOf("OBD", "ELM", "LINK", "VGATE", "ICAR", "KONNWEI", "VEEPEAK", "CAR", "SCAN", "OBDII")

    private fun uuid16(x: String) = UUID.fromString("0000$x-0000-1000-8000-00805f9b34fb")
    private val CCCD = uuid16("2902")
    /** (service, notify, write) used by common adapters, tried before guessing. */
    private val KNOWN = listOf(
        Triple(uuid16("fff0"), uuid16("fff1"), uuid16("fff2")),
        Triple(uuid16("ffe0"), uuid16("ffe1"), uuid16("ffe1")),
        Triple(uuid16("18f0"), uuid16("2af0"), uuid16("2af1")),
        Triple(UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2"), UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f"), UUID.fromString("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f")),
    )
    /** Generic Access / Attribute / Device Information / Battery: never the data channel. */
    private val STANDARD = setOf("1800", "1801", "180a", "180f").map(::uuid16).toSet()

    /** Scans until the returned handle is closed. Reports each named device once. */
    fun scan(context: Context, onFound: (Found) -> Unit): AutoCloseable {
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth is off")
        val seen = HashSet<String>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                val name = r.scanRecord?.deviceName ?: r.device.name ?: return
                if (seen.add(r.device.address)) onFound(Found(r.device, name, r.rssi))
            }
        }
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
        return AutoCloseable { runCatching { scanner.stopScan(cb) } }
    }

    fun device(context: Context, address: String): BluetoothDevice? =
        runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address) }.getOrNull()

    suspend fun open(context: Context, device: BluetoothDevice): ElmLink {
        val link = Link()
        link.gatt = device.connectGatt(context.applicationContext, false, link, BluetoothDevice.TRANSPORT_LE)
        try {
            withTimeout(15_000) { link.ready.await() }
        } catch (e: Throwable) {
            link.close()
            throw if (e is IOException) e else IOException("The adapter didn't answer over Bluetooth LE", e)
        }
        return link
    }

    private class Link : BluetoothGattCallback(), ElmLink {
        var gatt: BluetoothGatt? = null
        val ready = CompletableDeferred<Unit>()
        private val rx = LinkedBlockingQueue<ByteArray>()
        private var pending: ByteArray? = null
        private val writeSlot = Semaphore(1)
        private var tx: BluetoothGattCharacteristic? = null
        @Volatile private var closed = false

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> { closed = true; ready.completeExceptionally(IOException("Bluetooth LE disconnected (status $status)")) }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val pair = pick(g) ?: run { ready.completeExceptionally(IOException("This Bluetooth device isn't an OBD adapter")); return }
            val (notify, write) = pair
            tx = write
            g.setCharacteristicNotification(notify, true)
            val d = notify.getDescriptor(CCCD) ?: run { ready.complete(Unit); return }
            val value = if (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, value)
            else @Suppress("DEPRECATION") run { d.value = value; g.writeDescriptor(d) }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { ready.complete(Unit) }

        // API 33+: the value comes as a parameter. Older versions only call the deprecated overload.
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) { rx.offer(value) }
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) @Suppress("DEPRECATION") c.value?.let { rx.offer(it.copyOf()) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { writeSlot.release() }

        private fun pick(g: BluetoothGatt): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
            KNOWN.forEach { (s, n, w) ->
                val svc = g.getService(s) ?: return@forEach
                val nc = svc.getCharacteristic(n); val wc = svc.getCharacteristic(w)
                if (nc != null && wc != null) return nc to wc
            }
            val canNotify = BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE
            val canWrite = BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            g.services.filter { it.uuid !in STANDARD }.forEach { svc ->
                val nc = svc.characteristics.firstOrNull { it.properties and canNotify != 0 }
                val wc = svc.characteristics.firstOrNull { it.properties and canWrite != 0 }
                if (nc != null && wc != null) return nc to wc
            }
            return null
        }

        override fun write(bytes: ByteArray) {
            val g = gatt ?: throw IOException("not connected"); val c = tx ?: throw IOException("not connected")
            val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            // 20 bytes is the payload that fits every BLE link without negotiating a bigger MTU.
            bytes.toList().chunked(20).forEach { chunk ->
                if (closed) throw IOException("Bluetooth LE disconnected")
                if (!writeSlot.tryAcquire(2, TimeUnit.SECONDS)) throw IOException("The adapter stopped answering")
                val data = chunk.toByteArray()
                val ok = if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, data, type) == BluetoothGatt.GATT_SUCCESS
                    else @Suppress("DEPRECATION") run { c.writeType = type; c.value = data; g.writeCharacteristic(c) }
                if (!ok) { writeSlot.release(); throw IOException("Couldn't send to the adapter") }
            }
        }

        override fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val chunk = pending ?: rx.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return if (closed) throw IOException("Bluetooth LE disconnected") else 0
            val n = minOf(chunk.size, buffer.size)
            System.arraycopy(chunk, 0, buffer, 0, n)
            pending = if (n < chunk.size) chunk.copyOfRange(n, chunk.size) else null
            return n
        }

        override fun close() {
            closed = true
            runCatching { gatt?.disconnect() }; runCatching { gatt?.close() }
            gatt = null
        }
    }
}
