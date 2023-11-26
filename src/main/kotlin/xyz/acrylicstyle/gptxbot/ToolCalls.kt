package xyz.acrylicstyle.gptxbot

import com.aallam.openai.api.chat.ChatMessage
import dev.kord.common.entity.Snowflake
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ToolCalls {
    val toolCalls = mutableMapOf<Snowflake, List<ChatMessage>>()

    fun addToolCall(id: Snowflake, message: ChatMessage) {
        toolCalls[id] = toolCalls[id]?.let { it + message } ?: listOf(message)
    }

    init {
        // load
        try {
            val text = File("tool_calls.json").readText()
            toolCalls.putAll(Json.decodeFromString<Map<Snowflake, List<ChatMessage>>>(text))
        } catch (ignored: Exception) {}
    }

    fun save() {
        try {
            val text = Json.encodeToString<Map<Snowflake, List<ChatMessage>>>(toolCalls)
            File("tool_calls.json").writeText(text)
        } catch (ignored: Exception) {}
    }
}
