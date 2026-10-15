package dev.kinoko.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import dev.kinoko.app.data.Read
import dev.kinoko.app.data.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Replies read aloud in the computer's voice (kinoko/docs/phone-voice.md): each sentence is asked of
 * the hub (POST /v1/speech) when the one before has arrived, and its audio, raw 16-bit mono PCM as it
 * is made, goes into one AudioTrack for the whole reply. The backend makes speech several times faster
 * than it is heard, so the next sentence is there before it is needed. stop() is silence at once.
 */
class Voice(private val context: Context, private val route: () -> Route?, private val failed: (String) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    // Music on the phone gets quieter while kinoko talks, and comes back.
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()

    /** The reply being read: its sentences, its request in flight, its track. */
    private inner class Reply {
        val items = Channel<Read>(Channel.UNLIMITED)
        @Volatile var call: Call? = null
        @Volatile var track: AudioTrack? = null
        /** Frames written to the track: the reply is heard once the track has played them all. */
        var frames = 0L
        lateinit var job: Job
    }

    /** The reply being written, if any. */
    @Volatile private var reply: Reply? = null
    /** Every reply not yet heard to its end, in order: one plays after another, never over it. */
    private val playing = mutableListOf<Reply>()
    /** A failure is said once per reply, not once per sentence. */
    @Volatile private var told = false

    @Synchronized fun read(item: Read) {
        if (item is Read.Hush) return stop()
        val r = reply ?: begin()
        r.items.trySend(item)
        if (item is Read.End) reply = null // the next reply gets a track of its own
    }

    /** Silence, now: the request in flight cancelled, what the track holds dropped. */
    @Synchronized fun stop() {
        reply = null
        for (r in playing.toList()) {
            r.items.close()
            r.call?.cancel()
            r.track?.let { runCatching { it.pause(); it.flush() } }
            r.job.cancel()
        }
    }

    private fun begin(): Reply {
        val r = Reply()
        told = false
        val before = playing.lastOrNull()?.job
        r.job = scope.launch {
            try {
                before?.join()
                for (item in r.items) when (item) {
                    is Read.Say -> speak(r, item.sentence)
                    Read.End -> {
                        heard(r)
                        break
                    }
                    Read.Hush -> Unit
                }
            } finally {
                r.track?.let { runCatching { it.release() } }
                r.track = null
                synchronized(this@Voice) {
                    playing.remove(r)
                    if (playing.isEmpty()) audio.abandonAudioFocusRequest(focus)
                }
            }
        }
        reply = r
        playing.add(r)
        return r
    }

    private fun speak(reply: Reply, sentence: String) {
        val r = route() ?: return fail("not connected to the computer")
        val body = JSONObject().put("text", sentence).toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("${r.url}/v1/speech").header("Authorization", "Bearer ${r.token}").post(body).build()
        val c = r.client.newCall(request)
        reply.call = c
        try {
            c.execute().use { res ->
                if (!res.isSuccessful) return fail(runCatching { JSONObject(res.body?.string().orEmpty()).optString("error") }.getOrNull()?.ifBlank { null } ?: "the computer said ${res.code}")
                val rate = Regex("""rate=(\d+)""").find(res.header("content-type").orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 24_000
                val out = reply.track ?: open(rate).also { reply.track = it }
                val input = res.body?.byteStream() ?: return
                val buf = ByteArray(8_192)
                var held = 0 // a sample split between two reads waits for its other half
                while (true) {
                    val n = input.read(buf, held, buf.size - held)
                    if (n < 0) break
                    val whole = (held + n) and 1.inv()
                    // Paused (stopped): a short count; the reply is over.
                    val wrote = if (whole > 0) out.write(buf, 0, whole) else 0
                    if (wrote < whole) return
                    reply.frames += whole / 2
                    held = held + n - whole
                    if (held == 1) buf[0] = buf[whole]
                }
            }
        } catch (e: java.io.IOException) {
            if (!c.isCanceled()) fail(e.message ?: "the computer stopped answering")
        } finally {
            reply.call = null
        }
    }

    private fun open(rate: Int): AudioTrack {
        audio.requestAudioFocus(focus)
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, rate)) // half a second
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        t.play()
        return t
    }

    /** The reply is over: what was written is heard to its end (a few seconds more at most). */
    private suspend fun heard(reply: Reply) {
        val t = reply.track ?: return
        val rate = t.sampleRate.coerceAtLeast(1)
        val deadline = System.currentTimeMillis() + 3_000 + reply.frames * 1_000 / rate
        while (t.playbackHeadPosition.toLong() and 0xFFFFFFFFL < reply.frames && System.currentTimeMillis() < deadline) delay(50)
    }

    private fun fail(why: String) {
        if (told) return
        told = true
        failed("Voice: $why")
    }
}
