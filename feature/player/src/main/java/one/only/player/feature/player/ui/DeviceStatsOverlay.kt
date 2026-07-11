package one.only.player.feature.player.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import java.io.FileReader
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

data class DeviceStats(
    val cpuPercent: Int = 0,
    val tempCelsius: Float = 0f,
    val peakBatteryTemp: Float = 0f,
    val tempRise: Float = 0f,
    val usedMemMb: Long = 0,
    val totalMemMb: Long = 0,
    val batteryPercent: Int = 0,
    val batteryCharging: Boolean = false,
    val batteryWatts: String = "-- W",
    val batteryRate: String = "Unknown",
    val thermalStateText: String = "Normal",
    val sessionDrainText: String = "0%",
    val burnRateText: String = "Calculating...",
    val estRemainingPlaybackText: String = "Calculating...",
    val videoFps: Float = 0f,
    val fileName: String = "--",
    val renderContext: String = "--",
    val cache: String = "--",
    val droppedFrames: String = "--",
    val videoCodec: String = "--",
    val audioCodec: String = "--",
    val gpuEstimatePercent: Float = 0f,
    val networkText: String = "0 KB/s",
    val networkMbps: Float = 0f,
    val networkHistory: List<Float> = emptyList(),
    val batteryPercentText: String = "--%",
    val batteryWattsText: String = "-- W",
    val batteryTempText: String = "--°C",
    val hdrActive: String = "--",
    val sessionPlayTimeText: String = "00:00:00",
    val decoderEfficiencyText: String = "Unknown",
    val totalDataConsumedText: String = "0 Bytes",
    val stallCountText: String = "0 stalls",
)

data class PlaybackStats(
    val videoResolution: String = "N/A",
    val videoCodec: String = "N/A",
    val videoBitrate: String = "N/A",
    val audioCodec: String = "N/A",
    val audioSampleRate: String = "N/A",
    val audioChannels: String = "N/A",
    val bufferHealth: String = "0 / 0 ms",
    val playbackSpeed: String = "1.0x",
    val displayRefreshRate: String = "N/A",
    val screenResolution: String = "N/A",
    val reportedFps: Float = 0f,
    val formatFps: Float = 0f,
    val isHdr: Boolean = false,
)

