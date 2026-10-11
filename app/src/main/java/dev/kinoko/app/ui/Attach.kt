package dev.kinoko.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.kinoko.app.ATTACH_MAX
import dev.kinoko.app.Attachment
import dev.kinoko.app.readAttachment
import dev.kinoko.app.sizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The 📎 by the input: a photo from the gallery, one taken now, or any file. What is picked is read
 * (a photo shrunk) off the main thread and handed to `onPicked`; `onRefused` says why one was not.
 */
@Composable
fun AttachButton(total: Int, onPicked: (Attachment) -> Unit, onRefused: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var shot by remember { mutableStateOf<Uri?>(null) }
    val take = { uri: Uri?, name: String ->
        if (uri != null) scope.launch {
            val read = withContext(Dispatchers.IO) { runCatching { readAttachment(context, uri, name) }.getOrNull() }
            when {
                read == null -> onRefused("Could not read that file")
                total + read.size > ATTACH_MAX -> onRefused("Too big: a message carries at most ${sizeText(ATTACH_MAX)} of files")
                else -> onPicked(read)
            }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { take(it, "photo.jpg") }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { take(it, "file") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) take(shot, "photo.jpg") }
    val openCamera = {
        // The camera app writes the photo into our cache, through the FileProvider.
        val file = File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "photo-${System.currentTimeMillis()}.jpg") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        shot = uri
        camera.launch(uri)
    }
    // The app holds CAMERA (for the QR): Android then wants it granted before the camera app is asked.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else onRefused("The camera needs its permission")
    }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.AttachFile, "attach", tint = Kinoko.muted) }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Photo") }, leadingIcon = { Icon(Icons.Outlined.Image, null) }, onClick = {
                menu = false
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            DropdownMenuItem(text = { Text("Camera") }, leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) }, onClick = {
                menu = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
                else permission.launch(Manifest.permission.CAMERA)
            })
            DropdownMenuItem(text = { Text("File") }, leadingIcon = { Icon(Icons.Outlined.Description, null) }, onClick = {
                menu = false
                document.launch(arrayOf("*/*"))
            })
        }
    }
}

/** What goes with the next message, above the input: name and size, ✕ to leave one out. */
@Composable
fun AttachmentChips(items: List<Attachment>, onRemove: (Attachment) -> Unit) {
    if (items.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (item in items) {
            Row(Modifier.background(Kinoko.panel, RoundedCornerShape(14.dp)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (item.mime.startsWith("image/")) "🖼" else "📄"} ${item.name} · ${sizeText(item.size)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Kinoko.cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
                IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "remove", tint = Kinoko.muted, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}
