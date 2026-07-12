package com.castbrowse.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Icons.Default.Pause is in extended icons; define it inline via vector path to avoid the dependency
private val PauseIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(6f, 19f)
        horizontalLineTo(10f)
        verticalLineTo(5f)
        horizontalLineTo(6f)
        verticalLineTo(19f)
        moveTo(14f, 5f)
        verticalLineTo(19f)
        horizontalLineTo(18f)
        verticalLineTo(5f)
        horizontalLineTo(14f)
        close()
    }.build()
}

class CastControlActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = EncryptedStorage.getPreferences(this)
        val themeMode = prefs.getString("theme_mode", "dark") ?: "dark"
        setContent {
            CastBrowseTheme(themeMode = themeMode) {
                ControlScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ControlScreen() {
        val activeDevice = CastSessionManager.castingDevice

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                "Cast Control Center",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        ) { paddingValues ->
            if (activeDevice == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Not Connected",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "No Device Connected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Please connect to an FCast receiver using the Setup Wizard first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(paddingValues)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Device Info Card
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Connected",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Connected Device",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    activeDevice.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${activeDevice.ipAddress}:${activeDevice.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 2. Playback State Indicator / URL Card
                    val activeUrl = CastSessionManager.activeMediaUrl
                    if (!activeUrl.isNullOrEmpty()) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Now Casting",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    activeUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Playback Position & Seek Slider — synced from receiver's PlaybackUpdate
                    // isTimerActive reflects the actual receiver state (opcode 7)
                    val isTimerActive = CastSessionManager.playbackState == 1 || CastSessionManager.isMediaPlaying
                    // Receiver pushes position via opcode 7; we also tick locally when playing
                    var localOffsetSeconds by remember { mutableStateOf(0) }
                    val receiverPos = CastSessionManager.playbackPositionSeconds
                    // Seed local offset when receiver sends a position
                    LaunchedEffect(receiverPos) {
                        localOffsetSeconds = receiverPos.toInt()
                    }
                    // Tick locally while playing to fill gaps between receiver updates
                    LaunchedEffect(isTimerActive) {
                        if (isTimerActive) {
                            while (true) {
                                delay(1000)
                                localOffsetSeconds++
                            }
                        }
                    }
                    var sliderValue by remember { mutableStateOf(localOffsetSeconds.toFloat()) }
                    var isUserSeeking by remember { mutableStateOf(false) }
                    // Keep slider in sync when not user-dragging
                    LaunchedEffect(localOffsetSeconds) {
                        if (!isUserSeeking) sliderValue = localOffsetSeconds.toFloat()
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val s = sliderValue.toInt()
                            val durationVal = CastSessionManager.mediaDurationSeconds.toInt()
                            val elapsedStr = String.format("%02d:%02d", s / 60, s % 60)
                            val durationStr = if (durationVal > 0) String.format("%02d:%02d", durationVal / 60, durationVal % 60) else "--:--"
                            Text(
                                text = "$elapsedStr / $durationStr",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val stateLabel = when (CastSessionManager.playbackState) {
                                1 -> "▶ Playing"
                                2 -> "⏸ Paused"
                                3 -> "⏳ Buffering"
                                else -> if (!CastSessionManager.isMediaPlaying) "⏹ Stopped" else ""
                            }
                            Text(
                                text = stateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val maxDuration = if (CastSessionManager.mediaDurationSeconds > 0) CastSessionManager.mediaDurationSeconds.toFloat() else 7200f
                        Slider(
                            value = sliderValue.coerceIn(0f, maxDuration),
                            onValueChange = { 
                                sliderValue = it
                                isUserSeeking = true
                            },
                            onValueChangeFinished = {
                                isUserSeeking = false
                                localOffsetSeconds = sliderValue.toInt()
                                lifecycleScope.launch {
                                    FCastClient.seek(
                                        activeDevice.ipAddress,
                                        sliderValue.toDouble(),
                                        CastSessionManager.customFcastPort
                                    )
                                }
                            },
                            valueRange = 0f..maxDuration
                        )
                    }

                    // 4. Playback Buttons (FF, Play/Pause, RW)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                localOffsetSeconds = maxOf(0, localOffsetSeconds - 30)
                                sliderValue = localOffsetSeconds.toFloat()
                                lifecycleScope.launch {
                                    FCastClient.seek(
                                        activeDevice.ipAddress,
                                        localOffsetSeconds.toDouble(),
                                        CastSessionManager.customFcastPort
                                    )
                                }
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Rewind 30s",
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                val port = CastSessionManager.customFcastPort
                                lifecycleScope.launch {
                                    if (isTimerActive) {
                                        FCastClient.pause(activeDevice.ipAddress, port)
                                        CastSessionManager.isMediaPlaying = false
                                        CastSessionManager.playbackState = 2
                                    } else {
                                        FCastClient.resume(activeDevice.ipAddress, port)
                                        CastSessionManager.isMediaPlaying = true
                                        CastSessionManager.playbackState = 1
                                    }
                                }
                            },
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = if (isTimerActive) PauseIcon else Icons.Default.PlayArrow,
                                contentDescription = if (isTimerActive) "Pause" else "Play",
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                localOffsetSeconds += 30
                                sliderValue = localOffsetSeconds.toFloat()
                                lifecycleScope.launch {
                                    FCastClient.seek(
                                        activeDevice.ipAddress,
                                        localOffsetSeconds.toDouble(),
                                        CastSessionManager.customFcastPort
                                    )
                                }
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Forward 30s",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 5. Volume Slider
                    var volumeState by remember { mutableStateOf(50f) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Volume Low",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = volumeState,
                                onValueChange = { volumeState = it },
                                onValueChangeFinished = {
                                    lifecycleScope.launch {
                                        FCastClient.setVolume(
                                            activeDevice.ipAddress,
                                            volumeState / 100f,
                                            CastSessionManager.customFcastPort
                                        )
                                    }
                                },
                                valueRange = 0f..100f,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Volume High",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Volume: ${volumeState.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    // 6. Playback Speed Selector
                    var speedState by remember { mutableStateOf(1.0f) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Speed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (speedState == speed) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (speedState == speed) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            speedState = speed
                                            lifecycleScope.launch {
                                                FCastClient.setSpeed(
                                                    activeDevice.ipAddress,
                                                    speed.toDouble(),
                                                    CastSessionManager.customFcastPort
                                                )
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${speed}x",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (speedState == speed) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 7. Stop Casting Button
                    Button(
                        onClick = {
                            lifecycleScope.launch {
                                FCastClient.stop(activeDevice.ipAddress, CastSessionManager.customFcastPort)
                                CastSessionManager.isMediaPlaying = false
                                CastSessionManager.activeMediaUrl = null
                                Toast.makeText(this@CastControlActivity, "Playback stopped", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Stop Playback")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Streaming Session", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
