package xyz.acrylicstyle.gptxbot.function

import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ToolId
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.acrylicstyle.gptxbot.ToolCalls

@Serializable
@SerialName("list_remind")
data object ListRemindFunction : Function {
    override suspend fun call(originalMessage: Message, toolCallMessageId: Snowflake, toolCallId: String) {
        val list = mutableListOf<String>()
        SetRemindFunction.reminds.forEach {
            val index = SetRemindFunction.reminds.filter { r -> r.userId == it.userId }.indexOf(it)
            val date = SetRemindFunction.format.format(it.at)
            val every = if (it.every != null) " (every ${it.every / 1000} seconds)" else ""
            val message = if (it.message != null) " (message: ${it.message})" else ""
            list += "#${index + 1}: $date$every$message"
        }
        ToolCalls.addToolCall(toolCallMessageId, ChatMessage.Tool(list.joinToString("\n"), ToolId(toolCallId)))
    }
}
