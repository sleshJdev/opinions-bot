package by.jprof.telegram.opinions.publication

import by.jprof.telegram.components.entity.DynamoAttrs
import by.jprof.telegram.opinions.news.entity.InsideJavaNewscastAttrs
import by.jprof.telegram.opinions.news.entity.InsideJavaPodcastAttrs
import by.jprof.telegram.opinions.news.entity.JepAttrs
import by.jprof.telegram.opinions.news.queue.Event
import by.jprof.telegram.opinions.news.queue.NewsQueue
import by.jprof.telegram.opinions.voting.JEPLinksVoting
import by.jprof.telegram.opinions.voting.YoutubeVoting
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.extensions.api.send.media.sendPhoto
import com.github.insanusmokrassar.TelegramBotAPI.extensions.api.send.sendMessage
import com.github.insanusmokrassar.TelegramBotAPI.requests.abstracts.FileId
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.ParseMode.MarkdownV2ParseMode
import com.github.insanusmokrassar.TelegramBotAPI.types.toChatId
import org.apache.logging.log4j.LogManager

class TelegramPublisher(
    val queue: NewsQueue,
    val chats: ChatDao,
    val youtubeVoting: YoutubeVoting,
    val jepVoting: JEPLinksVoting,
    val bot: RequestsExecutor
) : Publisher {
    companion object {
        private val logger = LogManager.getLogger(TelegramPublisher::class.java)!!
    }

    override suspend fun publish() {
        publishOneNews(Event.INSIDE_JAVA_PODCAST, this::announcePodcast)
                || publishOneNews(Event.INSIDE_JAVA_NEWSCAST, this::announceNewscast)
                || publishOneNews(Event.JEP, this::announceJep)
    }

    private suspend fun <T : DynamoAttrs> publishOneNews(
        event: Event,
        poster: suspend (ChatAttrs, T) -> Unit
    ): Boolean {
        val eligibleChats = chats.findAll(event)
        return eligibleChats.isNotEmpty() && queue.news<T>(event)
            .sortedByDescending { it.createdAt ?: it.queuedAt }
            .any { news ->
                eligibleChats.forEach { chat ->
                    logger.info("Publish {} to {}", news, chat)
                    poster(chat, news.payload)
                }
                queue.markProcessed(news)
                return true
            }
    }

    private suspend fun announceJep(chat: ChatAttrs, item: JepAttrs) {
        jepVoting.sendVoteForJep(chat.chatId.toLong().toChatId(), item.jep)
    }

    private suspend fun announceNewscast(chat: ChatAttrs, item: InsideJavaNewscastAttrs) {
        youtubeVoting.sendVoteForVideoMessage(chat.chatId.toLong().toChatId(), item.videoId)
    }

    private suspend fun announcePodcast(chat: ChatAttrs, item: InsideJavaPodcastAttrs) {
        if (item.fileId != null) {
            logger.info(
                "Sending photo {} with caption {} to {}",
                item.fileId, item.caption
            )

            bot.sendPhoto(
                chatId = ChatId(chat.chatId.toLong()),
                fileId = FileId(item.fileId!!),
                caption = item.caption,
                parseMode = MarkdownV2ParseMode,
            )
        } else {
            logger.info(
                "Sending text {} to {}",
                item.caption, chat.chatId
            )

            bot.sendMessage(
                chatId = ChatId(chat.chatId.toLong()),
                text = item.caption,
                parseMode = MarkdownV2ParseMode,
            )
        }
    }
}