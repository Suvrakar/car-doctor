package com.garidoctor.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Garage(
    val id: String, val name: String, val type: String, val area: String, val phone: String = "",
    val lat: Double, val lng: Double, val featured: Boolean = false, val verified: Boolean = false, val sample: Boolean = false,
)

@Serializable
private data class GarageFile(val garages: List<Garage>)

object Garages {
    private val json = Json { ignoreUnknownKeys = true }
    /** Remote Config "garages" key wins over the bundled sample list. Featured (paid) listings sort first. */
    fun load(context: Context, remoteJson: String?): List<Garage> {
        val text = remoteJson ?: context.assets.open("garages.json").bufferedReader().use { it.readText() }
        return runCatching { json.decodeFromString(GarageFile.serializer(), text).garages }.getOrDefault(emptyList())
    }
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1); val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}
