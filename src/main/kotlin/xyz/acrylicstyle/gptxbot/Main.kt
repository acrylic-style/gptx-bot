@file:JvmName("MainKt")
package xyz.acrylicstyle.gptxbot

import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ToolId
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.NON_PRIVILEGED
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.embed
import io.ktor.client.request.forms.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File

private val logger = LoggerFactory.getLogger("GPTxBot")

@OptIn(PrivilegedIntent::class)
suspend fun main() {
    BotConfig.loadConfig(File("."))

    val client = Kord(BotConfig.instance.token)
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    client.on<ReadyEvent> {
        logger.info("Logged in as ${client.getSelf().username}")
    }

    client.on<MessageCreateEvent> {
        if (message.author?.isBot != false) return@on
        if (!message.mentionedUserIds.contains(client.selfId) && message.referencedMessage?.author?.id != client.selfId) return@on
        if (!Util.hasValidUserData(message.author!!.id)) {
            message.reply {
                content = "Discordアカウントに関連付けられた有効な請求先アカウントがありません。"
            }
            return@on
        }
        val msg = message.reply { content = "Thinking..." }
        var currentMessage = ""
        var lastUpdate = System.currentTimeMillis()
        suspend fun generate(message: Message) {
            Util.createChatCompletions(message).collect { data ->
                if (data.data == "[DONE]") {
                    if (currentMessage.length > 500) {
                        msg.edit {
                            content = currentMessage
                            ByteArrayInputStream(currentMessage.toByteArray()).use { stream ->
                                addFile("output.md", ChannelProvider { stream.toByteReadChannel() })
                            }
                        }
                    }
                    if (ToolCalls.toolCalls[msg.id] != null && currentMessage.isBlank()) {
                        generate(msg)
                    }
                    return@collect
                }
                val response = json.decodeFromString<StreamResponse>(data.data)
                if (!response.choices[0].delta.isToolCallEmpty()) {
                    ToolCalls.addToolCall(
                        msg.id,
                        json.decodeFromJsonElement<ChatMessage>(json.encodeToJsonElement(response.choices[0].delta))
                    )
                }
                response.choices[0].delta.toolCalls.forEach { call ->
                    if (call.id != null && call.function.name == "get_100_messages") {
                        val list = mutableListOf<String>()
                        message.getChannel().getMessagesBefore(message.id, 100)
                            .map {
                                val bot = if (it.author?.isBot == true) " (Bot)" else ""
                                "${it.author?.username ?: "Unknown"}$bot: ${it.content}"
                            }
                            .collect { list += it }
                        ToolCalls.addToolCall(msg.id, ChatMessage.Tool(list.reversed().joinToString("\n"), ToolId(call.id)))
                        ToolCalls.save()
                    } else if (call.id != null && call.function.name == "get_100_messages_from_referenced_message") {
                        val refMsg = message.referencedMessage
                        if (refMsg == null) {
                            ToolCalls.addToolCall(
                                msg.id,
                                ChatMessage.Tool("No referenced message found", ToolId(call.id))
                            )
                        } else {
                            val list = mutableListOf<String>()
                            //list += "${refMsg.author?.username ?: "Unknown"}${if (refMsg.author?.isBot == true) " (Bot)" else ""}: ${refMsg.content}"
                            message.getChannel().getMessagesBefore(refMsg.id, 100)
                                .map {
                                    val bot = if (it.author?.isBot == true) " (Bot)" else ""
                                    "${it.author?.username ?: "Unknown"}$bot: ${it.content}"
                                }
                                .collect { list += it }
                            ToolCalls.addToolCall(
                                msg.id,
                                ChatMessage.Tool(list.reversed().joinToString("\n"), ToolId(call.id))
                            )
                        }
                        ToolCalls.save()
                    }
                }
                val delta = response.choices[0].delta.content
                if (delta != null) {
                    currentMessage += delta
                }
                if (currentMessage.isBlank()) return@collect
                if (System.currentTimeMillis() - lastUpdate < 1000) return@collect
                lastUpdate = System.currentTimeMillis()
                if (currentMessage.length in 1..2000) {
                    msg.edit { content = currentMessage }
                } else if (currentMessage.length > 2000) {
                    msg.edit {
                        content = ""
                        embed {
                            description = currentMessage.capAtLength(4000)
                        }
                    }
                }
            }
        }
        generate(message)
    }

    client.login {
        intents {
            +Intents.NON_PRIVILEGED
            +Intent.MessageContent
        }
    }
}
