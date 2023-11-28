package xyz.acrylicstyle.gptxbot.vector

import kotlinx.serialization.Serializable

@Serializable
data class RawTextVector(
    val id: String,
    val text: String,
    val metadata: Map<String, String> = emptyMap()
)
