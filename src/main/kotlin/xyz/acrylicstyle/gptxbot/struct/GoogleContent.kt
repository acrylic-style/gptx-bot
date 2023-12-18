package xyz.acrylicstyle.gptxbot.struct

import com.google.cloud.vertexai.api.Content
import kotlinx.serialization.Serializable

@Serializable
data class GoogleContent(val role: String, val parts: MutableList<GooglePart>) {
    fun toGoogle() =
        Content.newBuilder()
            .setRole(role)
            .addAllParts(parts.map { it.toGoogle() })
            .build()!!
}
