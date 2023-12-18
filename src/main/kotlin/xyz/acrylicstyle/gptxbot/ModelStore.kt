package xyz.acrylicstyle.gptxbot

import dev.kord.common.entity.Snowflake
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ModelStore {
    val map = mutableMapOf<Snowflake, String>()

    init {
        // load
        try {
            val text = File("model_store.json").readText()
            map.putAll(Json.decodeFromString<Map<Snowflake, String>>(text))
        } catch (ignored: Exception) {}
    }

    fun save() {
        try {
            val text = Json.encodeToString<Map<Snowflake, String>>(map)
            File("model_store.json").writeText(text)
        } catch (ignored: Exception) {}
    }
}
