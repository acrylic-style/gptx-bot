@file:JvmName("MainKt")
package xyz.acrylicstyle.gptxbot

import dev.kord.core.Kord
import dev.kord.core.behavior.reply
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.ApplicationCommandCreateEvent
import dev.kord.core.event.interaction.ApplicationCommandInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.NON_PRIVILEGED
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.interaction.string
import org.slf4j.LoggerFactory
import xyz.acrylicstyle.gptxbot.commands.AskCommand
import xyz.acrylicstyle.gptxbot.function.SetRemindFunction
import xyz.acrylicstyle.gptxbot.message.EditableMessage
import java.io.File
import java.util.concurrent.atomic.AtomicReference

private val logger = LoggerFactory.getLogger("GPTxBot")

@OptIn(PrivilegedIntent::class)
suspend fun main() {
    BotConfig.loadConfig(File("."))

    val client = Kord(BotConfig.instance.token)

    val commands = mapOf(
        "ask" to AskCommand,
    )

    client.on<ReadyEvent> {
        SetRemindFunction.load(client)
        logger.info("Logged in as ${client.getSelf().username}")
    }

    client.createGlobalApplicationCommands {
        commands.forEach { (_, command) ->
            command.register(this)
        }
    }

    client.on<ApplicationCommandInteractionCreateEvent> {
        if (interaction.user.isBot) return@on
        val command = commands[interaction.invokedCommandName] ?: return@on
        command.handle(interaction)
    }

    client.on<MessageCreateEvent> {
        if (message.author?.isBot != false) return@on
        if (message.content.isBlank()) return@on
        if (!message.mentionedUserIds.contains(client.selfId) && message.referencedMessage?.author?.id != client.selfId) {
//            val thread = message.getChannelOrNull() as? ThreadChannel ?: return@on
//            if (thread.ownerId != client.selfId) return@on
            return@on
        }
//        if (!Util.hasValidUserData(message.author!!.id)) {
//            message.reply {
//                content = "Discordアカウントに関連付けられた有効な請求先アカウントがありません。"
//            }
//            return@on
//        }
        var trimmed = message.content.replace("<@!?${kord.selfId}>".toRegex(), "").trim()
        if (trimmed.isBlank()) return@on
        // extract pattern like ||google|| from the message
        val firstMatch = "\\|\\|[a-z0-9_\\-]+\\|\\|".toRegex().find(trimmed)
        val type = if (firstMatch != null) {
            val model = firstMatch.value.substring(2, firstMatch.value.length - 2)
            trimmed = trimmed.replaceFirst(firstMatch.value, "").trim()
            model
        } else {
            ModelStore.map[message.referencedMessage?.id]
        }
        val msg = if (BotConfig.instance.createThread && message.getChannel() !is ThreadChannel && message.getChannel() is TextChannel) {
            (message.getChannel() as TextChannel)
                .startPublicThreadWithMessage(message.id, trimmed.take(50))
                .createMessage("Thinking...")
        } else if (message.getChannel() is ThreadChannel) {
            val thread = message.getChannel() as ThreadChannel
            if (thread.ownerId != client.selfId) {
                message.reply { content = "Thinking..." }
            } else {
                thread.createMessage("Thinking...")
            }
        } else {
            message.reply { content = "Thinking..." }
        }
        if (type != null) {
            ModelStore.map[message.id] = type
            ModelStore.save()
        }
        val currentMessage = AtomicReference("")
        when (type) {
            "google" -> Util.generateGoogle(true, currentMessage, msg, message)
            "google-nostream" -> Util.generateGoogle(false, currentMessage, msg, message)
            else -> Util.generateOpenAI(currentMessage, EditableMessage.adapt(msg), EditableMessage.adapt(message))
        }
    }

    client.login {
        intents {
            +Intents.NON_PRIVILEGED
            +Intent.MessageContent
        }
    }
}
