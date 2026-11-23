package com.example.speedopro

enum class AppLanguage { ES, EN }

data class Strings(
    // Top bar
    val appTitle: String,
    val langToggle: String,
    val unitKmh: String,
    val unitMph: String,
    val modeAnalog: String,
    val modeDigital: String,
    // GPS status
    val gpsActive: String,
    val gpsSearching: String,
    val gpsDisabled: String,
    // Metric cards
    val distance: String,
    val avgSpeed: String,
    val maxSpeed: String,
    val unitKm: String,
    val unitMi: String,
    // Controls
    val pause: String,
    val resume: String,
    val reset: String,
    // Permissions
    val permTitle: String,
    val permRationale: String,
    val permGrant: String,
    val permDenied: String,
    // Gauge labels
    val speedLabel: String,
    val tripLabel: String,
)

fun getStrings(lang: AppLanguage): Strings = when (lang) {
    AppLanguage.ES -> Strings(
        appTitle       = "L_AIO GPS",
        langToggle     = "EN",
        unitKmh        = "km/h",
        unitMph        = "mph",
        modeAnalog     = "Analógico",
        modeDigital    = "Digital",
        gpsActive      = "GPS activo",
        gpsSearching   = "Buscando satélites…",
        gpsDisabled    = "GPS desactivado",
        distance       = "Distancia",
        avgSpeed       = "Vel. promedio",
        maxSpeed       = "Vel. máxima",
        unitKm         = "km",
        unitMi         = "mi",
        pause          = "Pausar",
        resume         = "Reanudar",
        reset          = "Reiniciar",
        permTitle      = "Permiso de ubicación",
        permRationale  = "L_AIO GPS necesita acceso a tu ubicación GPS para medir la velocidad y la distancia.",
        permGrant      = "Conceder permiso",
        permDenied     = "Permiso denegado. Actívalo en Ajustes.",
        speedLabel     = "Velocidad",
        tripLabel      = "Viaje",
    )
    AppLanguage.EN -> Strings(
        appTitle       = "SpeedoPro",
        langToggle     = "ES",
        unitKmh        = "km/h",
        unitMph        = "mph",
        modeAnalog     = "Analog",
        modeDigital    = "Digital",
        gpsActive      = "GPS active",
        gpsSearching   = "Searching satellites…",
        gpsDisabled    = "GPS disabled",
        distance       = "Distance",
        avgSpeed       = "Avg speed",
        maxSpeed       = "Top speed",
        unitKm         = "km",
        unitMi         = "mi",
        pause          = "Pause",
        resume         = "Resume",
        reset          = "Reset",
        permTitle      = "Location permission",
        permRationale  = "SpeedoPro needs GPS location access to measure speed and distance.",
        permGrant      = "Grant permission",
        permDenied     = "Permission denied. Enable it in Settings.",
        speedLabel     = "Speed",
        tripLabel      = "Trip",
    )
}
