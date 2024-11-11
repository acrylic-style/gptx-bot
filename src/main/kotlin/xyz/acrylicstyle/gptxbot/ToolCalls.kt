package xyz.acrylicstyle.gptxbot

import com.aallam.openai.api.chat.ChatMessage
import dev.kord.common.entity.Snowflake
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ToolCalls {
    val toolCalls = mutableMapOf<String, List<ChatMessage>>()

    fun addToolCall(index: Int, id: String, message: ChatMessage) {
        val list = toolCalls[id]?.toMutableList() ?: mutableListOf()
        list.add(index, message)
        toolCalls[id] = list
    }

    fun addToolCall(id: String, message: ChatMessage) {
        val list = toolCalls[id]?.toMutableList() ?: mutableListOf()
        list.add(message)
        toolCalls[id] = list
    }

    init {
        // load
        try {
            val text = File("tool_calls.json").readText()
            toolCalls.putAll(Json.decodeFromString<Map<String, List<ChatMessage>>>(text))
        } catch (ignored: Exception) {}
    }

    fun save() {
        try {
            val text = Json.encodeToString<Map<String, List<ChatMessage>>>(toolCalls)
            File("tool_calls.json").writeText(text)
        } catch (ignored: Exception) {}
    }
}
