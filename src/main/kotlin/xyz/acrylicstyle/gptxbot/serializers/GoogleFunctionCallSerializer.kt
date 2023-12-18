package xyz.acrylicstyle.gptxbot.serializers

import com.google.cloud.vertexai.api.FunctionCall
import com.google.protobuf.Struct
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

object GoogleFunctionCallSerializer : KSerializer<FunctionCall> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FunctionCall", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): FunctionCall {
        val json = Json.parseToJsonElement(decoder.decodeString())
        val name = json.jsonObject["name"]!!.jsonPrimitive.content
        val args = json.jsonObject["args"]?.jsonArray?.map { it.jsonPrimitive.int.toByte() }?.toByteArray()?.let { Struct.parseFrom(it) }
        return FunctionCall.newBuilder()
            .setName(name)
            .setArgs(args ?: Struct.getDefaultInstance())
            .build()
    }

    override fun serialize(encoder: Encoder, value: FunctionCall) {
        encoder.encodeString(Json.encodeToString(JsonObject(mapOf(
            "name" to JsonPrimitive(value.name),
            "args" to (if (value.hasArgs()) JsonArray(value.args.toByteArray().map { JsonPrimitive(it.toInt()) }) else JsonNull),
        ))))
    }
}
