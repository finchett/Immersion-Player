package io.github.immersionplayer.mining

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Everything needed to build an Anki card later: the word, the sentence it came from,
 * and where in the video the sentence is (for audio clips and screenshots).
 */
data class MiningCard(
    val expression: String,
    val reading: String,
    val glossary: String,
    val sentence: String,
    val translation: String?,
    val videoUri: String,
    val videoName: String,
    val sentenceStart: Double,
    val sentenceEnd: Double,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("expression", expression)
        .put("reading", reading)
        .put("glossary", glossary)
        .put("sentence", sentence)
        .put("translation", translation ?: JSONObject.NULL)
        .put("videoUri", videoUri)
        .put("videoName", videoName)
        .put("sentenceStart", sentenceStart)
        .put("sentenceEnd", sentenceEnd)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(json: JSONObject) = MiningCard(
            expression = json.getString("expression"),
            reading = json.getString("reading"),
            glossary = json.getString("glossary"),
            sentence = json.getString("sentence"),
            translation = if (json.isNull("translation")) null else json.getString("translation"),
            videoUri = json.getString("videoUri"),
            videoName = json.getString("videoName"),
            sentenceStart = json.getDouble("sentenceStart"),
            sentenceEnd = json.getDouble("sentenceEnd"),
            createdAt = json.getLong("createdAt"),
        )
    }
}

/** Destination for mined cards. AnkiDroid export will be another implementation of this. */
interface CardExporter {
    fun export(card: MiningCard)
}

/** Saves mined cards to a local JSON-lines file so nothing is lost before Anki export exists. */
class MiningStore(context: Context) : CardExporter {
    private val file = File(context.filesDir, "mined_cards.jsonl")

    override fun export(card: MiningCard) {
        synchronized(this) {
            file.appendText(card.toJson().toString() + "\n")
        }
    }

    fun all(): List<MiningCard> =
        synchronized(this) {
            if (!file.exists()) return emptyList()
            file.readLines().filter { it.isNotBlank() }.mapNotNull {
                runCatching { MiningCard.fromJson(JSONObject(it)) }.getOrNull()
            }
        }

    fun contains(expression: String, sentence: String): Boolean =
        all().any { it.expression == expression && it.sentence == sentence }
}
