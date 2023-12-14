package xyz.acrylicstyle.gptxbot

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlComment
import com.google.api.gax.rpc.ResponseObserver
import com.google.api.gax.rpc.StreamController
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.aiplatform.v1.*
import com.google.cloud.vertexai.Transport
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.api.Content
import com.google.cloud.vertexai.generativeai.preview.GenerativeModel
import com.google.protobuf.util.JsonFormat
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CountDownLatch

private val logger = LoggerFactory.getLogger(BotConfig::class.java)!!

@Serializable
data class BotConfig(
    val token: String = System.getenv("TOKEN") ?: "BOT_TOKEN_HERE",
    val openAIToken: String = System.getenv("OPENAI_TOKEN") ?: "sk-xxx",
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
}

@Serializable
data class VertexAi(
    val project: String = System.getenv("VERTEX_AI_PROJECT") ?: "example-project",
    val location: String = System.getenv("VERTEX_AI_REGION") ?: "us-central1",
    val publisher: String = System.getenv("VERTEX_AI_PUBLISHER") ?: "google",
) {
    fun predictStreaming(model: String, instance: JsonObject, parameters: Parameters = Parameters()) = channelFlow {
        val endpointName = EndpointName.ofProjectLocationPublisherModelName(project, location, publisher, model)!!
        val endpoint = "$location-aiplatform.googleapis.com:443"
        val settings = PredictionServiceSettings.newBuilder().setEndpoint(endpoint).build()

        PredictionServiceClient.create(settings).use { client ->
            val instanceTensor = instance.toTensor()
            val parametersTensor = Json.encodeToJsonElement(parameters).toTensor()
            val latch = CountDownLatch(1)
            val stream = client.streamingPredictCallable().splitCall(object : ResponseObserver<StreamingPredictResponse> {
                override fun onStart(controller: StreamController?) {
                    // no-op
                }

                override fun onError(throwable: Throwable?) {
                    logger.error("Error while predicting", throwable)
                    runBlocking { this@channelFlow.close(throwable) }
                    latch.countDown()
                }

                override fun onComplete() {
                    runBlocking { send(null) }
                    latch.countDown()
                }

                override fun onResponse(value: StreamingPredictResponse?) {
                    runBlocking {
                        send(value)
                    }
                }
            })
            stream.send(StreamingPredictRequest.newBuilder().setEndpoint(endpointName.toString()).setParameters(parametersTensor).addInputs(instanceTensor).build())
            latch.await()
        }
    }

    suspend fun predict(model: String, contents: List<Content>, parameters: Parameters = Parameters()) = flow {
        VertexAI(project, location, Transport.REST, GoogleCredentials.getApplicationDefault()).use { client ->
            val generativeModel = GenerativeModel(model, client)
            val stream = generativeModel.generateContentStream(contents)
            stream.forEach { response ->
                emit(response)
            }
            emit(null)
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
