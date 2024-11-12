package xyz.acrylicstyle.gptxbot.vector

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import xyz.acrylicstyle.gptxbot.BotConfig
import xyz.acrylicstyle.gptxbot.toJsonElement

class VectorOpenAI(private val database: VectorDatabase) {
    private val client = HttpClient(OkHttp)

    suspend fun embedding(text: String): DoubleArray =
        client.post("https://api.openai.com/v1/embeddings") {
            header("Authorization", "Bearer ${BotConfig.instance.openAIToken}")
            header("Content-Type", "application/json")
            BotConfig.instance.getExtraOpenAIHeaders().forEach { (k, v) -> header(k, v) }
            setBody(Json.encodeToString<Map<String, String>>(mapOf(
                "model" to "text-embedding-ada-002",
                "input" to text,
            )))
        }.bodyAsText().let {
            try {
                Json.parseToJsonElement(it)
                    .jsonObject["data"]!!
                    .jsonArray[0]
                    .jsonObject["embedding"]!!
                    .jsonArray
                    .map { e -> e.jsonPrimitive.double }
                    .toDoubleArray()
            } catch (e: Exception) {
                throw RuntimeException(it, e)
            }
        }

    private suspend fun embeddingBulk(texts: List<String>): List<DoubleArray> =
        client.post("https://api.openai.com/v1/embeddings") {
            header("Authorization", "Bearer ${BotConfig.instance.openAIToken}")
            header("Content-Type", "application/json")
            BotConfig.instance.getExtraOpenAIHeaders().forEach { (k, v) -> header(k, v) }
            setBody(Json.encodeToString(mapOf(
                "model" to "text-embedding-ada-002",
                "input" to texts,
            ).toJsonElement()))
        }.bodyAsText().let {
            try {
                Json.parseToJsonElement(it)
                    .jsonObject["data"]!!
                    .jsonArray
                    .map { e -> e.jsonObject["embedding"]!!.jsonArray.map { e2 -> e2.jsonPrimitive.double }.toDoubleArray() }
            } catch (e: Exception) {
                throw RuntimeException(it, e)
            }
        }

    suspend fun insert(id: String, text: String, metadata: Map<String, String> = emptyMap()) {
        if (database.getVector(id) != null) {
            error("Vector with id $id already exists")
        }
        database.insertVector(Vector(id, embedding(text), text, metadata))
    }

    suspend fun insertBulk(texts: List<RawTextVector>, overwrite: Boolean = false): List<Vector> {
        val list = if (overwrite) {
            texts
        } else {
            texts.filter { database.getVector(it.id) == null }
        }
        if (list.isEmpty()) return emptyList()
        val embeddings = embeddingBulk(list.map { it.text })
        return list.mapIndexed { index, rawTextVector -> Vector(rawTextVector.id, embeddings[index], rawTextVector.text, rawTextVector.metadata) }
            .onEach { database.insertVector(it) }
    }

    suspend fun search(text: String, count: Int = 10, filterMetadata: (metadata: Map<String, String>) -> Boolean = { true }): List<Pair<Vector, Double>> {
        return database.query(embedding(text), count, filterMetadata)
    }
}
