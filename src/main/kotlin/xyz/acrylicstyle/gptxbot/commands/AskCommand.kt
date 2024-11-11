package xyz.acrylicstyle.gptxbot.commands

import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import xyz.acrylicstyle.gptxbot.Util
import xyz.acrylicstyle.gptxbot.Util.optString
import xyz.acrylicstyle.gptxbot.message.EditableMessage
import java.util.concurrent.atomic.AtomicReference

object AskCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val input = interaction.optString("input") ?: return
        val reply = interaction.respondPublic { content = "考え中..." }
        val currentMessage = AtomicReference("")
        val message = EditableMessage.adapt(reply, interaction.user, input)
        Util.generateOpenAI(currentMessage, message, message)
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("ask", "Ask a question") {
            dmPermission = true
            string("input", "Input") {
                required = true
            }
        }
        builder.user("Ask a question with GPT-4o")
        builder.message("Ask a question with GPT-4o")
    }
}
