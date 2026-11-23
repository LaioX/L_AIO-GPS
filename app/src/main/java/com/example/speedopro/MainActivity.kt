package com.example.speedopro

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.launch

// ── Colores del tema oscuro automotriz ───────────────────────────────────────
private val BgDark        = Color(0xFF080D12)
private val SurfaceDark   = Color(0xFF101820)
private val CardDark      = Color(0xFF131E2B)
private val NeonCyan      = Color(0xFF00E5FF)
private val NeonOrange    = Color(0xFFFF6D00)
private val NeonGreen     = Color(0xFF00E676)
private val NeonRed       = Color(0xFFFF1744)
private val TextPrimary   = Color(0xFFECEFF1)
private val TextSecondary = Color(0xFF78909C)

// ── Constantes de conversión ─────────────────────────────────────────────────
private const val MS_TO_KMH          = 3.6f
private const val MS_TO_MPH          = 2.23694f
private const val M_TO_KM            = 0.001f
private const val M_TO_MI            = 0.000621371f
private const val NOISE_THRESHOLD_MS = 0.4f
private const val JUMP_THRESHOLD_M   = 500f

enum class SpeedUnit { KMH, MPH }
enum class GaugeMode  { ANALOG, DIGITAL }
enum class GpsStatus  { SEARCHING, ACTIVE, DISABLED }

