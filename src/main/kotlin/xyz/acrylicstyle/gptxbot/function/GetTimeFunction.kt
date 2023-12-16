package xyz.acrylicstyle.gptxbot.function

import dev.kord.core.entity.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("get_time")
data object GetTimeFunction : Function {
    override suspend fun call(originalMessage: Message, addToolCallOutput: (String) -> Unit) {
        addToolCallOutput("Current time is: ${SetRemindFunction.format.format(System.currentTimeMillis())})")
    }
}
