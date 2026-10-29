package com.cypressit.cyvault.feature.vault.home.data.local

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Reads a Photo Picker content URI, turns the photo upright from its EXIF orientation (which
 * re-encoding would otherwise lose) and returns it as a JPEG of at most [MAX_SIDE] px.
 */
class AndroidPickedPhotoReader(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : PickedPhotoReader {

    private companion object {
        // Big enough for a sharp 512 px avatar after the server's own resize, small enough to upload fast.
        const val MAX_SIDE = 1024
        const val JPEG_QUALITY = 90
    }

    private val resolver: ContentResolver = context.contentResolver

    // Reading the file and decoding it both block.
    override suspend fun readJpeg(photo: PickedPhoto): ByteArray = withContext(ioDispatcher) {
        uprightJpeg(Uri.parse(photo.reference))
    }

    private fun uprightJpeg(uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }

        // Decoding at a power-of-two fraction keeps a 50 MP photo from filling the memory.
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_SIDE) sampleSize *= 2
        val decoded = resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("Not an image")

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val scale = min(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(-90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        // JPEG has no transparency: put transparent PNGs on white instead of black.
        val opaque = Bitmap.createBitmap(upright.width, upright.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply {
                drawColor(Color.WHITE)
                drawBitmap(upright, 0f, 0f, null)
            }
        }

        return ByteArrayOutputStream().use { output ->
            opaque.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            output.toByteArray()
        }
    }
}
