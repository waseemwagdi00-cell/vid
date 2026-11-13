package com.videograbber

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun extractUrl(t: String?): String = Regex("https?://\\S+").find(t ?: "")?.value ?: ""
fun urlFrom(i: Intent): String =
    if (i.action == Intent.ACTION_SEND) extractUrl(i.getStringExtra(Intent.EXTRA_TEXT)) else ""

class MainActivity : ComponentActivity() {
    private var url by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = urlFrom(intent)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { Sheet(url, onUrl = { url = it }, onClose = { finish() }) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val u = urlFrom(intent)
        if (u.isNotEmpty()) url = u
    }

    // the clipboard can only be read once the window has focus (Android 10+)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && url.isEmpty()) {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cb.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            url = extractUrl(text)
        }
    }
}

data class Info(val title: String, val heights: List<Int>)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Sheet(url: String, onUrl: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    var info by remember { mutableStateOf<Info?>(null) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var audio by remember { mutableStateOf(false) }
    var height by remember { mutableStateOf(0) }
    var ext by remember { mutableStateOf("mp4") }
    var progress by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(url) {
        info = null; msg = ""
        if (url.isEmpty()) return@LaunchedEffect
        loading = true
        try {
            info = withContext(Dispatchers.IO) {
                App.ready.await().getOrThrow()
                val i = YoutubeDL.getInstance().getInfo(url)
                val hs = (i.formats ?: emptyList()).map { f -> f.height ?: 0 }
                    .filter { it > 0 }.distinct().sortedDescending().take(6)
                Info(i.title ?: url, hs.ifEmpty { listOf(1080, 720, 480, 360) })
            }
        } catch (e: Exception) {
            msg = "ما قدرتش أقرأ الرابط: ${e.message}"
        }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (url.isEmpty()) {
                Text("الصق رابط الفيديو", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(typed, { typed = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { onUrl(extractUrl(typed)) }, enabled = extractUrl(typed).isNotEmpty()) { Text("كمّل") }
            } else if (loading) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp)); Text("جاري قراءة الرابط...")
                }
            } else info?.let { i ->
                Text(i.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text("عايز إيه؟")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!audio, { audio = false; ext = "mp4" }, { Text("فيديو") })
                    FilterChip(audio, { audio = true; ext = "mp3" }, { Text("صوت بس") })
                }
                if (!audio) {
                    Text("الجودة")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(height == 0, { height = 0 }, { Text("أعلى جودة") })
                        i.heights.forEach { h -> FilterChip(height == h, { height = h }, { Text("${h}p") }) }
                    }
                }
                Text("الصيغة")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (audio) listOf("mp3", "m4a", "opus") else listOf("mp4", "mkv", "webm")).forEach { e ->
                        FilterChip(ext == e, { ext = e }, { Text(e.uppercase()) })
                    }
                }
                if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = {
                        busy = true; progress = 0f; msg = "جاري التحضير..."
                        val a = audio; val h = height; val e = ext
                        val ui = Handler(Looper.getMainLooper())
                        App.bg.launch {
                            try {
                                download(ctx, url, a, h, e) { p -> ui.post { progress = p / 100f; msg = "بينزل... ${p.toInt()}%" } }
                                ui.post {
                                    msg = "تم التحميل ✓ (في مجلد Downloads)"; busy = false
                                    Toast.makeText(ctx, "تم التحميل ✓", Toast.LENGTH_LONG).show()
                                }
                            } catch (ex: Exception) {
                                ui.post { msg = "حصل خطأ: ${ex.message}"; busy = false }
                            }
                        }
                    }) { Text("تحميل") }
                    TextButton(onClick = onClose) { Text("إغلاق") }
                }
            }
            if (msg.isNotEmpty()) Text(msg)
        }
    }
}

suspend fun download(ctx: Context, url: String, audio: Boolean, height: Int, ext: String, onProgress: (Float) -> Unit) {
    App.ready.await().getOrThrow()
    val dir = File(ctx.getExternalFilesDir(null), "dl_${System.currentTimeMillis()}").apply { mkdirs() }
    val req = YoutubeDLRequest(url)
    req.addOption("-o", dir.absolutePath + "/%(title).80s.%(ext)s")
    req.addOption("--no-playlist")
    if (audio) {
        req.addOption("-x")
        req.addOption("--audio-format", ext)
    } else {
        req.addOption("-f", if (height == 0) "bv*+ba/b" else "bv*[height<=$height]+ba/b[height<=$height]/b")
        req.addOption("--merge-output-format", ext)
    }
    YoutubeDL.getInstance().execute(req) { p, _, _ -> onProgress(p.coerceAtLeast(0f)) }
    dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.forEach { saveToDownloads(ctx, it); it.delete() }
    dir.deleteRecursively()
}

fun saveToDownloads(ctx: Context, file: File) {
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, file.name)
        put(MediaStore.Downloads.MIME_TYPE, mime)
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
    ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
}