@Composable
fun DeviceStatsOverlay(
    visible: Boolean,
    player: Player?,
    modifier: Modifier = Modifier,
    videoFps: Float = 0f,
    videoDecoderName: String? = null,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    var deviceStats by remember { mutableStateOf(DeviceStats()) }
    var playbackStats by remember { mutableStateOf(PlaybackStats()) }

    LaunchedEffect(visible, player) {
        if (visible) {
            val exoPlayer = player as? androidx.media3.exoplayer.ExoPlayer

            var lastCpuMs = Process.getElapsedCpuTime().takeIf { it > 0 } ?: (readSelfCpuTicks() * 10)
            var lastRealTime = SystemClock.elapsedRealtime()

            // Session variables
            val sessionStartRealtime = SystemClock.elapsedRealtime()
            var startBatteryPercent: Int? = null
            var startBatteryTemp: Float? = null
            var peakBatteryTemp = 0.0f
            var totalActivePlayTimeMs = 0L

            var lastDropped = exoPlayer?.videoDecoderCounters?.droppedBufferCount ?: 0
            var lastSkipped = exoPlayer?.videoDecoderCounters?.skippedOutputBufferCount ?: 0
            var lastUidBytes = android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid())
            var accumulatedNetworkBytes = 0L
            var previousPausedForCache = false
            var stallCount = 0
            var stallTimeMs = 0L
            val history = ArrayDeque<Float>()

            while (true) {
                val currentTimeMs = SystemClock.elapsedRealtime()
                val timeDelta = (currentTimeMs - lastRealTime).coerceAtLeast(1L)

                val isPlaying = player?.isPlaying ?: false
                if (isPlaying) {
                    totalActivePlayTimeMs += timeDelta
                } else {
                    totalActivePlayTimeMs += timeDelta
                }

                val uri = player?.currentMediaItem?.localConfiguration?.uri
                    ?: player?.currentMediaItem?.mediaId?.let { if (it.startsWith("/")) Uri.fromFile(java.io.File(it)) else Uri.parse(it) }
                val fileName = player?.currentMediaItem?.mediaMetadata?.title?.toString()
                    ?: uri?.lastPathSegment
                    ?: "--"

                playbackStats = collectPlaybackStats(player, context, videoFps)

                // ── App CPU % ──────────────────────────────────────────────────────────
                val currentCpuMs = Process.getElapsedCpuTime().takeIf { it > 0 } ?: (readSelfCpuTicks() * 10)
                val cpuDelta = (currentCpuMs - lastCpuMs).coerceAtLeast(0L)
                val appCpuPercent = ((cpuDelta.toFloat() / timeDelta.toFloat()) * 100f).coerceIn(0f, 100f).toInt()

                // ── GPU pressure estimate (Frame Pressure) ──────────────────────────────
                val currentCounters = exoPlayer?.videoDecoderCounters
                val dropped = currentCounters?.droppedBufferCount ?: 0
                val skipped = currentCounters?.skippedOutputBufferCount ?: 0
                val droppedDelta = (dropped - lastDropped).coerceAtLeast(0)
                val skippedDelta = (skipped - lastSkipped).coerceAtLeast(0)

                val estFps = if (playbackStats.reportedFps > 0f) {
                    playbackStats.reportedFps
                } else if (playbackStats.formatFps > 0f) {
                    playbackStats.formatFps
                } else {
                    24f
                }
                val framePressure = if (estFps > 0f) {
                    ((droppedDelta + skippedDelta).toFloat() / estFps).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val gpuEstimate = (framePressure * 95f + if (estFps > 0f) 5f else 0f).coerceIn(0f, 100f)

                // ── Traffic Stats Speed & Data consumed ─────────────────────────────────
                val currentUidBytes = android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid())
                val netBps = if (lastUidBytes > 0L && currentUidBytes > lastUidBytes) {
                    val byteDiff = currentUidBytes - lastUidBytes
                    (byteDiff.toDouble() / (timeDelta / 1000.0))
                } else {
                    0.0
                }
                lastUidBytes = currentUidBytes

                val netText = when {
                    netBps >= 1024 * 1024 -> String.format("%.1f MB/s", netBps / (1024 * 1024))
                    netBps >= 1024 -> String.format("%.0f KB/s", netBps / 1024)
                    else -> "${netBps.toInt()} B/s"
                }
                val netMbps = ((netBps * 8.0) / (1024.0 * 1024.0)).toFloat().coerceAtLeast(0f)

                history.addLast(netMbps)
                if (history.size > 42) history.removeFirst()

                accumulatedNetworkBytes += (netBps * (timeDelta / 1000f)).toLong()
                val totalDataConsumedText = when {
                    accumulatedNetworkBytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", accumulatedNetworkBytes / (1024f * 1024f * 1024f))
                    accumulatedNetworkBytes >= 1024 * 1024 -> String.format("%.1f MB", accumulatedNetworkBytes / (1024f * 1024f))
                    accumulatedNetworkBytes >= 1024 -> String.format("%.0f KB", accumulatedNetworkBytes / 1024f)
                    else -> "$accumulatedNetworkBytes Bytes"
                }

                // ── Active playtime tracking ───────────────────────────────────────────
                val playSecs = totalActivePlayTimeMs / 1000L
                val sessionPlayTimeText = String.format("%02d:%02d:%02d", playSecs / 3600, (playSecs % 3600) / 60, playSecs % 60)

                // ── Battery numeric parsing & metrics ──────────────────────────────────
                val battery = readBatterySnapshot(context)
                val currentPercentText = battery.percentageText.replace("%", "").trim()
                val currentPercent = currentPercentText.toIntOrNull() ?: 0
                val currentTempText = battery.tempText.replace("°C", "").trim()
                val currentTemp = currentTempText.toFloatOrNull() ?: 0f

                if (startBatteryPercent == null && currentPercent > 0) {
                    startBatteryPercent = currentPercent
                }
                if (startBatteryTemp == null && currentTemp > 0f) {
                    startBatteryTemp = currentTemp
                }
                if (currentTemp > peakBatteryTemp) {
                    peakBatteryTemp = currentTemp
                }

                val sessionDrainText = if (startBatteryPercent != null) {
                    val drainPercent = startBatteryPercent - currentPercent
                    "$drainPercent%"
                } else {
                    "0%"
                }

                val activeHours = totalActivePlayTimeMs / 3600000f
                val burnRateText = if (startBatteryPercent != null && activeHours > 0.005f) {
                    val drainPercent = startBatteryPercent - currentPercent
                    val rate = drainPercent / activeHours
                    String.format("%.1f%% / hr", rate)
                } else {
                    "Calculating..."
                }

                val estRemainingPlaybackText = if (startBatteryPercent != null && activeHours > 0.005f) {
                    val drainPercent = startBatteryPercent - currentPercent
                    if (drainPercent > 0) {
                        val rate = drainPercent / activeHours
                        val hoursLeft = currentPercent / rate
                        val minsLeft = (hoursLeft * 60).roundToInt()
                        String.format("%dh %dm", minsLeft / 60, minsLeft % 60)
                    } else {
                        "Calculating..."
                    }
                } else {
                    "Calculating..."
                }

                val peakTempText = if (peakBatteryTemp > 0f) String.format("%.1f°C", peakBatteryTemp) else "--°C"
                val tempRiseText = if (startBatteryTemp != null) {
                    val rise = currentTemp - startBatteryTemp
                    String.format("%+.1f°C", rise)
                } else {
                    "+0.0°C"
                }

                val batteryTempFormatted = if (currentTemp > 0f) {
                    "%.1f°C (Peak: %s | Rise: %s)".format(currentTemp, peakTempText, tempRiseText)
                } else {
                    "--°C"
                }

                // ── Android Thermal Status ─────────────────────────────────────────────
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    powerManager?.currentThermalStatus ?: 0
                } else {
                    0
                }
                val thermalStateText = when (thermalStatus) {
                    0 -> "Normal"
                    1 -> "Light Throttling"
                    2 -> "Moderate Throttling"
                    3 -> "Severe Throttling"
                    4 -> "Critical Throttling"
                    5 -> "Emergency!"
                    6 -> "Overheating Shutdown!"
                    else -> "Normal"
                }

                // ── Decoder Efficiency ─────────────────────────────────────────────────
                val isSoftware = videoDecoderName == null ||
                    videoDecoderName.contains("c2.android", ignoreCase = true) ||
                    videoDecoderName.contains("omx.google", ignoreCase = true) ||
                    videoDecoderName.contains("ffmpeg", ignoreCase = true)
                val decoderEfficiencyText = if (isSoftware) {
                    "Low (Software Decoding, CPU-heavy)"
                } else {
                    "High (Hardware Direct, OpenGLES backend)"
                }

                // ── Network / Cache stalls ─────────────────────────────────────────────
                val pausedForCache = player?.playbackState == Player.STATE_BUFFERING
                if (pausedForCache) {
                    if (!previousPausedForCache) {
                        stallCount++
                    }
                    stallTimeMs += timeDelta
                }
                previousPausedForCache = pausedForCache
                val stallCountText = if (stallCount > 0) {
                    "$stallCount stalls (${String.format("%.1fs", stallTimeMs / 1000f)} total)"
                } else {
                    "0 stalls"
                }

                val bufferPos = player?.bufferedPosition ?: 0L
                val currentPos = player?.currentPosition ?: 0L
                val cacheDurationSec = ((bufferPos - currentPos).coerceAtLeast(0L) / 1000f)
                val cacheText = "%.1fs".format(cacheDurationSec)

                // ── HDR ────────────────────────────────────────────────────────────────
                val hdrActive = if (playbackStats.isHdr) "HDR Source | HDR Output" else "SDR Source | SDR Output"

                // ── Memory Info ────────────────────────────────────────────────────────
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                val totalMb = memInfo.totalMem / 1_048_576L
                val availMb = memInfo.availMem / 1_048_576L
                val usedMb = totalMb - availMb

                deviceStats = DeviceStats(
                    cpuPercent = appCpuPercent,
                    tempCelsius = currentTemp,
                    peakBatteryTemp = peakBatteryTemp,
                    tempRise = if (startBatteryTemp != null) currentTemp - startBatteryTemp else 0f,
                    usedMemMb = usedMb,
                    totalMemMb = totalMb,
                    batteryPercent = currentPercent,
                    batteryCharging = battery.percentageText.contains("⚡") || (battery.rateText.contains("Charging", ignoreCase = true)),
                    batteryWatts = battery.wattsText,
                    batteryRate = battery.rateText,
                    thermalStateText = thermalStateText,
                    sessionDrainText = sessionDrainText,
                    burnRateText = burnRateText,
                    estRemainingPlaybackText = estRemainingPlaybackText,
                    videoFps = videoFps,
                    fileName = fileName,
                    renderContext = "SurfaceView",
                    cache = cacheText,
                    droppedFrames = "%d (decoder) | %d (output) | +%d/+%d delta".format(dropped, skipped, droppedDelta, skippedDelta),
                    videoCodec = playbackStats.videoCodec,
                    audioCodec = playbackStats.audioCodec,
                    gpuEstimatePercent = gpuEstimate,
                    networkText = netText,
                    networkMbps = netMbps,
                    networkHistory = history.toList(),
                    batteryPercentText = "%d%%".format(currentPercent),
                    batteryWattsText = battery.wattsText,
                    batteryTempText = batteryTempFormatted,
                    hdrActive = hdrActive,
                    sessionPlayTimeText = sessionPlayTimeText,
                    decoderEfficiencyText = decoderEfficiencyText,
                    totalDataConsumedText = totalDataConsumedText,
                    stallCountText = stallCountText,
                )

                delay(1000)

                lastCpuMs = currentCpuMs
                lastRealTime = currentTimeMs
                lastDropped = dropped
                lastSkipped = skipped
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 450.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xD9121212),
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Header with Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "STATS FOR NERDS",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.padding(0.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Stats",
                            tint = Color.White,
                        )
                    }
                }

                // Playback & Video
                StatsSectionHeader("Playback & Video")
                StatItem("File", deviceStats.fileName)

                val decoderLabel = when {
                    videoDecoderName == null -> "-"
                    videoDecoderName.contains("c2.android", ignoreCase = true) -> "[SW] $videoDecoderName"
                    videoDecoderName.contains("omx.google", ignoreCase = true) -> "[SW] $videoDecoderName"
                    videoDecoderName.contains("ffmpeg", ignoreCase = true) -> "[SW] $videoDecoderName"
                    else -> "[HW] $videoDecoderName"
                }
                val decoderColor = if (decoderLabel.contains("[SW]")) Color(0xFFFF9800) else Color(0xFF4CAF50)
                StatItem("Decoder", decoderLabel, decoderColor)
                StatItem("Resolution", playbackStats.videoResolution)
                StatItem("Video Codec", playbackStats.videoCodec)
                StatItem("Bitrate", playbackStats.videoBitrate)

                val fpsToDisplay = if (playbackStats.reportedFps > 0f) {
                    "%.3f fps".format(playbackStats.reportedFps)
                } else if (playbackStats.formatFps > 0f) {
                    "%.3f fps".format(playbackStats.formatFps)
                } else {
                    "N/A"
                }
                StatItem("Refresh Rate", "${playbackStats.displayRefreshRate} (Video: $fpsToDisplay)")
                StatItem("Screen Resolution", playbackStats.screenResolution)
                StatItem("Dropped Frames", deviceStats.droppedFrames)
                StatItem("HDR Mode", deviceStats.hdrActive)

                // Audio
                StatsSectionHeader("Audio")
                StatItem("Audio Codec", playbackStats.audioCodec)
                StatItem("Sample Rate", playbackStats.audioSampleRate)
                StatItem("Channels", playbackStats.audioChannels)

                // Cache & Network
                StatsSectionHeader("Cache & Network")
                StatItem("Buffer (Buf/Cur)", playbackStats.bufferHealth)
                StatItem("Remaining Buffer", deviceStats.cache)
                StatItem("Stalls Count", deviceStats.stallCountText)
                StatItem("Playback Speed", playbackStats.playbackSpeed)
                StatItem("Data Consumed", deviceStats.totalDataConsumedText)
                StatItem("Download Speed", "${deviceStats.networkText} (${String.format("%.1f", deviceStats.networkMbps)} Mbps)")

                if (deviceStats.networkHistory.isNotEmpty()) {
                    NetworkSparkline(
                        points = deviceStats.networkHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .padding(top = 4.dp, bottom = 4.dp),
                    )
                }

                // Power & Thermals
                StatsSectionHeader("Power & Thermals")
                StatItem("Battery Level", deviceStats.batteryPercentText)
                StatItem("Power Draw", "${deviceStats.batteryWattsText} (${deviceStats.batteryRate})")
                StatItem("Temperature", deviceStats.batteryTempText)
                StatItem(
                    "Thermal State",
                    deviceStats.thermalStateText,
                    when (deviceStats.thermalStateText) {
                        "Normal" -> Color(0xFF4CAF50)
                        "Light Throttling" -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    },
                )
                StatItem("Session Drain", "${deviceStats.sessionDrainText} (Burn: ${deviceStats.burnRateText})")
                StatItem("Projected Playback", deviceStats.estRemainingPlaybackText)

                // Performance & System
                StatsSectionHeader("Performance & System")
                StatItem("Active Playtime", deviceStats.sessionPlayTimeText)
                StatItem("System Memory", "${deviceStats.usedMemMb} / ${deviceStats.totalMemMb} MB", Color.Cyan.copy(alpha = 0.85f))

                CustomProgressBar(
                    label = "App CPU (this process)",
                    value = deviceStats.cpuPercent.toFloat(),
                    color = levelColor(deviceStats.cpuPercent.toFloat() / 100f),
                )

                CustomProgressBar(
                    label = "Frame Pressure (drop-based est.)",
                    value = deviceStats.gpuEstimatePercent,
                    color = levelColor(deviceStats.gpuEstimatePercent / 100f),
                )
            }
        }
    }
}

