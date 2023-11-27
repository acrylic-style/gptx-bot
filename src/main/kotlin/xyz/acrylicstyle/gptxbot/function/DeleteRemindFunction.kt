package xyz.acrylicstyle.gptxbot.function

import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ToolId
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.acrylicstyle.gptxbot.ToolCalls

@Serializable
@SerialName("delete_remind")
data class DeleteRemindFunction(val index: Int) : Function {
    override suspend fun call(originalMessage: Message, toolCallMessageId: Snowflake, toolCallId: String) {
        if (index < 1) {
            ToolCalls.addToolCall(toolCallMessageId, ChatMessage.Tool("Index must be greater than 0.", ToolId(toolCallId)))
            return
        }
        val remind = SetRemindFunction.reminds.filter { it.userId == originalMessage.author?.id }.getOrNull(index - 1)
        if (remind == null) {
            ToolCalls.addToolCall(toolCallMessageId, ChatMessage.Tool("No remind found.", ToolId(toolCallId)))
            return
        }
        SetRemindFunction.reminds.remove(remind)
        SetRemindFunction.saveReminds()
        val date = SetRemindFunction.format.format(remind.at)
        val every = if (remind.every != null) " (every ${remind.every / 1000} seconds)" else ""
        val message = if (remind.message != null) " (message: ${remind.message})" else ""
        ToolCalls.addToolCall(toolCallMessageId, ChatMessage.Tool("Deleted remind #${index + 1}: $date$every$message", ToolId(toolCallId)))
    }
}
