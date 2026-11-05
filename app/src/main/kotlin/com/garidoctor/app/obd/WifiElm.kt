package com.garidoctor.app.obd

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Wi-Fi ELM327 adapters make their own hotspot ("WiFi_OBDII", "V-LINK") and listen on a TCP port,
 * almost always 192.168.0.10:35000. That hotspot has no internet, so Android would send our
 * socket over mobile data instead; we bind it to the Wi-Fi network explicitly.
 */
object WifiElm {
    const val DEFAULT_HOST = "192.168.0.10"
    const val DEFAULT_PORT = 35000

    fun open(context: Context, host: String, port: Int): ElmLink {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val wifi = cm?.allNetworks?.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
            ?: throw IllegalStateException("Connect the phone's Wi-Fi to the OBD adapter first")
        val socket: Socket = (wifi.socketFactory ?: SocketFactory.getDefault()).createSocket()
        try {
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.tcpNoDelay = true
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
        val input = socket.getInputStream(); val output = socket.getOutputStream()
        return object : ElmLink {
            override fun write(bytes: ByteArray) { output.write(bytes); output.flush() }
            override fun read(buffer: ByteArray, timeoutMs: Int): Int {
                val n = input.available()
                return if (n > 0) input.read(buffer, 0, minOf(n, buffer.size)) else 0
            }
            override fun close() { runCatching { socket.close() } }
        }
    }
}
