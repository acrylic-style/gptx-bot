package xyz.acrylicstyle.gptxbot

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File

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
    val createThread: Boolean = System.getenv("CREATE_THREAD").toBooleanStrictOrNull() ?: false,
    val githubAccessToken: String = System.getenv("GITHUB_ACCESS_TOKEN") ?: "",
    @YamlComment("Use vector search when browsing the web contents. This feature helps reduce the token usage and reduce the bill, at the cost of cutting the accuracy.")
    val useVectorSearch: Boolean = System.getenv("USE_VECTOR_SEARCH").toBooleanStrictOrNull() ?: false,
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
