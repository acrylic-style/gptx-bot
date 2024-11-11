package xyz.acrylicstyle.gptxbot.commands

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.actionRow

object AskCommand : CommandHandler {
    override suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean = true

    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        interaction.modal("Ask a question", "question") {
            actionRow {
                textInput(TextInputStyle.Paragraph, "input", "Input") {
                    required = true
                    placeholder = "Type your input here"
                }
            }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("ask", "Ask a question") {
            dmPermission = true
            string("query", "The question you want to ask")
        }
        builder.user("ask") {
            dmPermission = true
        }
    }
}