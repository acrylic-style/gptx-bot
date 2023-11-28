package xyz.acrylicstyle.gptxbot.function

import dev.kord.core.entity.Message
import io.github.furstenheim.CopyDown
import it.skrape.core.htmlDocument
import it.skrape.fetcher.BrowserFetcher
import it.skrape.fetcher.response
import it.skrape.fetcher.skrape
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.acrylicstyle.gptxbot.BotConfig
import xyz.acrylicstyle.gptxbot.vector.MapVectorDatabase
import xyz.acrylicstyle.gptxbot.vector.RawTextVector
import xyz.acrylicstyle.gptxbot.vector.VectorUtil
import java.util.*

@Serializable
@SerialName("get_web_contents_by_url")
data class GetWebContentsByURLFunction(val url: String, val query: String): Function {
    override suspend fun call(originalMessage: Message, addToolCallOutput: (String) -> Unit) {
        if (url.isEmpty()) {
            addToolCallOutput("URL must not be empty.")
            return
        }

        try {
            //  fetch html by url
            val htmlDocument = getDocumentByUrl(url).html
            //  convert html to markdown
            val markdown = CopyDown().convert(htmlDocument)

            if (BotConfig.instance.useVectorSearch) {
                val database = MapVectorDatabase()

                val texts = VectorUtil.splitText(markdown, 800, 150)
                val id = UUID.randomUUID().toString()
                val rawVectors = texts.mapIndexed { index, it -> RawTextVector("$id-$index", it) }

                database.openAI.insertBulk(rawVectors)

                val out = database.openAI.search(query, 10).joinToString("\n\n") { it.first.text }

                println("Query result of $query:")
                println(out)

                addToolCallOutput(out)
            } else {
                addToolCallOutput(markdown)
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
            addToolCallOutput("Failed to fetch site contents.")
        }
    }
}

private fun getDocumentByUrl(urlToScrape: String) = skrape(BrowserFetcher) { // <--- pass BrowserFetcher to include rendered JS
    request { url = urlToScrape }
    response { htmlDocument { this } }
}
