package com.m57.hermescontrol.ui.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.m57.hermescontrol.data.remote.OkHttpProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun ImagePreviewDialog(
    source: String,
    fileName: String,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember(source) { mutableStateOf(1f) }
    var offsetX by remember(source) { mutableStateOf(0f) }
    var offsetY by remember(source) { mutableStateOf(0f) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = source,
                    contentDescription = fileName,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                            )
                            .pointerInput(source) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close preview",
                        tint = Color.White,
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                ) {
                    FilledTonalButton(
                        enabled = !isSaving,
                        onClick = {
                            isSaving = true
                            saveMessage = null
                            scope.launch {
                                val result =
                                    withContext(Dispatchers.IO) {
                                        saveImageToGallery(context, source, fileName)
                                    }
                                isSaving = false
                                saveMessage = result.fold(
                                    onSuccess = { "Saved to Pictures/Hermes" },
                                    onFailure = { "Save failed: ${it.message ?: "unknown error"}" },
                                )
                            }
                        },
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Download, contentDescription = null)
                        }
                        Text("Save", modifier = Modifier.padding(start = 6.dp))
                    }
                }

                saveMessage?.let { message ->
                    Text(
                        text = message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 78.dp)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private suspend fun saveImageToGallery(
    context: Context,
    source: String,
    fileName: String,
): Result<Unit> = runCatching {
    val bytes =
        if (source.startsWith("http://") || source.startsWith("https://")) {
            val request = Request.Builder().url(source).get().build()
            OkHttpProvider.base.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.body?.bytes() ?: error("empty response")
            }
        } else {
            context.contentResolver.openInputStream(Uri.parse(source))?.use { it.readBytes() }
                ?: error("cannot open image")
        }

    check(BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) { "invalid image data" }
    val mimeType = guessImageMimeType(fileName, bytes)
    val displayName = safeImageName(fileName, mimeType)
    val resolver = context.contentResolver
    val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    val values =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Hermes")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
    val destination = resolver.insert(collection, values) ?: error("cannot create gallery item")
    try {
        resolver.openOutputStream(destination)?.use { it.write(bytes) }
            ?: error("cannot open gallery output")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                destination,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        }
    } catch (error: Throwable) {
        resolver.delete(destination, null, null)
        throw error
    }
}

private fun guessImageMimeType(fileName: String, bytes: ByteArray): String =
    when {
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        fileName.lowercase(Locale.ROOT).endsWith(".webp") -> "image/webp"
        else -> "image/jpeg"
    }

private fun safeImageName(fileName: String, mimeType: String): String {
    val raw = fileName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "hermes-image" }
    val clean = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val ext = when (mimeType) {
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        else -> ".jpg"
    }
    return if (clean.contains('.')) clean else "$clean$ext"
}
