package xyz.acrylicstyle.gptxbot.function

import dev.kord.core.entity.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("list_remind")
data object ListRemindFunction : Function {
    override suspend fun call(originalMessage: Message, addToolCallOutput: (String) -> Unit) {
        val list = mutableListOf<String>()
        SetRemindFunction.reminds.filter { it.userId == originalMessage.author?.id }.forEach {
            val index = SetRemindFunction.reminds.filter { r -> r.userId == it.userId }.indexOf(it)
            val date = SetRemindFunction.format.format(it.at)
            val every = if (it.every != null) " (every ${it.every / 1000} seconds)" else ""
            val message = if (it.message != null) " (message: ${it.message})" else ""
            list += "#${index + 1}: $date$every$message"
        }
        addToolCallOutput(list.joinToString("\n"))
    }
}
