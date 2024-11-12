package xyz.acrylicstyle.gptxbot.commands

import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import xyz.acrylicstyle.gptxbot.Util
import xyz.acrylicstyle.gptxbot.Util.optString
import xyz.acrylicstyle.gptxbot.message.EditableMessage
import java.util.concurrent.atomic.AtomicReference

object AskPrivateCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val input = interaction.optString("input") ?: return
        val reply = interaction.respondEphemeral { content = "考え中..." }
        val currentMessage = AtomicReference("")
        val message = EditableMessage.adapt(reply, interaction.user, input)
        Util.generateOpenAI(currentMessage, message, message)
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("ask", "Ask a question") {
            dmPermission = true
            string("input", "Input string") {
                required = true
            }
        }
    }
}