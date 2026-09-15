package com.castbrowse.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Lightweight inline vector icons to avoid extended material icon dependencies
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

private val RepeatIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(7f, 7f)
        horizontalLineTo(17f)
        verticalLineTo(10f)
        lineTo(21f, 6f)
        lineTo(17f, 2f)
        verticalLineTo(5f)
        horizontalLineTo(5f)
        verticalLineTo(11f)
        horizontalLineTo(7f)
        verticalLineTo(7f)
        close()
        moveTo(17f, 17f)
        horizontalLineTo(7f)
        verticalLineTo(14f)
        lineTo(3f, 18f)
        lineTo(7f, 22f)
        verticalLineTo(19f)
        horizontalLineTo(19f)
        verticalLineTo(13f)
        horizontalLineTo(17f)
        verticalLineTo(17f)
        close()
    }.build()
}

private val SubtitlesIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(20f, 4f)
        horizontalLineTo(4f)
        curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
        verticalLineTo(18f)
        curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
        horizontalLineTo(20f)
        curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
        verticalLineTo(6f)
        curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f)
        close()
        moveTo(4f, 12f)
        horizontalLineTo(8f)
        verticalLineTo(14f)
        horizontalLineTo(4f)
        verticalLineTo(12f)
        close()
        moveTo(14f, 18f)
        horizontalLineTo(4f)
        verticalLineTo(16f)
        horizontalLineTo(14f)
        verticalLineTo(18f)
        close()
        moveTo(20f, 18f)
        horizontalLineTo(16f)
        verticalLineTo(16f)
        horizontalLineTo(20f)
        verticalLineTo(18f)
        close()
        moveTo(20f, 14f)
        horizontalLineTo(10f)
        verticalLineTo(12f)
        horizontalLineTo(20f)
        verticalLineTo(14f)
        close()
    }.build()
}

private val QueueIcon: ImageVector by lazy {
    ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(15f, 6f)
        horizontalLineTo(3f)
        verticalLineTo(8f)
        horizontalLineTo(15f)
        verticalLineTo(6f)
        close()
        moveTo(15f, 10f)
        horizontalLineTo(3f)
        verticalLineTo(12f)
        horizontalLineTo(15f)
        verticalLineTo(10f)
        close()
        moveTo(3f, 16f)
        horizontalLineTo(11f)
        verticalLineTo(14f)
        horizontalLineTo(3f)
        verticalLineTo(16f)
        close()
        moveTo(17f, 6f)
        verticalLineTo(14.18f)
        curveTo(16.53f, 14.07f, 16.03f, 14f, 15.5f, 14f)
        curveTo(13.57f, 14f, 12f, 15.57f, 12f, 17.5f)
        curveTo(12f, 19.43f, 13.57f, 21f, 15.5f, 21f)
        curveTo(17.43f, 21f, 19f, 19.43f, 19f, 17.5f)
        verticalLineTo(9f)
        horizontalLineTo(22f)
        verticalLineTo(6f)
        horizontalLineTo(17f)
        close()
    }.build()
}

class CastControlActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val prefs = EncryptedStorage.getPreferences(this)
        val themeMode = prefs.getString("theme_mode", "dark") ?: "dark"
        val dynamicColor = prefs.getBoolean("dynamic_color", false)
        setContent {
            CastBrowseTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                ControlScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun ControlScreen() {
        val activeDevice = CastSessionManager.castingDevice
        val targetPort = if ((activeDevice?.port ?: 0) > 0) activeDevice!!.port else CastSessionManager.customFcastPort
        val context = LocalContext.current

        // File picker for external subtitles (.srt, .vtt)
        val subtitlePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                val success = SubtitleManager.loadSubtitle(context, uri, CastSessionManager.subtitleOffsetMs)
                if (success) {
                    Toast.makeText(context, "Subtitles loaded: ${SubtitleManager.activeSubtitleName}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to load subtitle file", Toast.LENGTH_SHORT).show()
                }
            }
        }

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
                            "Please connect to an FCast, DLNA, or AirPlay receiver first.",
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
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Device Info Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Connected",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Connected Device (${activeDevice.protocol})",
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
                                    "${activeDevice.ipAddress}:${if (activeDevice.port > 0) activeDevice.port else targetPort}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 2. Active Media Card
                    val activeUrl = CastSessionManager.activeMediaUrl
                    val activeTitle = CastSessionManager.activeMediaTitle
                    if (!activeUrl.isNullOrEmpty()) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Cast URL", activeUrl))
                                        Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Now Casting",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Long-press to copy URL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                Text(
                                    activeTitle?.ifEmpty { activeUrl } ?: activeUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 3. Playback Position & Seek Slider
                    val isTimerActive = CastSessionManager.playbackState == 1 || CastSessionManager.isMediaPlaying
                    var localOffsetSeconds by remember { mutableStateOf(0) }
                    val receiverPos = CastSessionManager.playbackPositionSeconds

                    LaunchedEffect(receiverPos) {
                        localOffsetSeconds = receiverPos.toInt()
                    }

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

                    LaunchedEffect(localOffsetSeconds) {
                        if (!isUserSeeking) sliderValue = localOffsetSeconds.toFloat()
                    }

                    // Resume Playback Prompt
                    val savedResumePosition = remember(activeUrl) {
                        if (activeUrl != null) PlaybackResumeManager.getSavedPosition(context, activeUrl) else 0.0
                    }
                    var resumeDismissed by remember(activeUrl) { mutableStateOf(false) }
                    val canShowResume = !resumeDismissed && savedResumePosition > 15.0 && sliderValue < 15.0

                    AnimatedVisibility(
                        visible = canShowResume,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Resume Playback?",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Continue from ${PlaybackResumeManager.formatTime(savedResumePosition)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            resumeDismissed = true
                                            localOffsetSeconds = savedResumePosition.toInt()
                                            sliderValue = savedResumePosition.toFloat()
                                            lifecycleScope.launch {
                                                CastSessionManager.seek(savedResumePosition)
                                                Toast.makeText(context, "Resumed at ${PlaybackResumeManager.formatTime(savedResumePosition)}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Resume", style = MaterialTheme.typography.labelMedium)
                                    }
                                    IconButton(
                                        onClick = { resumeDismissed = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Seekbar Section
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
                                fontWeight = FontWeight.Bold,
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
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
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
                                    CastSessionManager.seek(sliderValue.toDouble())
                                }
                            },
                            valueRange = 0f..maxDuration
                        )
                    }

                    // 4. Primary Playback & Jump Controls Row
                    // Layout: [0:00] [-30s] [-10s] (Play/Pause) [+10s] [+30s] [Loop]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Restart (0:00)
                        IconButton(
                            onClick = {
                                localOffsetSeconds = 0
                                sliderValue = 0f
                                lifecycleScope.launch {
                                    CastSessionManager.restart()
                                    Toast.makeText(context, "Restarted (0:00)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Restart",
                                    modifier = Modifier.size(22.dp)
                                )
                                Text("0:00", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
                            }
                        }

                        // Jump -30s
                        FilledTonalIconButton(
                            onClick = {
                                localOffsetSeconds = maxOf(0, localOffsetSeconds - 30)
                                sliderValue = localOffsetSeconds.toFloat()
                                lifecycleScope.launch {
                                    CastSessionManager.jump(-30.0)
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Text("-30s", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp))
                        }

                        // Jump -10s
                        FilledTonalIconButton(
                            onClick = {
                                localOffsetSeconds = maxOf(0, localOffsetSeconds - 10)
                                sliderValue = localOffsetSeconds.toFloat()
                                lifecycleScope.launch {
                                    CastSessionManager.jump(-10.0)
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Text("-10s", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp))
                        }

                        // Center Play / Pause
                        FilledIconButton(
                            onClick = {
                                lifecycleScope.launch {
                                    if (isTimerActive) {
                                        CastSessionManager.pause()
                                    } else {
                                        CastSessionManager.resume()
                                    }
                                    CastPlaybackService.updateState(this@CastControlActivity)
                                }
                            },
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = if (isTimerActive) PauseIcon else Icons.Default.PlayArrow,
                                contentDescription = if (isTimerActive) "Pause" else "Play",
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        // Jump +10s
                        FilledTonalIconButton(
                            onClick = {
                                localOffsetSeconds += 10
                                sliderValue = localOffsetSeconds.toFloat()
                                lifecycleScope.launch {
                                    CastSessionManager.jump(10.0)
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Text("+10s", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp))
                        }

                        // Jump +30s
                        FilledTonalIconButton(
                            onClick = {
                                localOffsetSeconds += 30
                                sliderValue = localOffsetSeconds.toFloat()
                                lifecycleScope.launch {
                                    CastSessionManager.jump(30.0)
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Text("+30s", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp))
                        }

                        // Loop / Repeat Toggle
                        val isLooping = CastSessionManager.isLoopEnabled
                        IconButton(
                            onClick = {
                                val next = !isLooping
                                CastSessionManager.setLoop(next)
                                Toast.makeText(context, if (next) "Loop enabled" else "Loop disabled", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isLooping) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                        ) {
                            Icon(
                                imageVector = RepeatIcon,
                                contentDescription = "Loop",
                                tint = if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 5. Aspect Ratio Selector
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Aspect Ratio",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("16:9", "Fill", "Zoom", "Original").forEach { ratio ->
                                val isSelected = CastSessionManager.aspectRatio == ratio
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            CastSessionManager.updateAspectRatio(ratio)
                                            Toast.makeText(context, "Aspect ratio: $ratio", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ratio,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 6. A/V & Subtitle Sync Controls Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                "A/V & Subtitle Sync",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Audio Delay (-500ms to +500ms)
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Audio Delay: ${if (CastSessionManager.audioDelayMs > 0) "+${CastSessionManager.audioDelayMs}" else "${CastSessionManager.audioDelayMs}"} ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (CastSessionManager.audioDelayMs != 0) {
                                        Text(
                                            "Reset (0ms)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                CastSessionManager.setAudioDelay(0)
                                            }
                                        )
                                    }
                                }
                                Slider(
                                    value = CastSessionManager.audioDelayMs.toFloat(),
                                    onValueChange = {
                                        val rounded = (Math.round(it / 50f) * 50).toInt()
                                        CastSessionManager.setAudioDelay(rounded)
                                    },
                                    valueRange = -500f..500f,
                                    steps = 19
                                )
                            }

                            // Subtitle Offset (-2000ms to +2000ms)
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Subtitle Offset: ${if (CastSessionManager.subtitleOffsetMs > 0) "+${CastSessionManager.subtitleOffsetMs}" else "${CastSessionManager.subtitleOffsetMs}"} ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (CastSessionManager.subtitleOffsetMs != 0) {
                                        Text(
                                            "Reset (0ms)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                CastSessionManager.setSubtitleOffset(0)
                                            }
                                        )
                                    }
                                }
                                Slider(
                                    value = CastSessionManager.subtitleOffsetMs.toFloat(),
                                    onValueChange = {
                                        val rounded = (Math.round(it / 100f) * 100).toInt()
                                        CastSessionManager.setSubtitleOffset(rounded)
                                    },
                                    valueRange = -2000f..2000f,
                                    steps = 39
                                )
                            }
                        }
                    }

                    // 7. External Subtitles (.srt / .vtt) Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = SubtitlesIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "External Subtitles",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (CastSessionManager.activeSubtitleName != null) {
                                    Text(
                                        "Remove",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            SubtitleManager.clearSubtitles()
                                            Toast.makeText(context, "Subtitles removed", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }

                            if (CastSessionManager.activeSubtitleName != null) {
                                Text(
                                    text = "Active: ${CastSessionManager.activeSubtitleName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "Inject local .srt or .vtt subtitle files directly into your cast stream.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    subtitlePickerLauncher.launch("*/*")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose .SRT or .VTT File", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 8. Queue / Play Next Playlist Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = QueueIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "Up Next (${CastSessionManager.mediaQueue.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (CastSessionManager.mediaQueue.isNotEmpty()) {
                                    Text(
                                        "Clear All",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            CastSessionManager.clearQueue()
                                            Toast.makeText(context, "Queue cleared", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }

                            if (CastSessionManager.mediaQueue.isEmpty()) {
                                Text(
                                    text = "Your playlist is empty. Tap 'Queue' on any web or device stream to build a binge-watch list.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CastSessionManager.mediaQueue.forEachIndexed { index, video ->
                                        val title = video.title.ifEmpty { MediaExtractorClient.extractFilenameFromUrl(video.url) }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Text(
                                                        "#${index + 1}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        title,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    IconButton(
                                                        onClick = {
                                                            lifecycleScope.launch {
                                                                CastSessionManager.removeFromQueue(index)
                                                                CastSessionManager.play(activeDevice, video.url, title)
                                                                CastPlaybackService.start(
                                                                    context = this@CastControlActivity,
                                                                    title = title,
                                                                    deviceName = activeDevice.name,
                                                                    ip = activeDevice.ipAddress,
                                                                    port = targetPort,
                                                                    url = video.url
                                                                )
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Now", modifier = Modifier.size(18.dp))
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            CastSessionManager.removeFromQueue(index)
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            lifecycleScope.launch {
                                                CastSessionManager.playNext()
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Play Next from Queue", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // 9. Volume Slider
                    var volumeState by remember { mutableStateOf(CastSessionManager.volume * 100f) }
                    LaunchedEffect(CastSessionManager.volume) {
                        volumeState = CastSessionManager.volume * 100f
                    }
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
                                        CastSessionManager.setVolume(volumeState / 100f)
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

                    // 10. Playback Speed Selector
                    var speedState by remember { mutableStateOf(1.0f) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Playback Speed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${speedState}x",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
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
                                                    targetPort
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

                    // 11. Stop Casting Button
                    Button(
                        onClick = {
                            lifecycleScope.launch {
                                CastPlaybackService.stop(this@CastControlActivity)
                                CastSessionManager.stop()
                                Toast.makeText(this@CastControlActivity, "Playback stopped", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
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
