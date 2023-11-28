package xyz.acrylicstyle.gptxbot.function

import dev.kord.core.entity.Message
import io.github.furstenheim.CopyDown
import it.skrape.core.htmlDocument
import it.skrape.fetcher.BrowserFetcher
import it.skrape.fetcher.response
import it.skrape.fetcher.skrape
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("get_web_contents_by_url")
data class GetWebContentsByURLFunction(val url: String = ""): Function {
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

            addToolCallOutput("$markdown\n")
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
