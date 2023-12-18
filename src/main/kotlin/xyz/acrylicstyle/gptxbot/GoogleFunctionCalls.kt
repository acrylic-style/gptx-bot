package xyz.acrylicstyle.gptxbot

import com.google.cloud.vertexai.api.FunctionCall
import dev.kord.common.entity.Snowflake
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import xyz.acrylicstyle.gptxbot.serializers.GoogleFunctionCallSerializer
import xyz.acrylicstyle.gptxbot.struct.*
import java.io.File

object GoogleFunctionCalls {
    val json = Json {
        serializersModule = SerializersModule {
            contextual(FunctionCall::class, GoogleFunctionCallSerializer)
        }
    }

    val functionCalls = mutableMapOf<Snowflake, List<GoogleContent>>()

    fun addFunctionCall(id: Snowflake, index: Int, message: FunctionCall) {
        val list = functionCalls[id]?.toMutableList() ?: mutableListOf()
        list.add(index, GoogleContent("model", mutableListOf(GooglePartFunctionCall(GoogleFunctionCall.fromGoogle(message)))))
        functionCalls[id] = list
    }

    fun addFunctionCall(id: Snowflake, message: FunctionCall) {
        val list = functionCalls[id]?.toMutableList() ?: mutableListOf()
        list.add(GoogleContent("model", mutableListOf(GooglePartFunctionCall(GoogleFunctionCall.fromGoogle(message)))))
        functionCalls[id] = list
    }

    fun addFunctionResponse(id: Snowflake, name: String, content: JsonElement) {
        val list = functionCalls[id]?.toMutableList() ?: mutableListOf()
        val part = GooglePartFunctionResponse(GoogleFunctionResponse(name, GoogleFunctionResponseResponse(name, content)))
        list.lastOrNull()?.let { googleContent ->
            if (googleContent.role == "function") {
                googleContent.parts += part
                part
            } else {
                null
            }
        } ?: list.add(GoogleContent("function", mutableListOf(part)))
        functionCalls[id] = list
    }

    init {
        // load
        try {
            val text = File("google_function_calls.json").readText()
            functionCalls.putAll(Json.decodeFromString<Map<Snowflake, List<GoogleContent>>>(text))
        } catch (ignored: Exception) {}
    }

    fun save() {
        try {
            val text = Json.encodeToString<Map<Snowflake, List<GoogleContent>>>(functionCalls)
            File("google_function_calls.json").writeText(text)
        } catch (ignored: Exception) {}
    }
}
