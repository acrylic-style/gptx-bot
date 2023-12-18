package xyz.acrylicstyle.gptxbot.struct

import com.google.cloud.vertexai.api.Content
import com.google.cloud.vertexai.api.FunctionCall
import com.google.cloud.vertexai.api.FunctionResponse
import com.google.cloud.vertexai.api.Part
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Struct
import com.google.protobuf.util.JsonFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import xyz.acrylicstyle.gptxbot.parseToStruct

@Serializable
sealed interface GooglePart {
    fun toGoogle(): Part
}

@Serializable
data class GooglePartFunctionCall(val functionCall: GoogleFunctionCall) : GooglePart {
    override fun toGoogle() = Part.newBuilder().setFunctionCall(functionCall.toGoogle()).build()!!
}

@Serializable
data class GoogleFunctionCall(val name: String, val args: JsonElement) {
    companion object {
        fun fromGoogle(call: FunctionCall) =
            GoogleFunctionCall(
                call.name,
                if (call.hasArgs()) Json.parseToJsonElement(JsonFormat.printer().print(call.args)) else JsonNull,
            )
    }

    fun toGoogle() =
        FunctionCall.newBuilder()
            .setName(name)
            .setArgs(if (args !is JsonNull) Json.encodeToString(args).parseToStruct() else Struct.getDefaultInstance())
            .build()!!
}

@Serializable
data class GooglePartFunctionResponse(val functionResponse: GoogleFunctionResponse) : GooglePart {
    override fun toGoogle() = Part.newBuilder().setFunctionResponse(functionResponse.toGoogle()).build()!!
}

@Serializable
data class GoogleFunctionResponse(val name: String, val response: GoogleFunctionResponseResponse) {
    fun toGoogle() =
        FunctionResponse.newBuilder()
            .setName(name)
            .setResponse(Json.encodeToString(JsonObject(mapOf("name" to JsonPrimitive(response.name), "content" to response.content))).parseToStruct())!!
}

@Serializable
data class GoogleFunctionResponseResponse(val name: String, val content: JsonElement)
