package xyz.acrylicstyle.gptxbot.function

import dev.kord.core.entity.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("delete_remind")
data class DeleteRemindFunction(val index: Int) : Function {
    override suspend fun call(originalMessage: Message, addToolCallOutput: (String) -> Unit) {
        if (index < 1) {
            return addToolCallOutput("Index must be greater than 0.")
        }
        val remind = SetRemindFunction.reminds.filter { it.userId == originalMessage.author?.id }.getOrNull(index - 1)
            ?: return addToolCallOutput("No remind found.")
        SetRemindFunction.reminds.remove(remind)
        SetRemindFunction.saveReminds()
        val date = SetRemindFunction.format.format(remind.at)
        val every = if (remind.every != null) " (every ${remind.every / 1000} seconds)" else ""
        val message = if (remind.message != null) " (message: ${remind.message})" else ""
        addToolCallOutput("Deleted remind #${index + 1}: $date$every$message")
    }
}
