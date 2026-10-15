package dev.kinoko.app.data

import org.json.JSONObject

/** What the voice does with an event of the reply being read aloud. */
sealed interface Read {
    data class Say(val sentence: String) : Read
    /** The reply is over: what was asked for is played to its end. */
    data object End : Read
    /** Silence, now: a new run, or the run cut short. */
    data object Hush : Read
}

/** One agent's live events, as the voice reads them: the same choices as the voice extension. */
class Reading {
    private val sentences = Sentences()

    fun apply(event: JSONObject): List<Read> = when (event.optString("type")) {
        "run_start" -> {
            sentences.reset()
            listOf(Read.Hush)
        }
        "message_delta" -> sentences.push(event.optString("delta")).map(Read::Say)
        "message_end" -> sentences.flush().map(Read::Say) + Read.End
        "run_end" -> if (event.optString("reason") == "aborted") {
            sentences.reset()
            listOf(Read.Hush)
        } else emptyList()
        else -> emptyList()
    }

    /** Stopped from outside (a message sent, the chat left): what was half read is dropped. */
    fun reset() = sentences.reset()
}