@Composable
private fun StatsSectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
        Text(
            text = title.uppercase(),
            color = Color(0xFF81D4FA),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = Color(0x1AFFFFFF))
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xB3FFFFFF),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            ),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun CustomProgressBar(
    label: String,
    value: Float,
    color: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = Color(0xB3FFFFFF),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
            )
            Text(
                text = "${value.roundToInt()}%",
                color = color,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
                fontWeight = FontWeight.Bold,
            )
        }
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = color,
            trackColor = Color(0x1AFFFFFF),
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
private fun NetworkSparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val maxY = (points.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()

        points.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = (value / maxY).coerceIn(0f, 1f)
            val y = size.height - (normalized * size.height)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x4D00E5FF), Color(0x0500E5FF)),
            ),
        )
        drawPath(
            path = linePath,
            color = Color(0xFF00E5FF),
            style = Stroke(width = 2f),
        )
    }
}

private fun collectPlaybackStats(player: Player?, context: Context, reportedFps: Float): PlaybackStats {
    var vRes = "N/A"
    var vCodec = "N/A"
    var vBitrate = "N/A"
    var aCodec = "N/A"
    var aSampleRate = "N/A"
    var aChannels = "N/A"
    var formatFps = 0f
    var isHdr = false

    val groups = player?.currentTracks?.groups ?: emptyList()
    for (group in groups) {
        if (group.isSelected) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        val format = group.getTrackFormat(i)
                        vRes = if (format.width != Format.NO_VALUE && format.height != Format.NO_VALUE) {
                            "${format.width}x${format.height}"
                        } else {
                            "N/A"
                        }
                        vCodec = format.sampleMimeType ?: "N/A"
                        vBitrate = if (format.bitrate != Format.NO_VALUE) "${format.bitrate / 1000} kbps" else "N/A"
                        formatFps = if (format.frameRate != Format.NO_VALUE.toFloat() && format.frameRate > 0f) format.frameRate else 0f
                        isHdr = format.colorInfo?.let { androidx.media3.common.ColorInfo.isTransferHdr(it) } == true
                        break
                    }
                }
            } else if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        val format = group.getTrackFormat(i)
                        aCodec = format.sampleMimeType ?: "N/A"
                        aSampleRate = if (format.sampleRate != Format.NO_VALUE) "${format.sampleRate} Hz" else "N/A"
                        aChannels = when (format.channelCount) {
                            1 -> "Mono (1)"
                            2 -> "Stereo (2)"
                            6 -> "5.1 (6)"
                            8 -> "7.1 (8)"
                            Format.NO_VALUE -> "N/A"
                            else -> "${format.channelCount} ch"
                        }
                        break
                    }
                }
            }
        }
    }

    if (vBitrate == "N/A") {
        getLocalVideoBitrate(player, context)?.let {
            vBitrate = it
        }
    }

    val bufferPos = player?.bufferedPosition ?: 0L
    val currentPos = player?.currentPosition ?: 0L
    val bufferHealth = "%.1f / %.1f s".format(bufferPos / 1000f, currentPos / 1000f)
    val speed = player?.playbackParameters?.speed ?: 1f

    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }
    val refreshRate = display?.refreshRate ?: 0f

    val screenRes = context.resources.displayMetrics.run { "${widthPixels}x$heightPixels" }

    return PlaybackStats(
        videoResolution = vRes,
        videoCodec = vCodec,
        videoBitrate = vBitrate,
        audioCodec = aCodec,
        audioSampleRate = aSampleRate,
        audioChannels = aChannels,
        bufferHealth = bufferHealth,
        playbackSpeed = "%.2fx".format(speed),
        displayRefreshRate = if (refreshRate > 0f) "%.1f Hz".format(refreshRate) else "N/A",
        screenResolution = screenRes,
        reportedFps = reportedFps,
        formatFps = formatFps,
        isHdr = isHdr,
    )
}

