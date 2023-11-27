package xyz.acrylicstyle.gptxbot.function

import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ToolId
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.acrylicstyle.gptxbot.ToolCalls

@Serializable
@SerialName("clear_remind")
data object ClearRemindFunction : Function {
    override suspend fun call(originalMessage: Message, toolCallMessageId: Snowflake, toolCallId: String) {
        SetRemindFunction.reminds.removeIf { it.userId == originalMessage.author?.id }
        SetRemindFunction.saveReminds()
        ToolCalls.addToolCall(toolCallMessageId, ChatMessage.Tool("Successfully cleared reminds of ${originalMessage.author!!.id}", ToolId(toolCallId)))
    }
}
