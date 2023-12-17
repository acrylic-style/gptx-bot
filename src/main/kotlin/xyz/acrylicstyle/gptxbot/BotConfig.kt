package xyz.acrylicstyle.gptxbot

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlComment
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.vertexai.Transport
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.api.Content
import com.google.cloud.vertexai.api.GenerateContentResponse
import com.google.cloud.vertexai.generativeai.preview.GenerativeModel
import com.google.protobuf.util.JsonFormat
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger(BotConfig::class.java)!!

@Serializable
data class BotConfig(
    val token: String = System.getenv("TOKEN") ?: "BOT_TOKEN_HERE",
    val openAIToken: String = System.getenv("OPENAI_TOKEN") ?: "sk-xxx",
    val openAIOrganization: String? = System.getenv("OPENAI_ORGANIZATION") ?: null,
    val assistantId: String = System.getenv("ASSISTANT_ID") ?: "asst_xxx",
    val cloudflareApiKey: String = System.getenv("CLOUDFLARE_API_KEY") ?: "",
    val cloudflareAccountId: String = System.getenv("CLOUDFLARE_ACCOUNT_ID") ?: "",
    val cloudflareKvUsersId: String = System.getenv("CLOUDFLARE_KV_USERS_ID") ?: "",
    val cloudflareKvDiscordId: String = System.getenv("CLOUDFLARE_KV_DISCORD_ID") ?: "",
    val createThread: Boolean = System.getenv("CREATE_THREAD")?.toBooleanStrictOrNull() ?: false,
    val githubAccessToken: String = System.getenv("GITHUB_ACCESS_TOKEN") ?: "",
    @YamlComment("Use vector search when browsing the web contents. This feature helps reduce the token usage and reduce the bill, at the cost of cutting the accuracy.")
    val useVectorSearch: Boolean = System.getenv("USE_VECTOR_SEARCH")?.toBooleanStrictOrNull() ?: false,
    @YamlComment("Docker host URL (e.g. tcp://localhost:2376 or unix:///var/run/docker.sock). If left empty, code interpreter will be disabled.")
    val dockerHost: String = System.getenv("DOCKER_HOST") ?: "",
    val vertexAi: VertexAi = VertexAi(),
) {
    companion object {
        lateinit var instance: BotConfig

        fun loadConfig(dataFolder: File) {
            val configFile = File(dataFolder, "config.yml")
            logger.info("Loading config from $configFile (absolute path: ${configFile.absolutePath})")
            if (!configFile.exists()) {
                logger.info("Config file not found. Creating new one.")
                configFile.writeText(Yaml.default.encodeToString(serializer(), BotConfig()) + "\n")
            }
            instance = Yaml.default.decodeFromStream(serializer(), configFile.inputStream())
            logger.info("Saving config to $configFile (absolute path: ${configFile.absolutePath})")
            configFile.writeText(Yaml.default.encodeToString(serializer(), instance) + "\n")
        }
    }

    fun getCloudflareKVUsers() = CloudflareKV(cloudflareAccountId, cloudflareKvUsersId)
    fun getCloudflareKVDiscord() = CloudflareKV(cloudflareAccountId, cloudflareKvDiscordId)

    fun getExtraOpenAIHeaders(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (openAIOrganization != null) map["OpenAI-Organization"] = openAIOrganization
        return map
    }
}

@Serializable
data class VertexAi(
    val project: String = System.getenv("VERTEX_AI_PROJECT") ?: "example-project",
    val location: String = System.getenv("VERTEX_AI_REGION") ?: "us-central1",
    val publisher: String = System.getenv("VERTEX_AI_PUBLISHER") ?: "google",
) {
    suspend fun predictStreaming(model: String, contents: List<Content>, parameters: Parameters = Parameters()) = flow {
        VertexAI(project, location, Transport.REST, GoogleCredentials.getApplicationDefault()).use { client ->
            val generativeModel = GenerativeModel(model, client)
            val stream = generativeModel.generateContentStream(contents)
            stream.forEach { response ->
                emit(response)
            }
            emit(null)
        }
    }

    fun predict(model: String, contents: List<Content>, parameters: Parameters = Parameters()): GenerateContentResponse {
        VertexAI(project, location, Transport.REST, GoogleCredentials.getApplicationDefault()).use { client ->
            val generativeModel = GenerativeModel(model, client)
            return generativeModel.generateContent(contents)
        }
    }

    private fun stringToValue(str: String): com.google.protobuf.Value {
        val value = com.google.protobuf.Value.newBuilder()
        JsonFormat.parser().merge(str, value)
        return value.build()
    }

    @Serializable
    data class Parameters(
        val temperature: Double = 0.5,
        val maxOutputTokens: Int = 2048,
    )
}