private fun getLocalVideoBitrate(player: Player?, context: Context): String? {
    val playerInstance = player ?: return null
    val mediaItem = playerInstance.currentMediaItem ?: return null
    val uri = mediaItem.localConfiguration?.uri ?: mediaItem.mediaId.let { if (it.startsWith("/")) Uri.fromFile(java.io.File(it)) else Uri.parse(it) }
    val durationMs = playerInstance.duration
    if (durationMs <= 0) return null

    val sizeInBytes = try {
        when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return null
                val file = java.io.File(path)
                if (file.exists()) file.length() else 0L
            }
            "content" -> {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getLong(0)
                    } else {
                        0L
                    }
                } ?: 0L
            }
            else -> 0L
        }
    } catch (e: Exception) {
        0L
    }

    if (sizeInBytes <= 0) return null

    val durationSeconds = durationMs / 1000f
    val bitrateBps = (sizeInBytes * 8) / durationSeconds
    val bitrateKbps = bitrateBps / 1000f
    return if (bitrateKbps >= 1000f) {
        "%.2f Mbps".format(bitrateKbps / 1000f)
    } else {
        "%.0f kbps".format(bitrateKbps)
    }
}

private fun levelColor(level: Float): Color = when {
    level < 0.5f -> Color(0xFF4CAF50) // green
    level < 0.75f -> Color(0xFFFF9800) // orange
    else -> Color(0xFFF44336) // red
}