class MainActivity : ComponentActivity() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    // ── Estado GPS ────────────────────────────────────────────────────────────
    private val _speedMs        = mutableFloatStateOf(0f)
    private val _gpsStatus      = mutableStateOf(GpsStatus.SEARCHING)
    private val _currentLat     = mutableDoubleStateOf(0.0)
    private val _currentLon     = mutableDoubleStateOf(0.0)

    // Telemetría interna (SI)
    private val _distanceMeters = mutableDoubleStateOf(0.0)
    private val _maxSpeedMs     = mutableFloatStateOf(0f)
    private val _movingSamples  = mutableStateOf(mutableListOf<Float>())
    private var lastLatLng: Pair<Double, Double>? = null
    private var trackingActive  = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            SpeedoProApp(
                context       = this,
                speedMs       = _speedMs.floatValue,
                gpsStatus     = _gpsStatus.value,
                distanceM     = _distanceMeters.doubleValue,
                maxSpeedMs    = _maxSpeedMs.floatValue,
                movingSamples = _movingSamples.value.toList(),
                currentLat    = _currentLat.doubleValue,
                currentLon    = _currentLon.doubleValue,
                onPermissionsGranted = { startLocationUpdates() },
                onPauseResume = { active ->
                    trackingActive = active
                    if (active) startLocationUpdates() else stopLocationUpdates()
                },
                onReset = {
                    _distanceMeters.doubleValue = 0.0
                    _maxSpeedMs.floatValue      = 0f
                    _speedMs.floatValue         = 0f
                    _movingSamples.value        = mutableListOf()
                    lastLatLng                  = null
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (trackingActive &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L
        ).setMinUpdateIntervalMillis(500L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc        = result.lastLocation ?: return
                val rawSpeedMs = if (loc.hasSpeed()) loc.speed else 0f
                val speed      = if (rawSpeedMs > NOISE_THRESHOLD_MS) rawSpeedMs else 0f

                _speedMs.floatValue    = speed
                _gpsStatus.value       = GpsStatus.ACTIVE
                _currentLat.doubleValue = loc.latitude
                _currentLon.doubleValue = loc.longitude

                val cur  = Pair(loc.latitude, loc.longitude)
                val prev = lastLatLng
                if (prev != null && speed > NOISE_THRESHOLD_MS) {
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        prev.first, prev.second, cur.first, cur.second, results
                    )
                    if (results[0] < JUMP_THRESHOLD_M) {
                        _distanceMeters.doubleValue += results[0]
                    }
                }
                lastLatLng = cur

                if (speed > _maxSpeedMs.floatValue) _maxSpeedMs.floatValue = speed
                if (speed > NOISE_THRESHOLD_MS) {
                    val list = _movingSamples.value.toMutableList().also { it.add(speed) }
                    _movingSamples.value = list
                }
            }
        }
        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        _gpsStatus.value = GpsStatus.SEARCHING
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        _speedMs.floatValue = 0f
        _gpsStatus.value    = GpsStatus.DISABLED
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  UI Principal
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun SpeedoProApp(
    context: android.content.Context,
    speedMs: Float,
    gpsStatus: GpsStatus,
    distanceM: Double,
    maxSpeedMs: Float,
    movingSamples: List<Float>,
    currentLat: Double,
    currentLon: Double,
    onPermissionsGranted: () -> Unit,
    onPauseResume: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    // ── Estado UI ─────────────────────────────────────────────────────────────
    var language  by remember { mutableStateOf(AppLanguage.ES) }
    var unit      by remember { mutableStateOf(SpeedUnit.KMH) }
    var gaugeMode by remember { mutableStateOf(GaugeMode.ANALOG) }
    var tracking  by remember { mutableStateOf(true) }
    var hasPerms  by remember { mutableStateOf(false) }

    // ── Estado de cámaras ─────────────────────────────────────────────────────
    var cameras       by remember { mutableStateOf(emptyList<SpeedCamera>()) }
    var alertRadiusM  by remember { mutableFloatStateOf(300f) }
    var updateState   by remember { mutableStateOf<CameraUpdateState>(CameraUpdateState.Idle) }
    val scope         = rememberCoroutineScope()

    // Carga inicial de cámaras desde assets/prefs
    LaunchedEffect(Unit) {
        cameras = CameraRepository.loadCameras(context)
    }

    val str = remember(language) { getStrings(language) }

    // ── Permiso launcher ──────────────────────────────────────────────────────
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPerms = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                   results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasPerms) onPermissionsGranted()
    }

    // ── Conversiones de display ───────────────────────────────────────────────
    val displaySpeed = if (unit == SpeedUnit.KMH) speedMs * MS_TO_KMH else speedMs * MS_TO_MPH
    val maxScale     = if (unit == SpeedUnit.KMH) 240f else 160f
    val unitLabel    = if (unit == SpeedUnit.KMH) str.unitKmh else str.unitMph
    val displayDist  = if (unit == SpeedUnit.KMH) distanceM * M_TO_KM else distanceM * M_TO_MI
    val distUnit     = if (unit == SpeedUnit.KMH) str.unitKm else str.unitMi
    val avgSpeedMs   = if (movingSamples.isNotEmpty()) movingSamples.average().toFloat() else 0f
    val displayAvg   = if (unit == SpeedUnit.KMH) avgSpeedMs * MS_TO_KMH else avgSpeedMs * MS_TO_MPH
    val displayMax   = if (unit == SpeedUnit.KMH) maxSpeedMs * MS_TO_KMH else maxSpeedMs * MS_TO_MPH

    // ── Lógica de alerta de cámara ────────────────────────────────────────────
    val nearest = remember(currentLat, currentLon, cameras) {
        if (currentLat == 0.0 && currentLon == 0.0) null
        else CameraRepository.nearestCamera(cameras, currentLat, currentLon)
    }
    val cameraAlertVisible = nearest != null && nearest.second <= alertRadiusM
    val metersAway         = nearest?.second ?: Float.MAX_VALUE
    val nearestName        = nearest?.first?.name ?: ""

    // ── Pantalla ──────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier.fillMaxSize().background(BgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            TopBar(
                str          = str,
                language     = language,
                unit         = unit,
                gaugeMode    = gaugeMode,
                gpsStatus    = gpsStatus,
                onLangToggle = { language  = if (language  == AppLanguage.ES) AppLanguage.EN else AppLanguage.ES },
                onUnitToggle = { unit      = if (unit      == SpeedUnit.KMH)  SpeedUnit.MPH  else SpeedUnit.KMH },
                onModeToggle = { gaugeMode = if (gaugeMode == GaugeMode.ANALOG) GaugeMode.DIGITAL else GaugeMode.ANALOG }
            )

            // ── Banner de alerta (justo debajo del top bar) ───────────────────
            CameraAlertBanner(
                visible      = cameraAlertVisible,
                metersAway   = metersAway,
                cameraName   = nearestName,
                alertRadiusM = alertRadiusM,
            )

            Spacer(Modifier.height(8.dp))

            // Velocímetro
            if (hasPerms) {
                when (gaugeMode) {
                    GaugeMode.ANALOG -> Box(
                        modifier            = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        contentAlignment    = Alignment.Center
                    ) {
                        AnalogSpeedometer(
                            speedValue = displaySpeed,
                            maxScale   = maxScale,
                            unitLabel  = unitLabel,
                            size       = 300.dp
                        )
                    }
                    GaugeMode.DIGITAL -> DigitalSpeedometer(displaySpeed, unitLabel)
                }
            } else {
                PermissionCard(str = str, onRequest = {
                    permLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                })
            }

            Spacer(Modifier.height(16.dp))

            if (hasPerms) {
                // Tarjetas de métricas
                MetricCards(
                    str       = str,
                    distance  = displayDist,
                    distUnit  = distUnit,
                    avgSpeed  = displayAvg,
                    maxSpeed  = displayMax,
                    unitLabel = unitLabel
                )

                Spacer(Modifier.height(12.dp))

                // ── Panel de cámaras ──────────────────────────────────────────
                CameraSettingsRow(
                    alertRadiusM  = alertRadiusM,
                    onRadiusChange = { alertRadiusM = it },
                    cameraCount   = cameras.size,
                    updateState   = updateState,
                    isMetric      = unit == SpeedUnit.KMH,
                    onUpdateClick = {
                        if (currentLat != 0.0 && currentLon != 0.0) {
                            scope.launch {
                                updateState = CameraUpdateState.Loading
                                val result = CameraRepository.updateFromOverpass(
                                    context = context,
                                    lat     = currentLat,
                                    lon     = currentLon,
                                )
                                updateState = when (result) {
                                    is UpdateResult.Success -> {
                                        cameras = CameraRepository.loadCameras(context)
                                        CameraUpdateState.Done(result.count)
                                    }
                                    is UpdateResult.Error -> CameraUpdateState.Fail(result.message)
                                }
                            }
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                // Controles de viaje
                TripControls(
                    str           = str,
                    tracking      = tracking,
                    onPauseResume = {
                        tracking = !tracking
                        onPauseResume(tracking)
                    },
                    onReset = onReset
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // Solicitar permisos al componer
        LaunchedEffect(Unit) {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        LaunchedEffect(hasPerms) {
            if (hasPerms) onPermissionsGranted()
        }
    }
}

// ── Top Bar ──────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    str: Strings,
    language: AppLanguage,
    unit: SpeedUnit,
    gaugeMode: GaugeMode,
    gpsStatus: GpsStatus,
    onLangToggle: () -> Unit,
    onUnitToggle: () -> Unit,
    onModeToggle: () -> Unit,
) {
    Surface(
        color          = SurfaceDark,
        tonalElevation = 0.dp,
        modifier       = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth()
            ) {
                Text(
                    text       = str.appTitle,
                    color      = NeonCyan,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.weight(1f)
                )
                GpsStatusIcon(gpsStatus, str)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                ToggleChip(
                    label   = if (language == AppLanguage.ES) "EN" else "ES",
                    color   = NeonOrange,
                    onClick = onLangToggle
                )
                ToggleChip(
                    label   = if (unit == SpeedUnit.KMH) str.unitMph else str.unitKmh,
                    color   = NeonCyan,
                    onClick = onUnitToggle
                )
                ToggleChip(
                    label   = if (gaugeMode == GaugeMode.ANALOG) str.modeDigital else str.modeAnalog,
                    color   = Color(0xFFCE93D8),
                    onClick = onModeToggle
                )
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(50),
        color   = color.copy(alpha = 0.15f),
    ) {
        Text(
            text       = label,
            color      = color,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun GpsStatusIcon(status: GpsStatus, str: Strings) {
    val (icon, color, label) = when (status) {
        GpsStatus.ACTIVE    -> Triple(Icons.Default.GpsFixed,    NeonGreen,  str.gpsActive)
        GpsStatus.SEARCHING -> Triple(Icons.Default.GpsNotFixed, NeonOrange, str.gpsSearching)
        GpsStatus.DISABLED  -> Triple(Icons.Default.GpsOff,      NeonRed,    str.gpsDisabled)
    }
    val animColor by animateColorAsState(color, animationSpec = tween(500), label = "gpsColor")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = animColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = animColor, fontSize = 11.sp)
    }
}

// ── Velocímetro Digital ──────────────────────────────────────────────────────
@Composable
private fun DigitalSpeedometer(speed: Float, unitLabel: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF0D2030), CardDark)))
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(targetState = "%.0f".format(speed), label = "speed") { s ->
                Text(
                    text       = s,
                    color      = NeonCyan,
                    fontSize   = 96.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign  = TextAlign.Center,
                )
            }
            Text(text = unitLabel, color = TextSecondary, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// ── Tarjetas de métricas ─────────────────────────────────────────────────────
@Composable
private fun MetricCards(
    str: Strings,
    distance: Double,
    distUnit: String,
    avgSpeed: Float,
    maxSpeed: Float,
    unitLabel: String,
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard(Modifier.weight(1f), str.distance, "%.2f".format(distance), distUnit, NeonCyan)
        MetricCard(Modifier.weight(1f), str.avgSpeed, "%.1f".format(avgSpeed),  unitLabel, NeonOrange)
        MetricCard(Modifier.weight(1f), str.maxSpeed, "%.1f".format(maxSpeed),  unitLabel, NeonRed)
    }
}

@Composable
private fun MetricCard(modifier: Modifier, label: String, value: String, unit: String, accent: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = CardDark, tonalElevation = 0.dp) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(value, color = accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
            Text(unit, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

// ── Controles de viaje ───────────────────────────────────────────────────────
@Composable
private fun TripControls(
    str: Strings,
    tracking: Boolean,
    onPauseResume: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick  = onPauseResume,
            modifier = Modifier.weight(1f).height(52.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (tracking) NeonOrange.copy(alpha = 0.2f) else NeonGreen.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector        = if (tracking) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint               = if (tracking) NeonOrange else NeonGreen
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text       = if (tracking) str.pause else str.resume,
                color      = if (tracking) NeonOrange else NeonGreen,
                fontWeight = FontWeight.Bold
            )
        }
        OutlinedButton(
            onClick  = onReset,
            modifier = Modifier.weight(1f).height(52.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
            border   = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonRed)
            Spacer(Modifier.width(6.dp))
            Text(str.reset, color = NeonRed, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Pantalla de solicitud de permisos ────────────────────────────────────────
@Composable
private fun PermissionCard(str: Strings, onRequest: () -> Unit) {
    Surface(
        modifier       = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        shape          = RoundedCornerShape(20.dp),
        color          = CardDark,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier            = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.GpsOff, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(48.dp))
            Text(str.permTitle, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(str.permRationale, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Button(
                onClick = onRequest,
                colors  = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape   = RoundedCornerShape(12.dp)
            ) {
                Text(str.permGrant, color = BgDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}
