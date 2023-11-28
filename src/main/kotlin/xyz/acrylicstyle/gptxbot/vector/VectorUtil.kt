package xyz.acrylicstyle.gptxbot.vector

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object VectorUtil {
    fun cosineSimilarity(vectorA: DoubleArray, vectorB: DoubleArray): Double {
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i].pow(2.0)
            normB += vectorB[i].pow(2.0)
        }
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }

    fun splitText(text: String, chunkSize: Int, chunkOverlap: Int): List<String> {
        if (chunkSize <= 0) throw IllegalArgumentException("chunkSize must be positive")
        if (chunkOverlap < 0) throw IllegalArgumentException("chunkOverlap must be non-negative")
        if (chunkSize <= chunkOverlap) throw IllegalArgumentException("chunkSize must be greater than chunkOverlap")
        val list = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val endIndex = min(text.length, i + chunkSize)
            list.add(text.substring(i, endIndex))
            i += chunkSize - chunkOverlap
        }
        return list
    }
}
