package io.github.immersionplayer.player

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVLib.MpvFormat

/**
 * SurfaceView that owns the (process-wide) libmpv instance.
 * Based on mpv-android's BaseMPVView/MPVView.
 */
class MpvView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    interface Listener {
        fun onTimePos(seconds: Double) {}
        fun onDuration(seconds: Double) {}
        fun onPause(paused: Boolean) {}
        fun onFileLoaded() {}
        fun onEndReached() {}
    }

    var listener: Listener? = null
    private var pendingFile: String? = null
    private var initialized = false

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {}

        override fun eventProperty(property: String, value: Long) {}

        override fun eventProperty(property: String, value: Boolean) {
            when (property) {
                "pause" -> listener?.onPause(value)
                "eof-reached" -> if (value) listener?.onEndReached()
            }
        }

        override fun eventProperty(property: String, value: String) {}

        override fun eventProperty(property: String, value: Double) {
            when (property) {
                "time-pos" -> listener?.onTimePos(value)
                "duration" -> listener?.onDuration(value)
            }
        }

        override fun event(eventId: Int) {
            if (eventId == MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED) post { listener?.onFileLoaded() }
        }
    }

    fun initialize(startPosition: Double, fill: Boolean) {
        // libmpv is a per-process singleton shared with the thumbnail generator
        ThumbnailGenerator.requestAbort()
        MpvOwner.acquire(OWNER)
        MPVLib.create(context.applicationContext)

        MPVLib.setOptionString("config", "no")
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("hwdec", "mediacodec,mediacodec-copy")
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("audio-set-media-role", "yes")
        MPVLib.setOptionString("input-default-bindings", "no")
        MPVLib.setOptionString("demuxer-max-bytes", "${64 * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${64 * 1024 * 1024}")
        MPVLib.setOptionString("gpu-shader-cache-dir", context.cacheDir.path)
        MPVLib.setOptionString("icc-cache-dir", context.cacheDir.path)

        // Japanese audio; subtitles are never drawn on the video (the app shows them beside it)
        MPVLib.setOptionString("alang", "jpn,ja,jp")
        MPVLib.setOptionString("sid", "no")
        MPVLib.setOptionString("osd-level", "0")
        MPVLib.setOptionString("hr-seek", "yes")
        MPVLib.setOptionString("keep-open", "yes")
        if (startPosition > 1) MPVLib.setOptionString("start", startPosition.toString())
        MPVLib.setOptionString("panscan", if (fill) "1.0" else "0.0")

        MPVLib.init()

        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("idle", "once")

        holder.addCallback(this)
        MPVLib.addObserver(observer)
        MPVLib.observeProperty("time-pos", MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MpvFormat.MPV_FORMAT_FLAG)
        initialized = true
    }

    fun playFile(path: String) {
        pendingFile = path
    }

    fun destroy() {
        if (!initialized) return
        initialized = false
        MPVLib.removeObserver(observer)
        holder.removeCallback(this)
        MPVLib.destroy()
        MpvOwner.release(OWNER)
    }

    var paused: Boolean
        get() = MPVLib.getPropertyBoolean("pause") ?: true
        set(value) = MPVLib.setPropertyBoolean("pause", value)

    /** Crop to fill the view (1.0) or fit the whole picture (0.0). */
    fun setFill(fill: Boolean) {
        MPVLib.setPropertyDouble("panscan", if (fill) 1.0 else 0.0)
    }

    /** Current frame, for the library's thumbnails (mpv decodes formats Android can't). */
    fun grabFrame(size: Int): android.graphics.Bitmap? =
        runCatching { MPVLib.grabThumbnail(size) }.getOrNull()

    fun seek(seconds: Double) {
        MPVLib.command(arrayOf("seek", seconds.coerceAtLeast(0.0).toString(), "absolute+exact"))
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.v(TAG, "attaching surface")
        MPVLib.attachSurface(holder.surface)
        MPVLib.setOptionString("force-window", "yes")
        val file = pendingFile
        if (file != null) {
            MPVLib.command(arrayOf("loadfile", file))
            pendingFile = null
        } else {
            MPVLib.setPropertyString("vo", "gpu")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.v(TAG, "detaching surface")
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        MPVLib.detachSurface()
    }

    companion object {
        private const val TAG = "ImmersionMpv"
        private const val OWNER = "player"
    }
}
