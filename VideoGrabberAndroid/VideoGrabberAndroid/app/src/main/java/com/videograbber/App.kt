package com.videograbber

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // first launch extracts python + yt-dlp, so do it off the main thread
        ready = bg.async {
            runCatching {
                YoutubeDL.getInstance().init(this@App)
                FFmpeg.getInstance().init(this@App)
            }
        }
    }

    companion object {
        // lives as long as the process, so a download continues if the popup is closed
        val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        lateinit var ready: Deferred<Result<Unit>>
    }
}
