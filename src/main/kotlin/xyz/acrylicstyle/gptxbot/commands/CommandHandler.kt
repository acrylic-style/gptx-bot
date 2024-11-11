package xyz.acrylicstyle.gptxbot.commands

import dev.kord.core.behavior.channel.GuildChannelBehavior
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder

interface CommandHandler {
    suspend fun handle(interaction: ApplicationCommandInteraction) {
        if (canProcess(interaction)) {
            handle0(interaction)
        }
    }

    suspend fun canProcess(interaction: ApplicationCommandInteraction): Boolean

    suspend fun handle0(interaction: ApplicationCommandInteraction)

    fun register(builder: GlobalMultiApplicationCommandBuilder)
}

suspend fun MessageChannelBehavior.getGuildOrNull() = if (this is GuildChannelBehavior) this.getGuildOrNull() else null
