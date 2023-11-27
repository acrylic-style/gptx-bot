package xyz.acrylicstyle.gptxbot.function

import com.spotify.github.v3.clients.RepositoryClient
import dev.kord.core.entity.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.acrylicstyle.gptxbot.githubClient
import java.util.*

@Serializable
@SerialName("get_github_repository_document")
data class GetGitHubRepositoryDocumentFunction(val url: String = ""): Function {
    override suspend fun call(originalMessage: Message, addToolCallOutput: (String) -> Unit) {
        println("called get_github_repository_document")

        if (url.isEmpty()) {
            addToolCallOutput("URL must not be empty.")
            return
        }

        //  validate url. it is https://github.com/owner/repo
        val splitUrl = url.split("/")
        if (splitUrl.size != 5 || splitUrl[3].isEmpty() || splitUrl[4].isEmpty()) {
            addToolCallOutput("Invalid URL.")
            return
        }

        //  get owner and repo name
        val owner = splitUrl[3]
        val repo = splitUrl[4]

        //  create repository client
        if (githubClient == null) {
            addToolCallOutput("This function cannot be used because it has not been configured.")
            return
        }

        println("create repository client")
        val repositoryClient = githubClient!!.createRepositoryClient(owner, repo)

        println("searching for markdown files")
        //  get .md and .mdx files
        val files = getMarkdownFiles(repositoryClient, "")

        println("outputting")
        addToolCallOutput(files.joinToString("\n"))
    }
}

private fun getMarkdownFiles(repositoryClient: RepositoryClient, path: String): MutableList<String> {
    val result = mutableListOf<String>()

    //  file contents
    val list = repositoryClient.getFolderContent(path).get()

    for (content in list) {
        if (content.type() == "dir") {
            result.addAll(getMarkdownFiles(repositoryClient, content.path() ?: continue))
        }
        if (content.type() == "file" && content.name() != null && (content.name()!!.endsWith(".md") || content.name()!!.endsWith(".mdx"))) {
            try {
                val markdown = repositoryClient.getFileContent(content.path() ?: continue).get()
                val decoder = Base64.getDecoder()
                val markdownContent = "Title: ${content.name()}\nContent:\n${decoder.decode(markdown.content())}\n"
                result.add(markdownContent)

                println("added $markdownContent")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    return result
}
