package one.only.player.core.media

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.ContentMetadata
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toAndroidUri
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.FileSystem
import one.only.player.core.common.Logger

class VideoThumbnailDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val strategy: ThumbnailStrategy,
    private val diskCache: Lazy<DiskCache?>,
) : Decoder {

    companion object {
        private const val MAX_THUMBNAIL_SIZE = 512
        private const val THUMBNAIL_CACHE_VERSION = 2
        private val decodeSemaphore = Semaphore(2)
    }

    private fun tryLoadSystemThumbnail(): Bitmap? {
        val uri = when (val metadata = source.metadata) {
            is ContentMetadata -> metadata.uri.toAndroidUri()
            else -> {
                if (source.fileSystem !== FileSystem.SYSTEM) return null
                findContentUriForPath(source.file().toFile().path) ?: return null
            }
        }
        val start = System.currentTimeMillis()
        return try {
            options.context.contentResolver.loadThumbnail(
                uri,
                Size(MAX_THUMBNAIL_SIZE, MAX_THUMBNAIL_SIZE),
                null,
            ).also {
                logThumbnail { "systemThumbnail ok ${System.currentTimeMillis() - start}ms uri=$uri" }
            }
        } catch (e: Exception) {
            logThumbnail { "systemThumbnail fail ${System.currentTimeMillis() - start}ms uri=$uri err=${e.message}" }
            null
        }
    }

    private fun findContentUriForPath(path: String): android.net.Uri? {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.Video.Media._ID)
        return try {
            options.context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Video.Media.DATA} = ?",
                arrayOf(path),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    ContentUris.withAppendedId(collection, id)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private val sourceCacheKey: String
        get() = options.diskCacheKey ?: run {
            val metadata = source.metadata
            when {
                metadata is ContentMetadata -> metadata.uri.toAndroidUri().toString()
                source.fileSystem === FileSystem.SYSTEM -> source.file().toFile().path
                else -> error("Not supported")
            }
        }

    private val diskCacheKey: String
        get() = "$sourceCacheKey#thumbnail=v$THUMBNAIL_CACHE_VERSION:${strategy.cacheKey}"

    @OptIn(ExperimentalCoilApi::class)
    override suspend fun decode(): DecodeResult {
        val key = diskCacheKey
        logThumbnail { "decode start strategy=${strategy.logName} key=$key" }
        readFromDiskCache()?.use { snapshot ->
            val file = snapshot.data.toFile()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)

            val cachedBitmap = BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
                },
            )

            if (cachedBitmap != null) {
                logThumbnail { "diskCache hit strategy=${strategy.logName} key=$key" }
                return DecodeResult(
                    image = cachedBitmap.toDrawable(options.context.resources).asImage(),
                    isSampled = true,
                )
            }
        }

        return decodeSemaphore.withPermit {
            tryLoadMediaMetadataRetriever()?.let { mmrBitmap ->
                logThumbnail { "mmr ok strategy=${strategy.logName} key=$key" }
                val bitmap = writeToDiskCache(mmrBitmap)
                return@withPermit DecodeResult(
                    image = bitmap.toDrawable(options.context.resources).asImage(),
                    isSampled = true,
                )
            }

            tryLoadSystemThumbnail()?.let { systemBitmap ->
                logThumbnail { "systemThumbnail fallback strategy=${strategy.logName} key=$key" }
                val bitmap = writeToDiskCache(systemBitmap)
                return@withPermit DecodeResult(
                    image = bitmap.toDrawable(options.context.resources).asImage(),
                    isSampled = true,
                )
            }

            logThumbnail { "decode fail strategy=${strategy.logName} key=$key" }
            throw IllegalStateException("Failed to get video thumbnail for key=$key")
        }
    }

    private fun tryLoadMediaMetadataRetriever(): Bitmap? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            val metadata = source.metadata
            if (metadata is ContentMetadata) {
                retriever.setDataSource(options.context, metadata.uri.toAndroidUri())
            } else if (source.fileSystem === FileSystem.SYSTEM) {
                retriever.setDataSource(source.file().toFile().path)
            } else {
                return null
            }

            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val timeUs = when (strategy) {
                is ThumbnailStrategy.FirstFrame -> 0L
                is ThumbnailStrategy.FrameAtPercentage -> (durationMs * strategy.percentage).toLong() * 1000L
                is ThumbnailStrategy.Hybrid -> (durationMs * strategy.percentage).toLong() * 1000L
            }
            retriever.getFrameAtTime(timeUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.scaleToFit()
        } catch (e: Exception) {
            logThumbnail { "mmr frame fail err=${e.message}" }
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        if (width <= MAX_THUMBNAIL_SIZE && height <= MAX_THUMBNAIL_SIZE) return 1
        var inSampleSize = 1
        val maxDimension = maxOf(width, height)
        while (maxDimension / (inSampleSize * 2) >= MAX_THUMBNAIL_SIZE) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun Bitmap.scaleToFit(): Bitmap {
        if (width <= MAX_THUMBNAIL_SIZE && height <= MAX_THUMBNAIL_SIZE) return this
        val scale = MAX_THUMBNAIL_SIZE.toFloat() / maxOf(width, height)
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
        if (scaled !== this) recycle()
        return scaled
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? = if (options.diskCachePolicy.readEnabled) {
        diskCache.value?.openSnapshot(diskCacheKey)
    } else {
        null
    }

    private fun writeToDiskCache(inBitmap: Bitmap): Bitmap {
        if (!options.diskCachePolicy.writeEnabled) return inBitmap
        val editor = diskCache.value?.openEditor(diskCacheKey) ?: return inBitmap
        try {
            editor.data.toFile().outputStream().use { output ->
                inBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
            }
            editor.commitAndOpenSnapshot()?.use { snapshot ->
                val outBitmap = snapshot.data.toFile().inputStream().use { input ->
                    BitmapFactory.decodeStream(input)
                }
                inBitmap.recycle()
                return outBitmap
            }
        } catch (_: Exception) {
            try {
                editor.abort()
            } catch (_: Exception) {
            }
        }
        return inBitmap
    }

    class Factory(
        private val thumbnailStrategy: () -> ThumbnailStrategy,
    ) : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (!isApplicable(result.mimeType)) return null
            val strategy = thumbnailStrategy()
            return VideoThumbnailDecoder(
                source = result.source,
                options = options,
                strategy = strategy,
                diskCache = lazy { imageLoader.diskCache },
            )
        }

        private fun isApplicable(mimeType: String?): Boolean = mimeType != null && mimeType.startsWith("video/")
    }
}

sealed class ThumbnailStrategy {
    data object FirstFrame : ThumbnailStrategy()
    data class FrameAtPercentage(val percentage: Float = 0.5f) : ThumbnailStrategy()
    data class Hybrid(val percentage: Float = 0.5f) : ThumbnailStrategy()
}

private val ThumbnailStrategy.logName: String
    get() = when (this) {
        ThumbnailStrategy.FirstFrame -> "firstFrame"
        is ThumbnailStrategy.FrameAtPercentage -> "frameAt:$percentage"
        is ThumbnailStrategy.Hybrid -> "hybrid:$percentage"
    }

private val ThumbnailStrategy.cacheKey: String
    get() = when (this) {
        ThumbnailStrategy.FirstFrame -> "first"
        is ThumbnailStrategy.FrameAtPercentage -> "frameAt:$percentage"
        is ThumbnailStrategy.Hybrid -> "hybrid:$percentage"
    }

private inline fun logThumbnail(message: () -> String) {
    Logger.info("VideoThumb", message())
}
