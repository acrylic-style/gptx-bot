package xyz.acrylicstyle.gptxbot

import com.aallam.openai.api.chat.ChatMessage
import dev.kord.common.entity.Snowflake
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ToolCalls {
    val toolCalls = mutableMapOf<Snowflake, List<ChatMessage>>()

    fun addToolCall(index: Int, id: Snowflake, message: ChatMessage) {
        val list = toolCalls[id]?.toMutableList() ?: mutableListOf()
        list.add(index, message)
        toolCalls[id] = list
    }

    fun addToolCall(id: Snowflake, message: ChatMessage) {
        val list = toolCalls[id]?.toMutableList() ?: mutableListOf()
        list.add(message)
        toolCalls[id] = list
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
