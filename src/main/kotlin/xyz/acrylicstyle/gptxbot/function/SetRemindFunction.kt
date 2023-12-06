package xyz.acrylicstyle.gptxbot.function

import dev.kord.common.entity.AllowedMentionType
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.allowedMentions
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.acrylicstyle.gptxbot.Util
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

@Serializable
@SerialName("set_remind")
data class SetRemindFunction(val time: String, val period: String? = null, val message: String? = null) : Function {
    companion object {
        val format = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
            .apply { timeZone = TimeZone.getTimeZone("Asia/Tokyo") }
        val reminds = mutableListOf<RemindData>()

        fun load(kord: Kord) {
            // load reminds
            try {
                val text = File("reminds.json").readText()
                reminds.addAll(Json.decodeFromString<List<RemindData>>(text))
            } catch (ignored: Exception) {
            }

            Timer(true).scheduleAtFixedRate(1000, 1000) {
                ArrayList(reminds).forEach { remindData ->
                    if (System.currentTimeMillis() < remindData.at) return@forEach
                    val loc = remindData.messageLocation
                    reminds.remove(remindData)
                    if (remindData.every != null) {
                        reminds.add(remindData.copy(at = remindData.at + remindData.every))
                    }
                    saveReminds()
                    kord.launch {
                        kord.rest.channel.createMessage(loc.channelId) {
                            content = "<@${remindData.userId}>"
                            messageReference = loc.messageId
                            allowedMentions {
                                repliedUser = true
                                add(AllowedMentionType.EveryoneMentions)
                                add(AllowedMentionType.RoleMentions)
                                add(AllowedMentionType.UserMentions)
                            }
                            content = if (remindData.message != null) {
                                "${remindData.message}"
                            } else {
                                ""
                            }
                            content += "\n[メッセージリンク](https://discord.com/channels/${loc.guildId}/${loc.channelId}/${loc.messageId})"
                        }
                    }
                }
            }
        }

        fun saveReminds() {
            try {
                val text = Json.encodeToString<MutableList<RemindData>>(reminds)
                File("reminds.json").writeText(text)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun call(originalMessage: Message, addToolCallOutput: (String) -> Unit) {
        val timeLong = try {
            format.parse(time).time
        } catch (e: Exception) {
            try {
                System.currentTimeMillis() + Util.processTime(time)
            } catch (e: RuntimeException) {
                return addToolCallOutput("Invalid time format.")
            }
        }
        val periodLong = period?.let { format.parse(it).time }

        reminds += RemindData(
            MessageLocation(
                originalMessage.getGuild().id.toString(),
                originalMessage.getChannel().id,
                originalMessage.id,
            ),
            originalMessage.author!!.id,
            timeLong,
            periodLong,
            message,
        )
        saveReminds()
        if (period != null) {
            addToolCallOutput("Successfully set remind at ${format.format(timeLong)} (every $period afterwards) (current: ${format.format(System.currentTimeMillis())})")
        } else {
            addToolCallOutput("Successfully set remind at ${format.format(timeLong)} (current: ${format.format(System.currentTimeMillis())})")
        }
    }

    @Serializable
    data class MessageLocation(
        val guildId: String,
        val channelId: Snowflake,
        val messageId: Snowflake,
    )

    @Serializable
    data class RemindData(
        val messageLocation: MessageLocation,
        val userId: Snowflake,
        val at: Long,
        val every: Long?,
        val message: String?,
    )
}
