package by.jprof.telegram.opinions.processors

import by.jprof.telegram.opinions.dao.KotlinMentionsDAO
import by.jprof.telegram.opinions.dao.download
import by.jprof.telegram.opinions.entity.KotlinMention
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.extensions.api.get.getFileAdditionalInfo
import com.github.insanusmokrassar.TelegramBotAPI.extensions.api.send.media.sendSticker
import com.github.insanusmokrassar.TelegramBotAPI.extensions.api.send.sendTextMessage
import com.github.insanusmokrassar.TelegramBotAPI.requests.abstracts.toInputFile
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.MessageIdentifier
import com.github.insanusmokrassar.TelegramBotAPI.types.message.CommonMessageImpl
import com.github.insanusmokrassar.TelegramBotAPI.types.message.abstracts.ContentMessage
import com.github.insanusmokrassar.TelegramBotAPI.types.message.content.TextContent
import com.github.insanusmokrassar.TelegramBotAPI.types.message.content.media.PhotoContent
import com.github.insanusmokrassar.TelegramBotAPI.types.message.content.media.StickerContent
import com.github.insanusmokrassar.TelegramBotAPI.types.toChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.update.MessageUpdate
import com.github.insanusmokrassar.TelegramBotAPI.types.update.abstracts.Update
import com.github.insanusmokrassar.TelegramBotAPI.utils.TelegramAPIUrlsKeeper
import kotlinx.coroutines.time.delay
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.random.Random

class KotlinMentionsProcessor(
        private val bot: RequestsExecutor,
        private val kotlinMentionsDAO: KotlinMentionsDAO,
        private val tesseract: Tesseract,
        private val telegramAPIUrlsKeeper: TelegramAPIUrlsKeeper,
        private val stickerFileId: String = zeroDaysWithoutKotlinStickerFileId,
        private val hasPassedEnoughTimeSincePreviousMention: (Duration) -> Boolean = ::hasPassedEnoughTimeSincePreviousMention,
        private val composeStickerMessage: (Duration) -> String = ::composeStickerMessage
) : UpdateProcessor {
    companion object {
        private const val zeroDaysWithoutKotlinStickerFileId = "CAACAgIAAxkBAAIBsF8V0dPb6EesBKSujFFOx_URfhSdAAJAAQACqSImBOs5DmSNtKlmGgQ"
        private val kotlinRegex = "([kк]+.{0,3}[оo0aа]+.{0,3}[тt]+.{0,3}[лl]+.{0,3}[ие1ie]+.{0,3}[нnH]+)".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        private val minRequiredDelayBetweenReplies: Duration = Duration.ofMinutes(30)!!
        private val maxRequiredDelayBetweenReplies: Duration = Duration.ofHours(1)!!

        private fun hasPassedEnoughTimeSincePreviousMention(duration: Duration): Boolean =
                duration.toMillis() > Random.nextLong(
                        minRequiredDelayBetweenReplies.toMillis(),
                        maxRequiredDelayBetweenReplies.toMillis())

        fun composeStickerMessage(duration: Duration): String =
                "Passed %02dd:%02dh:%02dm:%02ds without an incident".format(
                        duration.toDaysPart(), duration.toHoursPart(),
                        duration.toMinutesPart(), duration.toSecondsPart())
    }

    override suspend fun process(update: Update) {
        val message = (update as? MessageUpdate) ?: return
        val contentMessage = (message.data as? CommonMessageImpl<*>) ?: return
        if (!containsMatchIn(contentMessage)) return
        val chatId = contentMessage.chat.id.chatId
        val userId = contentMessage.user.id.chatId
        val mentions = kotlinMentionsDAO.find(chatId)
                ?: return sendSticker(
                        KotlinMention(chatId, Instant.now()),
                        contentMessage.messageId)

        val duration = Duration.between(mentions.timestamp, Instant.now())
        var updatedMention = mentions.updateUserStats(userId)
        if (!hasPassedEnoughTimeSincePreviousMention(duration)) {
            kotlinMentionsDAO.save(updatedMention)
            return
        }

        updatedMention = updatedMention.copy(timestamp = Instant.now())
        sendSticker(updatedMention, contentMessage.messageId) {
            delay(Duration.of(2, ChronoUnit.SECONDS))
            bot.sendTextMessage(chatId.toChatId(),
                    composeStickerMessage(duration),
                    replyToMessageId = it.messageId)
        }
    }

    private suspend fun containsMatchIn(contentMessage: ContentMessage<*>): Boolean {
        val content = contentMessage.content
        return (content is TextContent) && containsInText(content)
                || (content is PhotoContent) && containsInImage(content)
    }

    private fun containsInText(textContent: TextContent): Boolean =
            kotlinRegex.containsMatchIn(textContent.text)

    private suspend fun containsInImage(photoContent: PhotoContent): Boolean {
        val fileInfo = bot.getFileAdditionalInfo(photoContent.media.fileId)
        val imageFile = fileInfo.download(telegramAPIUrlsKeeper)
        return Lang.values().any {
            kotlinRegex.containsMatchIn(tesseract.ocr(imageFile, it))
        }
    }

    private suspend fun sendSticker(
            mentions: KotlinMention,
            messageId: MessageIdentifier,
            onSend: suspend (ContentMessage<StickerContent>) -> Unit = {}
    ) {
        val response = bot.sendSticker(
                ChatId(mentions.chatId),
                stickerFileId.toInputFile(),
                replyToMessageId = messageId
        )

        onSend(response)

        kotlinMentionsDAO.save(mentions)
    }
}
