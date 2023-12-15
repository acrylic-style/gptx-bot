package xyz.acrylicstyle.gptxbot

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.core.Role
import com.google.cloud.aiplatform.v1.Tensor
import com.google.cloud.vertexai.api.Blob
import com.google.cloud.vertexai.api.Content
import com.google.cloud.vertexai.api.Part
import com.google.protobuf.ByteString
import com.google.protobuf.util.JsonFormat
import com.spotify.github.v3.clients.GitHubClient
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TopGuildMessageChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.rest.builder.message.embed
import io.ktor.client.request.forms.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import xyz.acrylicstyle.gptxbot.function.Function
import xyz.acrylicstyle.gptxbot.function.SetRemindFunction
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

object Util {
    private const val YEAR = 1000L * 60L * 60L * 24L * 365L
    private const val MONTH = 1000L * 60L * 60L * 24L * 30L
    private const val DAY = 1000L * 60L * 60L * 24L
    private const val HOUR = 1000L * 60L * 60L
    private const val MINUTE = 1000L * 60L
    private const val SECOND = 1000L

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun trimContent(message: Message): String {
        var trimmed = message.content.replace("<@!?${message.kord.selfId}>".toRegex(), "").trim()
        if (trimmed.isBlank()) return ""
        val firstMatch = "\\|\\|[a-z0-9_\\-]+\\|\\|".toRegex().find(trimmed)
        if (firstMatch != null) {
            trimmed = trimmed.replaceFirst(firstMatch.value, "").trim()
        }
        return trimmed
    }

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
        println(messages)
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
                                "description" to JsonPrimitive("Set a reminder with the given time. Message is optional. DO NOT retry this function. Current time is ${SetRemindFunction.format.format(System.currentTimeMillis())}"),
                                "parameters" to JsonObject(mapOf(
                                    "type" to JsonPrimitive("object"),
                                    "properties" to JsonObject(mapOf(
                                        "time" to JsonObject(mapOf(
                                            "type" to JsonPrimitive("string"),
                                            "description" to JsonPrimitive(
                                                "Time to remind at (in the format of yyyy/MM/dd HH:mm:ss, or XyXmoXdXhXmXs where X is an number, each units can be omitted). If user says 'one year', you must say '1y'. If user says 'nine days' or '9 days', you must say '9d'. Current time is: ${SetRemindFunction.format.format(System.currentTimeMillis())}"
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
                                            "description" to JsonPrimitive("Message to remind with. If an user input for this argument is provided, DO NOT MODIFY THE MESSAGE."),
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
                                        "query" to JsonObject(mapOf(
                                            "type" to JsonPrimitive("string"),
                                            "description" to JsonPrimitive(
                                                "Search query to get data related to the given query (usually this is an user input without url)"
                                            ),
                                        )),
                                    )),
                                    "required" to JsonArray(listOf("url", "query").map { JsonPrimitive(it) }),
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
            ) + BotConfig.instance.getExtraOpenAIHeaders(),
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

    suspend fun generateGoogle(currentMessage: AtomicReference<String>, replyMessage: Message, originalMessage: Message) {
        var lastUpdate = System.currentTimeMillis()
        val contents = originalMessage.toGoogleContentList()
        val model = if (contents.hasImage()) "gemini-pro-vision" else "gemini-pro"
        val parameters = VertexAi.Parameters()
        BotConfig.instance.vertexAi.predict(model, contents, parameters).collect { response ->
            if (response == null) {
                if (currentMessage.get().isNotBlank()) {
                    replyMessage.edit {
                        if (currentMessage.get().length > 2000) {
                            content = ""
                            embed {
                                description = currentMessage.get().capAtLength(4000)
                            }
                        } else {
                            content = currentMessage.get()
                        }
                        if (currentMessage.get().length > 500) {
                            ByteArrayInputStream(currentMessage.get().toByteArray()).use { stream ->
                                addFile("output.md", ChannelProvider { stream.toByteReadChannel() })
                            }
                        }
                    }
                }
                return@collect
            }
            val candidate = response.candidatesList.getOrNull(0)
            if (candidate != null && candidate.hasContent()) {
                println(JsonFormat.printer().print(response))
                val delta = candidate.content.partsList.getOrNull(0)?.text
                if (delta != null) {
                    currentMessage.set(currentMessage.get() + delta)
                }
            }
            if (currentMessage.get().isBlank()) return@collect
            if (System.currentTimeMillis() - lastUpdate < 1000) return@collect
            lastUpdate = System.currentTimeMillis()
            if (currentMessage.get().length in 1..2000) {
                replyMessage.edit { content = currentMessage.get() }
            } else if (currentMessage.get().length > 2000) {
                replyMessage.edit {
                    content = ""
                    embed {
                        description = currentMessage.get().capAtLength(4000)
                    }
                }
            }
        }
    }

    suspend fun generateOpenAI(currentMessage: AtomicReference<String>, replyMessage: Message, originalMessage: Message) {
        var lastUpdate = System.currentTimeMillis()
        val initialToolCallIndex = ToolCalls.toolCalls[replyMessage.id]?.size ?: 0
        val toolCalls = mutableListOf<AssistantToolCallData>()
        createChatCompletions(originalMessage).collect { data ->
            if (data.data == "[DONE]") {
                if (currentMessage.get().isNotBlank()) {
                    replyMessage.edit {
                        if (currentMessage.get().length > 2000) {
                            content = ""
                            embed {
                                description = currentMessage.get().capAtLength(4000)
                            }
                        } else {
                            content = currentMessage.get()
                        }
                        if (currentMessage.get().length > 500) {
                            ByteArrayInputStream(currentMessage.get().toByteArray()).use { stream ->
                                addFile("output.md", ChannelProvider { stream.toByteReadChannel() })
                            }
                        }
                    }
                }
                if (toolCalls.isNotEmpty()) {
                    val chatMessage = ChatMessage.Assistant(toolCalls = toolCalls.map { toolCallData ->
                        ToolCall.Function(
                            ToolId(toolCallData.id),
                            FunctionCall(
                                toolCallData.function?.name,
                                toolCallData.function?.arguments,
                            ),
                        )
                    })
                    println("Adding assistant tool call: " + json.encodeToJsonElement(chatMessage))
                    ToolCalls.addToolCall(replyMessage.id, chatMessage)
                }
                if (ToolCalls.toolCalls[replyMessage.id] != null && currentMessage.get().isBlank()) {
                    toolCalls.forEachIndexed { index, call ->
                        if (call.function?.name?.isNotBlank() == true) {
                            val obj = if (call.function!!.arguments.isNotBlank() && call.function!!.arguments != "{}") {
                                val arguments = json.parseToJsonElement(call.function!!.arguments)
                                JsonObject(arguments.jsonObject + mapOf("type" to JsonPrimitive(call.function!!.name)))
                            } else {
                                JsonObject(mapOf("type" to JsonPrimitive(call.function!!.name)))
                            }
                            val function = json.decodeFromJsonElement<Function>(obj)
                            var added = false
                            function.call(originalMessage) {
                                if (added) error("Already added")
                                added = true
                                ToolCalls.addToolCall(initialToolCallIndex + (index * 2) + 1, replyMessage.id, ChatMessage.Tool(it, ToolId(call.id)))
                            }
                        }
                    }
                    println(ToolCalls.toolCalls[replyMessage.id])
                    ToolCalls.save()
                    generateOpenAI(currentMessage, replyMessage, originalMessage)
                }
                return@collect
            }
            val response = json.decodeFromString<StreamResponse>(data.data)
            response.choices[0].delta.toolCalls.forEach { call ->
                if (call.id != null) {
                    if (toolCalls.size <= call.index) {
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
                currentMessage.set(currentMessage.get() + delta)
            }
            if (currentMessage.get().isBlank()) return@collect
            if (System.currentTimeMillis() - lastUpdate < 1000) return@collect
            lastUpdate = System.currentTimeMillis()
            if (currentMessage.get().length in 1..2000) {
                replyMessage.edit { content = currentMessage.get() }
            } else if (currentMessage.get().length > 2000) {
                replyMessage.edit {
                    content = ""
                    embed {
                        description = currentMessage.get().capAtLength(4000)
                    }
                }
            }
        }
    }
}

@JvmName("List_ChatMessage_hasImage")
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
        Util.getUserData(id)?.jsonObject?.get("default_instruction")?.jsonPrimitive?.contentOrNull?.let { instruction ->
            messages += ChatMessage.System(instruction)
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
            if (content.isNotBlank()) {
                messages += ChatMessage.Assistant(content)
            }
        } else {
            val trimmedContent = Util.trimContent(this)
            if (trimmedContent.isNotBlank()) {
                messages += ChatMessage.User(trimmedContent)
            }
            attachments.mapNotNull { attachment ->
                val filename = attachment.filename.lowercase()
                if (filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".jpeg") ||
                    filename.endsWith(".webp") || filename.endsWith(".gif")
                ) {
                    ImagePart(attachment.url)
                } else {
                    null
                }
            }.let {
                if (it.isNotEmpty()) {
                    messages += ChatMessage.User(it)
                }
            }
        }
    }
    return messages
}

@JvmName("List_Content_hasImage")
fun List<Content>.hasImage() =
    any { it.partsList.any { p -> p.hasInlineData() } }

suspend fun Message.toGoogleContentList(root: Boolean = true): List<Content> {
    fun createTextPart(text: String) = Part.newBuilder().setText(text).build()
    fun createImagePart(url: String, filename: String): Part {
        val mimeType = when {
            filename.endsWith(".png") -> "image/png"
            filename.endsWith(".jpg") || filename.endsWith(".jpeg") -> "image/jpeg"
            filename.endsWith(".webp") -> "image/webp"
            filename.endsWith(".gif") -> "image/gif"
            else -> "image/png"
        }
        return Part.newBuilder().setInlineData(Blob.newBuilder().setData(ByteString.readFrom(URL(url).openStream())).setMimeType(mimeType)).build()
    }
    fun createContent(role: String, parts: List<Part>) = Content.newBuilder().addAllParts(parts).setRole(role).build()

    val thread: ThreadChannel? = null//getChannelOrNull() as? ThreadChannel
    val starterMessage = (thread?.getParentOrNull() as? TopGuildMessageChannel)?.getMessage(thread.id)
    val messages = mutableListOf<Content>()
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
        Util.getUserData(id)?.jsonObject?.get("default_instruction")?.jsonPrimitive?.contentOrNull?.let { instruction ->
            // TODO: system role is not allowed?
            //messages += createContent("system", listOf(createTextPart(instruction)))
        }
        if (thread != null && thread.ownerId == kord.selfId) {
            starterMessage?.let { messages += it.toGoogleContentList(false) }
            thread.getMessagesBefore(Snowflake.max, 50)
                .collect { messages += it.toGoogleContentList(false) }
        }
    }
    if (thread == null) {
        referencedMessage?.toGoogleContentList(false)?.let { messages += it }
    }
    //ToolCalls.toolCalls[id]?.let { messages += it }
    if (ToolCalls.toolCalls[id] == null) {
        if (author?.id == kord.selfId) {
            if (content.isNotBlank()) {
                messages += createContent("model", listOf(createTextPart(content)))
            }
        } else {
            val parts = mutableListOf<Part>()
            val trimmedContent = Util.trimContent(this)
            if (trimmedContent.isNotBlank()) {
                parts += createTextPart(trimmedContent)
            }
            attachments.forEach { attachment ->
                val filename = attachment.filename.lowercase()
                if (filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                    parts += createImagePart(attachment.url, filename)
                }
            }
            messages += createContent("user", parts)
        }
    }
    return messages
}

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

fun JsonElement.toTensor(): Tensor = when (this) {
    is JsonPrimitive -> {
        this.doubleOrNull?.let { Tensor.newBuilder().addDoubleVal(it).build() } ?:
        this.intOrNull?.let { Tensor.newBuilder().addIntVal(it).build() } ?:
        this.longOrNull?.let { Tensor.newBuilder().addInt64Val(it).build() } ?:
        this.booleanOrNull?.let { Tensor.newBuilder().addBoolVal(it).build() } ?:
        Tensor.newBuilder().addStringVal(this.content).build()
    }
    is JsonObject -> {
        val tensor = Tensor.newBuilder()
        forEach { key, value -> tensor.putStructVal(key, value.toTensor()) }
        tensor.build()
    }
    is JsonArray -> Tensor.newBuilder().addAllListVal(map { it.toTensor() }).build()
    else -> throw IllegalArgumentException("Unsupported type: ${this::class.simpleName}")
}
