package dev.kinoko.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

/** A file to send with a message: the computer saves it and tells the agent where it is. */
class Attachment(val name: String, val mime: String, val bytes: ByteArray) {
    val size: Int get() = bytes.size
}

/** All attachments of one message together stay under this (a message travels whole, in base64). */
const val ATTACH_MAX = 20 * 1024 * 1024

/** A photo is sent at most this many pixels on its long side: the model sees it, the link stays quick. */
const val PHOTO_MAX = 2048

/** How much to shrink a picture of w×h so its long side is at most `max` (a power of two, as Android decodes). */
fun sampleSize(w: Int, h: Int, max: Int): Int {
    var n = 1
    while (maxOf(w, h) / n > max) n *= 2
    return n
}

/** The name a re-encoded photo goes by: its own, as .jpg. */
fun jpegName(name: String): String = name.substringBeforeLast('.', name).ifBlank { "photo" } + ".jpg"

/** "512 KB", "3.4 MB". */
fun sizeText(bytes: Int): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

/** Reads what a picker or the camera gave: a picture shrunk and re-encoded as JPEG, anything else as it is. */
fun readAttachment(context: Context, uri: Uri, fallbackName: String = "file"): Attachment? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: fallbackName
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (!mime.startsWith("image/") || mime == "image/gif") return Attachment(name, mime, bytes)
    // A photo: its size first, then decoded smaller, then JPEG.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return Attachment(name, mime, bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, PHOTO_MAX) })
        ?: return Attachment(name, mime, bytes)
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    bitmap.recycle()
    return Attachment(jpegName(name), "image/jpeg", out.toByteArray())
}
