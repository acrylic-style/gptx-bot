package xyz.acrylicstyle.gptxbot

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.ListContent
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

object Util {
    suspend fun getUserId(id: Snowflake) = BotConfig.instance.getCloudflareKVDiscord().get(id.toString())

    suspend fun getUserData(id: Snowflake) =
        getUserId(id)?.let { BotConfig.instance.getCloudflareKVUsers().get(it)?.let { s -> Json.parseToJsonElement(s) } }

    suspend fun hasValidUserData(id: Snowflake) =
        getUserData(id)?.let {
            if (it.jsonObject["active"]?.jsonPrimitive?.boolean != true) return@let false
            if (it.jsonObject["stripe_customer_id"]?.jsonPrimitive?.contentOrNull?.isNotBlank() != true) return@let false
            true
        } ?: false

    fun createPostEventsFlow(url: String, body: String, headers: Map<String, String> = emptyMap()): Flow<EventData> =
        flow {
            val conn = (URL(url).openConnection() as HttpURLConnection).also {
                headers.forEach { (key, value) -> it.setRequestProperty(key, value) }
                it.setRequestProperty("Accept", "text/event-stream")
                it.doInput = true
                it.doOutput = true
            }

            conn.connect()

            conn.outputStream.write(body.toByteArray())

            if (conn.responseCode !in 200..399) {
                error("Request failed with ${conn.responseCode}: ${conn.errorStream.bufferedReader().readText()}")
            }

            val reader = conn.inputStream.bufferedReader()

            var event = EventData()

            while (true) {
                val line = reader.readLine() ?: break

                when {
                    line.startsWith("event:") -> event = event.copy(name = line.substring(6).trim())
                    line.startsWith("data:") -> event = event.copy(data = line.substring(5).trim())
                    line.isEmpty() -> {
                        emit(event)
                        event = EventData()
                    }
                }
            }
        }.flowOn(Dispatchers.IO)

    suspend fun createChatCompletions(message: Message): Flow<EventData> {
        val messageList = message.toChatMessageList()
        val hasImage = messageList.hasImage()
        val messages = messageList.let { Json.encodeToJsonElement(it) }
        val body = JsonObject(
            if (hasImage) {
                mapOf(
                    "model" to JsonPrimitive("gpt-4-vision-preview"),
                    "messages" to messages,
                    "max_tokens" to JsonPrimitive(4096),
                    "user" to JsonPrimitive(message.author?.id?.toString() ?: "unknown"),
                    "stream" to JsonPrimitive(true),
                )
            } else {
                mapOf(
                    "model" to JsonPrimitive("gpt-4-1106-preview"),
                    "messages" to messages,
                    "user" to JsonPrimitive(message.author?.id?.toString() ?: "unknown"),
                    "stream" to JsonPrimitive(true),
                    "tools" to JsonArray(listOf(
                        JsonObject(mapOf(
                            "type" to JsonPrimitive("function"),
                            "function" to JsonObject(mapOf(
                                "name" to JsonPrimitive("get_100_messages"),
                                "description" to JsonPrimitive("Get 100 messages before the current message"),
                                "parameters" to JsonObject(mapOf(
                                    "type" to JsonPrimitive("object"),
                                    "properties" to JsonObject(emptyMap()),
                                    "required" to JsonArray(emptyList()),
                                )),
                            ))
                        )),
                        JsonObject(mapOf(
                            "type" to JsonPrimitive("function"),
                            "function" to JsonObject(mapOf(
                                "name" to JsonPrimitive("get_100_messages_from_referenced_message"),
                                "description" to JsonPrimitive("Get 100 messages before the referenced message (the message the user replied to)"),
                                "parameters" to JsonObject(mapOf(
                                    "type" to JsonPrimitive("object"),
                                    "properties" to JsonObject(emptyMap()),
                                    "required" to JsonArray(emptyList()),
                                )),
                            ))
                        )),
                    ))
                )
            }
        ).let { Json.encodeToString(it) }
        return createPostEventsFlow(
            "https://api.openai.com/v1/chat/completions",
            body,
            mapOf(
                "Authorization" to "Bearer ${BotConfig.instance.openAIToken}",
                "Content-Type" to "application/json",
            )
        )
    }
}

fun List<ChatMessage>.hasImage() =
    any { it.role == Role.User && it.messageContent is ListContent && (it.messageContent as ListContent).content.any { p -> p is ImagePart } }

suspend fun Message.toChatMessageList(root: Boolean = true): List<ChatMessage> {
    val messages = mutableListOf<ChatMessage>()
    if (root && author != null) {
        Util.getUserData(author!!.id)?.let {
            it.jsonObject["default_instruction"]?.jsonPrimitive?.contentOrNull?.let { instruction ->
                messages += ChatMessage.System(instruction)
            }
        }
    }
    referencedMessage?.toChatMessageList(false)?.let { messages += it }
    ToolCalls.toolCalls[id]?.let { messages += it }
    if (ToolCalls.toolCalls[id] == null) {
        if (author?.id == kord.selfId) {
            messages += ChatMessage.Assistant(content)
        } else {
            messages += ChatMessage.User(content.replace("<@!?${kord.selfId}>".toRegex(), "").trim())
            attachments.forEach { attachment ->
                val filename = attachment.filename.lowercase()
                if (filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".jpeg") ||
                    filename.endsWith(".webp") || filename.endsWith(".gif")
                ) {
                    messages += ChatMessage.User(listOf(ImagePart(attachment.url)))
                }
            }
        }
    }
    return messages
}

suspend fun OpenAI.createCompletion(message: Message) =
    chatCompletions(ChatCompletionRequest(
        ModelId("gpt-4-vision-preview"),
        message.toChatMessageList(),
        maxTokens = 4096,
        user = message.author?.id?.toString(),
    )).map { chunk -> chunk.choices.getOrNull(0)?.delta?.content }

fun String.capAtLength(maxStringLength: Int = 1900, linePrefix: String = ""): String {
    var chars = 0
    val lines = mutableListOf<String>()
    this.lines().reversed().forEach { line ->
        if ((chars + linePrefix.length + line.length) > maxStringLength) {
            return lines.reversed().joinToString("\n")
        }
        chars += linePrefix.length + line.length
        lines.add(linePrefix + line)
    }
    return lines.reversed().joinToString("\n")
}
