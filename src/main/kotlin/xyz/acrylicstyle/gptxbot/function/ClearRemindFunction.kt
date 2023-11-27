package xyz.acrylicstyle.gptxbot.function

import dev.kord.core.entity.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("clear_remind")
data object ClearRemindFunction : Function {
    override suspend fun call(originalMessage: Message, addToolCallOutput: (String) -> Unit) {
        SetRemindFunction.reminds.removeIf { it.userId == originalMessage.author?.id }
        SetRemindFunction.saveReminds()
        addToolCallOutput("Successfully cleared reminds of ${originalMessage.author!!.id}")
    }
}
