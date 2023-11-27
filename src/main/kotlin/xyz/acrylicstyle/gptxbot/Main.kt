@file:JvmName("MainKt")
package xyz.acrylicstyle.gptxbot

import com.aallam.openai.api.chat.ChatMessage
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
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
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import xyz.acrylicstyle.gptxbot.function.Function
import xyz.acrylicstyle.gptxbot.function.SetRemindFunction
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
        SetRemindFunction.load(client)
        logger.info("Logged in as ${client.getSelf().username}")
    }

    client.on<MessageCreateEvent> {
        if (message.author?.isBot != false) return@on
        if (message.content.isBlank()) return@on
        if (!message.mentionedUserIds.contains(client.selfId) && message.referencedMessage?.author?.id != client.selfId) {
            val thread = message.getChannelOrNull() as? ThreadChannel ?: return@on
            if (thread.ownerId != client.selfId) return@on
        }
//        if (!Util.hasValidUserData(message.author!!.id)) {
//            message.reply {
//                content = "Discordアカウントに関連付けられた有効な請求先アカウントがありません。"
//            }
//            return@on
//        }
        val msg = if (BotConfig.instance.createThread && message.getChannel() !is ThreadChannel && message.getChannel() is TextChannel) {
            (message.getChannel() as TextChannel)
                .startPublicThreadWithMessage(message.id, message.content.replace("<@!?${kord.selfId}>".toRegex(), "").trim().take(50))
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
        var currentMessage = ""
        var lastUpdate = System.currentTimeMillis()
        val toolCalls = mutableListOf<AssistantToolCallData>()
        suspend fun generate(message: Message) {
            Util.createChatCompletions(message).collect { data ->
                if (data.data == "[DONE]") {
                    if (currentMessage.isNotBlank()) {
                        msg.edit {
                            if (currentMessage.length > 2000) {
                                content = ""
                                embed {
                                    description = currentMessage.capAtLength(4000)
                                }
                            } else {
                                content = currentMessage
                            }
                            if (currentMessage.length > 500) {
                                ByteArrayInputStream(currentMessage.toByteArray()).use { stream ->
                                    addFile("output.md", ChannelProvider { stream.toByteReadChannel() })
                                }
                            }
                        }
                    }
                    if (ToolCalls.toolCalls[msg.id] != null && currentMessage.isBlank()) {
                        toolCalls.forEach { call ->
                            if (call.function?.name?.isNotBlank() == true) {
                                val obj = if (call.function!!.arguments.isNotBlank() && call.function!!.arguments != "{}") {
                                    val arguments = json.parseToJsonElement(call.function!!.arguments)
                                    JsonObject(arguments.jsonObject + mapOf("type" to JsonPrimitive(call.function!!.name)))
                                } else {
                                    JsonObject(mapOf("type" to JsonPrimitive(call.function!!.name)))
                                }
                                val function = json.decodeFromJsonElement<Function>(obj)
                                function.call(message, msg.id, call.id)
                            }
                        }
                        ToolCalls.save()
                        generate(msg)
                    }
                    return@collect
                }
                val response = json.decodeFromString<StreamResponse>(data.data)
                response.choices[0].delta.toolCalls.forEach { call ->
                    if (call.id != null) {
                        if (toolCalls.size <= call.index) {
                            ToolCalls.addToolCall(
                                msg.id,
                                json.decodeFromJsonElement<ChatMessage>(json.encodeToJsonElement(response.choices[0].delta))
                            )
                            toolCalls.add(AssistantToolCallData(call.id))
                        } else {
                            toolCalls[call.index] = AssistantToolCallData(call.id)
                        }
                    }
                    if (call.function.name != null) {
                        toolCalls[call.index].getAndSetFunction().name = call.function.name
                    }
                    if (call.function.arguments != null) {
                        toolCalls[call.index].getAndSetFunction().arguments += call.function.arguments
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
