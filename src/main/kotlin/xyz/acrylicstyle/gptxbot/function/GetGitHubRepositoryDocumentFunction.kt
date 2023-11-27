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

        //  get repository client
        val repositoryClient = githubClient!!.createRepositoryClient(owner, repo)

        try {
            //  get .md and .mdx files
            val readme = getReadmeFile(repositoryClient)

            if (readme.isEmpty()) {
                addToolCallOutput("README.md not found.")
                return
            }

            addToolCallOutput(readme)
        } catch (e: Exception) {
            addToolCallOutput("Repository not found or no access permission.")
        }
    }
}

private fun getReadmeFile(repositoryClient: RepositoryClient): String {
    //  file contents
    val list = repositoryClient.getFolderContent("").get()

    val readmeFile = list.find { it.name().equals("README.md", ignoreCase = true) || it.name().equals("README.mdx", ignoreCase = true) } ?: return ""
    //  get file content
    val markdown = repositoryClient.getFileContent(readmeFile.path()).get() ?: return ""
    //  decode base64
    val decoder = Base64.getDecoder()

    return String(decoder.decode(markdown.content()!!.replace("\n", "")))
}
