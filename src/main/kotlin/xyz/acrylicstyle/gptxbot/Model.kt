package xyz.acrylicstyle.gptxbot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
internal data class StreamResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<StreamResponseChoice>,
)

@Serializable
internal data class StreamResponseChoice(
    val delta: StreamResponseChoiceDelta,
    val index: Int,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
internal data class StreamResponseChoiceDelta(
    val content: String? = "",
    val role: String = "assistant",
    @SerialName("tool_calls")
    val toolCalls: List<StreamResponseChoiceDeltaToolCall> = emptyList(),
) {
    fun isToolCallEmpty(): Boolean {
        return toolCalls.isEmpty() || toolCalls.all { it.isEmpty() }
    }
}

@Serializable
data class StreamResponseChoiceDeltaToolCall(
    val index: Int = 0,
    val id: String? = null,
    val function: StreamResponseChoiceDeltaToolCallFunction = StreamResponseChoiceDeltaToolCallFunction(),
    val type: String = "",
) {
    fun isEmpty(): Boolean {
        return id.isNullOrEmpty() && function.name.isNullOrEmpty() && type.isEmpty()
    }
}

@Serializable
data class StreamResponseChoiceDeltaToolCallFunction(
    val name: String? = null,
    val arguments: String? = null,
)