private fun readSelfCpuTicks(): Long = try {
    val statContent = FileReader("/proc/self/stat").use { it.readText() }
    val lastParen = statContent.lastIndexOf(')')
    if (lastParen != -1) {
        val remaining = statContent.substring(lastParen + 1).trim()
        val parts = remaining.split("\\s+".toRegex())
        if (parts.size > 14) {
            parts[11].toLong() + parts[12].toLong() + parts[13].toLong() + parts[14].toLong()
        } else {
            0L
        }
    } else {
        0L
    }
} catch (_: Exception) {
    0L
}

internal data class BatterySnapshot(
    val percentageText: String,
    val rateText: String,
    val wattsText: String,
    val tempText: String,
)

internal fun readBatterySnapshot(context: Context): BatterySnapshot {
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percentage =
        if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        } else {
            null
        }

    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val currentMicroAmps =
        listOf(
            batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
        ).firstOrNull { value ->
            value != null && value != Long.MIN_VALUE && value != 0L
        }

    val voltageMilliVolts = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }

    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    val statusText =
        when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else ->
                when {
                    (currentMicroAmps ?: 0L) > 0L -> "Charging"
                    (currentMicroAmps ?: 0L) < 0L -> "Discharging"
                    else -> "Unknown"
                }
        }

    val currentMilliAmps = currentMicroAmps?.let { abs(it).toFloat() / 1000f }?.takeIf { it > 0f }
    val rateText =
        if (currentMilliAmps != null && statusText != "Full" && statusText != "Unknown") {
            val formattedCurrent =
                if (currentMilliAmps >= 100f) {
                    String.format("%.0f mA", currentMilliAmps)
                } else {
                    String.format("%.1f mA", currentMilliAmps)
                }
            "$statusText $formattedCurrent"
        } else {
            statusText
        }

    val wattsText =
        if (currentMilliAmps != null && voltageMilliVolts != null && voltageMilliVolts > 0) {
            val watts = (currentMilliAmps / 1000f) * (voltageMilliVolts / 1000f)
            String.format("%.2f W", watts)
        } else {
            "-- W"
        }

    val tempCelsius = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.takeIf { it > 0 }
    val tempText =
        if (tempCelsius != null) {
            String.format("%.1f°C", tempCelsius / 10f)
        } else {
            "--°C"
        }

    return BatterySnapshot(
        percentageText = percentage?.let { "$it%" } ?: "--%",
        rateText = rateText,
        wattsText = wattsText,
        tempText = tempText,
    )
}
