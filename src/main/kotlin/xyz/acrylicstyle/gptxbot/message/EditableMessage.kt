package xyz.acrylicstyle.gptxbot.message

import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.PublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.User
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.io.ByteArrayInputStream

interface EditableMessage {
    val message: Any
    val id: String
    val author: User?
    val kord: Kord
    val originalContent: String

    suspend fun edit(content: String)

    suspend fun addFile(name: String, content: ByteArray)

    companion object {
        fun adapt(message: Message) =
            object : EditableMessage {
                override val message: Any = message
                override val id: String = message.id.toString()
                override val author: User? = message.author
                override val kord: Kord = message.kord
                override val originalContent: String = message.content

                override suspend fun edit(content: String) {
                    message.edit { this.content = content }
                }

                override suspend fun addFile(name: String, content: ByteArray) {
                    message.edit {
                        ByteArrayInputStream(content).use { stream ->
                            addFile(name, ChannelProvider { stream.toByteReadChannel() })
                        }
                    }
                }
            }

        fun adapt(message: PublicMessageInteractionResponseBehavior, author: User, content: String) =
            object : EditableMessage {
                override val message: Any = message
                override val id: String = message.token.toString()
                override val author: User? = author
                override val kord: Kord = message.kord
                override val originalContent: String = content

                override suspend fun edit(content: String) {
                    message.edit { this.content = content }
                }

                override suspend fun addFile(name: String, content: ByteArray) {
                    message.edit {
                        ByteArrayInputStream(content).use { stream ->
                            addFile(name, ChannelProvider { stream.toByteReadChannel() })
                        }
                    }
                }
            }
    }
}
