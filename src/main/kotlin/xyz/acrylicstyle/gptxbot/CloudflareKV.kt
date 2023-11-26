package xyz.acrylicstyle.gptxbot

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

data class CloudflareKV(val accountId: String, val namespaceId: String) {
    companion object {
        val client = HttpClient(CIO)
    }

    suspend fun get(key: String): String? =
        client.get("https://api.cloudflare.com/client/v4/accounts/$accountId/storage/kv/namespaces/$namespaceId/values/${key.encodeURLPath()}") {
            header("Authorization", "Bearer ${BotConfig.instance.cloudflareApiKey}")
        }.let {
            if (it.status == HttpStatusCode.OK) {
                it.bodyAsText()
            } else {
                null
            }
        }

    suspend fun put(key: String, value: String) =
        client.put("https://api.cloudflare.com/client/v4/accounts/$accountId/storage/kv/namespaces/$namespaceId/values/${key.encodeURLPath()}") {
            header("Authorization", "Bearer ${BotConfig.instance.cloudflareApiKey}")
            header("Content-Type", "text/plain")
            setBody(value)
        }
}
