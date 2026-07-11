package one.only.player.feature.player.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class VideoThumbnailRetriever(private val context: Context, private val uri: Uri?) {
    private val retriever = MediaMetadataRetriever()
    private val _thumbnailState = MutableStateFlow<Bitmap?>(null)
    val thumbnailState: StateFlow<Bitmap?> = _thumbnailState.asStateFlow()

    init {
        try {
            if (uri != null) {
                retriever.setDataSource(context, uri)
            }
        } catch (e: Exception) {
            // Ignore initialization error
        }
    }

    suspend fun getThumbnailAtTime(timeMs: Long) {
        if (uri == null) return
        withContext(Dispatchers.IO) {
            try {
                val bitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                val scaled = bitmap?.let {
                    val maxWidth = 300
                    if (it.width > maxWidth) {
                        val ratio = maxWidth.toFloat() / it.width
                        Bitmap.createScaledBitmap(it, maxWidth, (it.height * ratio).toInt(), true)
                    } else {
                        it
                    }
                }
                _thumbnailState.value = scaled
            } catch (e: Exception) {
                // Ignore extraction error
            }
        }
    }

    fun release() {
        try {
            retriever.release()
        } catch (e: Exception) {
            // Ignore
        }
    }
}

@Composable
fun rememberVideoThumbnailRetriever(uri: Uri?): VideoThumbnailRetriever {
    val context = LocalContext.current
    val retriever = remember(uri) { VideoThumbnailRetriever(context, uri) }

    DisposableEffect(retriever) {
        onDispose {
            retriever.release()
        }
    }

    return retriever
}
