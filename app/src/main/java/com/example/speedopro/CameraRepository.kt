package com.example.speedopro

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

// ── Modelo de cámara ─────────────────────────────────────────────────────────
data class SpeedCamera(
    val id: Int,
    val lat: Double,
    val lon: Double,
    val name: String,
    val type: String = "speed",
)

// ── Estados de actualización desde Overpass ──────────────────────────────────
sealed class UpdateResult {
    data class Success(val count: Int) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

// ════════════════════════════════════════════════════════════════════════════
//  CameraRepository — carga local + actualización Overpass
// ════════════════════════════════════════════════════════════════════════════
object CameraRepository {

    private const val PREFS_NAME   = "speedo_cameras"
    private const val KEY_CAMERAS  = "cameras_json"
    private const val KEY_VERSION  = "cameras_version"

    // ── Carga las cámaras: prefiere la copia guardada en prefs, sino assets ──
    fun loadCameras(context: Context): List<SpeedCamera> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CAMERAS, null)
        return if (saved != null) {
            parseCamerasJson(saved)
        } else {
            loadFromAssets(context)
        }
    }

    private fun loadFromAssets(context: Context): List<SpeedCamera> = try {
        val json = context.assets.open("cameras.json").bufferedReader().readText()
        parseCamerasJson(json)
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseCamerasJson(json: String): List<SpeedCamera> = try {
        val root = JSONObject(json)
        val arr  = root.getJSONArray("cameras")
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            SpeedCamera(
                id   = obj.optInt("id", i),
                lat  = obj.getDouble("lat"),
                lon  = obj.getDouble("lon"),
                name = obj.optString("name", "Cámara"),
                type = obj.optString("type", "speed"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    // ── Descarga cámaras de Overpass API (hilo IO) ───────────────────────────
    // Consulta cámaras de tráfico etiquetadas en OSM globalmente
    suspend fun updateFromOverpass(
        context: Context,
        lat: Double,
        lon: Double,
        radiusKm: Double = 50.0,
    ): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val radiusM = (radiusKm * 1000).toInt()
            // Overpass QL: nodos con highway=speed_camera en un radio dado
            val query = """
                [out:json][timeout:25];
                (
                  node["highway"="speed_camera"](around:$radiusM,$lat,$lon);
                );
                out body;
            """.trimIndent()

            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://overpass-api.de/api/interpreter?data=$encodedQuery"
            val responseText = URL(urlStr).readText()

            val cameras = parseOverpassResponse(responseText)

            // Mezcla con las cámaras del asset original (sin duplicados por id)
            val assetCameras = loadFromAssets(context)
            val merged = (cameras + assetCameras)
                .distinctBy { it.id }

            // Persiste como JSON propio
            val savedJson = buildCamerasJson(merged)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CAMERAS, savedJson)
                .putString(KEY_VERSION, System.currentTimeMillis().toString())
                .apply()

            UpdateResult.Success(cameras.size)
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Error desconocido")
        }
    }

    private fun parseOverpassResponse(json: String): List<SpeedCamera> = try {
        val root     = JSONObject(json)
        val elements = root.getJSONArray("elements")
        (0 until elements.length()).mapNotNull { i ->
            val el = elements.getJSONObject(i)
            if (el.getString("type") == "node") {
                val tags = el.optJSONObject("tags")
                SpeedCamera(
                    id   = el.getInt("id"),
                    lat  = el.getDouble("lat"),
                    lon  = el.getDouble("lon"),
                    name = tags?.optString("name", "") ?: "",
                    type = "speed",
                )
            } else null
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun buildCamerasJson(cameras: List<SpeedCamera>): String {
        val items = cameras.joinToString(",\n") { c ->
            """{"id":${c.id},"lat":${c.lat},"lon":${c.lon},"name":"${c.name}","type":"${c.type}"}"""
        }
        return """{"version":"overpass","source":"OpenStreetMap","cameras":[$items]}"""
    }

    // ── Cálculo de distancia a cámara más cercana (en metros) ────────────────
    fun nearestCamera(
        cameras: List<SpeedCamera>,
        lat: Double,
        lon: Double,
    ): Pair<SpeedCamera, Float>? {
        if (cameras.isEmpty()) return null
        val results = FloatArray(1)
        return cameras
            .map { cam ->
                android.location.Location.distanceBetween(lat, lon, cam.lat, cam.lon, results)
                cam to results[0]
            }
            .minByOrNull { it.second }
    }
}
