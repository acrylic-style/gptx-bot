package xyz.acrylicstyle.gptxbot.function

import dev.kord.core.entity.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("get_time")
data class GetTimeFunction(val time: String, val period: String? = null, val message: String? = null) : Function {
    override suspend fun call(originalMessage: Message, addToolCallOutput: (String) -> Unit) {
        addToolCallOutput("Current time is: ${SetRemindFunction.format.format(System.currentTimeMillis())})")
    }
}
