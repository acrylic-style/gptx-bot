package xyz.acrylicstyle.gptxbot.function

import dev.kord.core.entity.Message
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("get_messages")
data class GetMessagesFunction(val count: Int = 100, val ref: Boolean = false) : Function {
    override suspend fun call(originalMessage: Message, addToolCallOutput: (String) -> Unit) {
        if (count !in 100..1000) {
            addToolCallOutput("Count must be between 100 and 1000.")
            return
        }
        val list = mutableListOf<String>()
        val message = if (ref) originalMessage.referencedMessage else originalMessage
        if (message == null) {
            addToolCallOutput("No message found.")
            return
        }
        message.getChannel().getMessagesBefore(message.id, count)
            .map {
                val bot = if (it.author?.isBot == true) " (Bot)" else ""
                "${it.author?.username ?: "Unknown"}$bot: ${it.content}"
            }
            .collect { list += it }
        addToolCallOutput(list.reversed().joinToString("\n"))
    }
}
