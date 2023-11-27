package xyz.acrylicstyle.gptxbot.function

import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ToolId
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.acrylicstyle.gptxbot.ToolCalls

@Serializable
@SerialName("get_messages")
data class GetMessagesFunction(val count: Int = 100, val ref: Boolean = false) : Function {
    override suspend fun call(originalMessage: Message, toolCallMessageId: Snowflake, toolCallId: String) {
        if (count !in 100..1000) {
            ToolCalls.addToolCall(toolCallMessageId, ChatMessage.Tool("Count must be between 100 and 1000.", ToolId(toolCallId)))
            return
        }
        val list = mutableListOf<String>()
        val message = if (ref) originalMessage.referencedMessage else originalMessage
        if (message == null) {
            ToolCalls.addToolCall(toolCallMessageId, ChatMessage.Tool("No message found.", ToolId(toolCallId)))
            return
        }
        message.getChannel().getMessagesBefore(message.id, count)
            .map {
                val bot = if (it.author?.isBot == true) " (Bot)" else ""
                "${it.author?.username ?: "Unknown"}$bot: ${it.content}"
            }
            .collect { list += it }
        ToolCalls.addToolCall(toolCallMessageId, ChatMessage.Tool(list.reversed().joinToString("\n"), ToolId(toolCallId)))
    }
}
