package xyz.acrylicstyle.gptxbot.function

import dev.kord.core.entity.Message
import kotlinx.serialization.Serializable

@Serializable
sealed interface Function {
    suspend fun call(originalMessage: Message, addToolCallOutput: (String) -> Unit)
}
