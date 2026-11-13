package com.videograbber

import android.app.Application
import android.content.Context
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
        ready = bg.async {
            runCatching {
                YoutubeDL.getInstance().init(this@App)
                FFmpeg.getInstance().init(this@App)
            }.also { r ->
                if (r.isSuccess) {
                    val prefs = getSharedPreferences("vg", Context.MODE_PRIVATE)
                    val last = prefs.getLong("last_update", 0L)
                    if (System.currentTimeMillis() - last > 6 * 3600_000L) {
                        runCatching {
                            YoutubeDL.getInstance().updateYoutubeDL(this@App, YoutubeDL.UpdateChannel.NIGHTLY)
                        }.onSuccess { prefs.edit().putLong("last_update", System.currentTimeMillis()).apply() }
                    }
                }
            }
        }
    }

    companion object {
        val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        lateinit var ready: Deferred<Result<Unit>>
    }
}
