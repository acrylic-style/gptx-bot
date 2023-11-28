package xyz.acrylicstyle.gptxbot

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.ListContent
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.spotify.github.v3.clients.GitHubClient
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import xyz.acrylicstyle.gptxbot.function.SetRemindFunction
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object Util {
    private const val YEAR = 1000L * 60L * 60L * 24L * 365L
    private const val MONTH = 1000L * 60L * 60L * 24L * 30L
    private const val DAY = 1000L * 60L * 60L * 24L
    private const val HOUR = 1000L * 60L * 60L
    private const val MINUTE = 1000L * 60L
    private const val SECOND = 1000L

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
                                "name" to JsonPrimitive("get_messages"),
                                "description" to JsonPrimitive("Get 100 messages before the current message"),
                                "parameters" to JsonObject(mapOf(
                                    "type" to JsonPrimitive("object"),
                                    "properties" to JsonObject(mapOf(
                                        "count" to JsonObject(mapOf(
                                            "type" to JsonPrimitive("number"),
                                            "description" to JsonPrimitive(
                                                "Number of messages to get (range: 100 - 1000) (default: 100)"
                                            ),
                                        )),
                                        "ref" to JsonObject(mapOf(
                                            "type" to JsonPrimitive("boolean"),
                                            "description" to JsonPrimitive(
                                                "Whether to get messages from the referenced message (default: false)"
                                            ),
                                        )),
                                    )),
                                    "required" to JsonArray(emptyList()),
                                )),
                            ))
                        )),
                        JsonObject(mapOf(
                            "type" to JsonPrimitive("function"),
                            "function" to JsonObject(mapOf(
                                "name" to JsonPrimitive("set_remind"),
                                "description" to JsonPrimitive("Set a reminder with the given time. Message is optional."),
                                "parameters" to JsonObject(mapOf(
                                    "type" to JsonPrimitive("object"),
                                    "properties" to JsonObject(mapOf(
                                        "time" to JsonObject(mapOf(
                                            "type" to JsonPrimitive("string"),
                                            "description" to JsonPrimitive(
                                                "Time to remind at (in the format of yyyy/MM/dd HH:mm:ss, or XyXmoXdXhXmXs where X is an number, each units can be omitted). If user says 'one year', you must say '1y'. Current time is: ${SetRemindFunction.format.format(System.currentTimeMillis())}"
                                            ),
                                        )),
                                        "period" to JsonObject(mapOf(
                                            "type" to JsonPrimitive("string"),
                                            "description" to JsonPrimitive(
                                                "Period to remind with (in the format of XyXmoXdXhXmXs where X is an number, each units can be omitted) (optional; if not specified, it will be a one-time reminder)"
                                            ),
                                        )),
                                        "message" to JsonObject(mapOf(
                                            "type" to JsonPrimitive("string"),
                                            "description" to JsonPrimitive("Message to remind with"),
                                        )),
                                    )),
                                    "required" to JsonArray(listOf("time").map { JsonPrimitive(it) }),
                                )),
                            ))
                        )),
                        JsonObject(mapOf(
                            "type" to JsonPrimitive("function"),
                            "function" to JsonObject(mapOf(
                                "name" to JsonPrimitive("list_remind"),
                                "description" to JsonPrimitive("List all reminds"),
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
                                "name" to JsonPrimitive("delete_remind"),
                                "description" to JsonPrimitive("Delete a remind with the given index"),
                                "parameters" to JsonObject(mapOf(
                                    "type" to JsonPrimitive("object"),
                                    "properties" to JsonObject(mapOf(
                                        "index" to JsonObject(mapOf(
                                            "type" to JsonPrimitive("string"),
                                            "description" to JsonPrimitive("Index of the remind to delete (starts from 1)"),
                                        )),
                                    )),
                                    "required" to JsonArray(listOf("index").map { JsonPrimitive(it) }),
                                )),
                            ))
                        )),
                        JsonObject(mapOf(
                            "type" to JsonPrimitive("function"),
                            "function" to JsonObject(mapOf(
                                "name" to JsonPrimitive("clear_remind"),
                                "description" to JsonPrimitive("Clear all reminds (requires confirmation)"),
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
                                "name" to JsonPrimitive("get_github_repository_document"),
                                "description" to JsonPrimitive("Get readme file from the given GitHub repository url"),
                                "parameters" to JsonObject(mapOf(
                                    "type" to JsonPrimitive("object"),
                                    "properties" to JsonObject(mapOf(
                                        "url" to JsonObject(mapOf(
                                            "type" to JsonPrimitive("string"),
                                            "description" to JsonPrimitive(
                                                "GitHub repository url (starts with https://github.com/...)"
                                            ),
                                        )),
                                    )),
                                    "required" to JsonArray(listOf("url").map { JsonPrimitive(it) }),
                                )),
                            ))
                        )),
                        JsonObject(mapOf(
                            "type" to JsonPrimitive("function"),
                            "function" to JsonObject(mapOf(
                                "name" to JsonPrimitive("get_web_contents_by_url"),
                                "description" to JsonPrimitive("Get web contents from the given url"),
                                "parameters" to JsonObject(mapOf(
                                    "type" to JsonPrimitive("object"),
                                    "properties" to JsonObject(mapOf(
                                        "url" to JsonObject(mapOf(
                                            "type" to JsonPrimitive("string"),
                                            "description" to JsonPrimitive(
                                                "URL to get web contents from (starts with https://)"
                                            ),
                                        )),
                                    )),
                                    "required" to JsonArray(listOf("url").map { JsonPrimitive(it) }),
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

    fun processTime(s: String): Long {
        var time = 0L
        var rawNumber = ""
        val reader = StringReader(s)
        while (!reader.isEOF()) {
            val c = reader.read(1).first()
            if (c.isDigit() || c == '.') {
                rawNumber += c
            } else {
                if (rawNumber.isEmpty()) {
                    throw IllegalArgumentException("Unexpected non-digit character: '$c' at index ${reader.index}")
                }
                // mo
                if (c == 'm' && !reader.isEOF() && reader.peek() == 'o') {
                    reader.skip(1)
                    time += MONTH * rawNumber.toLong()
                    rawNumber = ""
                    continue
                }
                // y(ear), d(ay), h(our), m(inute), s(econd)
                time += when (c) {
                    'y' -> (YEAR * rawNumber.toDouble()).toLong()
                    // mo is not here
                    'w' -> (7 * DAY * rawNumber.toDouble()).toLong()
                    'd' -> (DAY * rawNumber.toDouble()).toLong()
                    'h' -> (HOUR * rawNumber.toDouble()).toLong()
                    'm' -> (MINUTE * rawNumber.toDouble()).toLong()
                    's' -> (SECOND * rawNumber.toDouble()).toLong()
                    else -> throw IllegalArgumentException("Unexpected character: '$c' at index ${reader.index}")
                }
                rawNumber = ""
            }
        }
        return time
    }
}

fun List<ChatMessage>.hasImage() =
    any { it.role == Role.User && it.messageContent is ListContent && (it.messageContent as ListContent).content.any { p -> p is ImagePart } }

suspend fun Message.toChatMessageList(root: Boolean = true): List<ChatMessage> {
    val thread = getChannelOrNull() as? ThreadChannel
    val starterMessage = (thread?.getParentOrNull() as? TopGuildMessageChannel)?.getMessage(thread.id)
    val messages = mutableListOf<ChatMessage>()
    if (root && author != null) {
        val id = if (author!!.id == kord.selfId) {
            if (referencedMessage?.author?.isBot == false) {
                referencedMessage!!.author!!.id
            } else if (thread != null && thread.ownerId == kord.selfId && starterMessage != null && starterMessage.author != null) {
                starterMessage.author!!.id
            } else {
                author!!.id
            }
        } else {
            author!!.id
        }
        Util.getUserData(id)?.let {
            it.jsonObject["default_instruction"]?.jsonPrimitive?.contentOrNull?.let { instruction ->
                messages += ChatMessage.System(instruction)
            }
        }
        if (thread != null && thread.ownerId == kord.selfId) {
            starterMessage?.let { messages += it.toChatMessageList(false) }
            thread.getMessagesBefore(Snowflake.max, 50)
                .collect { messages += it.toChatMessageList(false) }
        }
    }
    if (thread == null) {
        referencedMessage?.toChatMessageList(false)?.let { messages += it }
    }
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

/**
 * GitHub client for fetching documentation
 */
val githubClient: GitHubClient? by lazy {
    if (BotConfig.instance.githubAccessToken.isEmpty()) {
        return@lazy null
    }

    //  create github client
    GitHubClient.create(URI.create("https://api.github.com/"), BotConfig.instance.githubAccessToken)
}
