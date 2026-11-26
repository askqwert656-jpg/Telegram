package org.telegram.messenger;

import android.text.TextUtils;

import com.example.tgarchive.database.ArchivedChatEntity;
import com.example.tgarchive.database.ArchivedMessageEntity;
import com.example.tgarchive.database.Folder42Database;
import com.example.tgarchive.database.MessageEditEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local persistence entry points for Folder 42 events.
 * Call these from Telegram's message/update processing after resolving the dialog metadata.
 */
public final class Folder42ArchiveManager {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Folder42ArchiveManager() {
    }

    private static boolean isOwnMessage(long senderId) {
        long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        return selfId != 0 && senderId == selfId;
    }


    public static void saveMessage(long dialogId, String chatTitle, int chatType,
                                   long messageId, long senderId, String text, long date, String mediaMetadata) {
        if (dialogId == 0 || messageId == 0 || ApplicationLoader.applicationContext == null || isOwnMessage(senderId)) {
            return;
        }
        final String chatId = String.valueOf(dialogId);
        final String safeTitle = TextUtils.isEmpty(chatTitle) ? "Переписка " + chatId : chatTitle;
        final String safeText = text == null ? "" : text;
        executor.execute(() -> {
            Folder42Database db = Folder42Database.getInstance(ApplicationLoader.applicationContext);
            db.runInTransaction(() -> {
                db.folder42Dao().insertChat(new ArchivedChatEntity(
                        chatId, safeTitle, chatType, System.currentTimeMillis(), System.currentTimeMillis(), false));
                db.folder42Dao().insertMessage(new ArchivedMessageEntity(
                        chatId + ":" + messageId, chatId, messageId, senderId, safeText,
                        date, 0, null, mediaMetadata));
            });
        });
    }

    public static void recordEdit(long dialogId, String chatTitle, int chatType,
                                  long messageId, String newText, long messageDate, long editedAt) {
        if (dialogId == 0 || messageId == 0 || ApplicationLoader.applicationContext == null) {
            return;
        }
        final String chatId = String.valueOf(dialogId);
        final String safeTitle = TextUtils.isEmpty(chatTitle) ? "Переписка " + chatId : chatTitle;
        final String safeText = newText == null ? "" : newText;
        executor.execute(() -> {
            Folder42Database db = Folder42Database.getInstance(ApplicationLoader.applicationContext);
            final boolean[] changed = {false};
            db.runInTransaction(() -> {
                ArchivedMessageEntity previous = db.folder42Dao().getMessage(chatId, messageId);
                // Without a pre-edit copy we cannot identify the author reliably.
                // Never create a speculative record for an unknown sender.
                if (previous == null || isOwnMessage(previous.senderId)) {
                    return;
                }
                if (TextUtils.equals(previous.text, safeText)) {
                    return;
                }
                long eventTime = editedAt > 0 ? editedAt : System.currentTimeMillis();
                db.folder42Dao().activateChat(chatId, safeTitle, chatType, eventTime);
                String eventId = chatId + ":" + messageId + ":edit:" + eventTime + ":" + safeText.hashCode();
                db.folder42Dao().insertEdit(new MessageEditEntity(
                        eventId, previous.recordId, previous.text, safeText, eventTime));
                db.folder42Dao().updateMessageText(chatId, messageId, safeText, eventTime);
                changed[0] = true;
            });
            if (changed[0]) {
                Folder42Notifications.notifyMessageChanged(ApplicationLoader.applicationContext,
                        safeTitle, false, dialogId, (int) messageId);
            }
        });
    }

    public static void recordDeleteByMessageId(long messageId, long channelId, String chatTitle, int chatType) {
        if (messageId == 0 || ApplicationLoader.applicationContext == null) return;
        executor.execute(() -> {
            Folder42Database db = Folder42Database.getInstance(ApplicationLoader.applicationContext);
            String chatId = null;
            if (channelId != 0) {
                chatId = String.valueOf(-channelId);
                recordDelete(-channelId, chatTitle, chatType, messageId, System.currentTimeMillis());
                return;
            } else {
                java.util.List<ArchivedMessageEntity> matches = db.folder42Dao().getMessagesByMessageId(messageId);
                if (matches == null || matches.size() != 1) return;
                chatId = matches.get(0).chatId;
            }
            com.example.tgarchive.database.ArchivedChatEntity chat = db.folder42Dao().getChat(chatId);
            if (chat == null) return;
            recordDelete(Long.parseLong(chatId), chat.title, chat.chatType, messageId, System.currentTimeMillis());
        });
    }
    public static void recordDelete(long dialogId, String chatTitle, int chatType,
                                    long messageId, long deletedAt) {
        if (dialogId == 0 || messageId == 0 || ApplicationLoader.applicationContext == null) {
            return;
        }
        final String chatId = String.valueOf(dialogId);
        final String safeTitle = TextUtils.isEmpty(chatTitle) ? "Переписка " + chatId : chatTitle;
        executor.execute(() -> {
            Folder42Database db = Folder42Database.getInstance(ApplicationLoader.applicationContext);
            ArchivedMessageEntity existing = db.folder42Dao().getMessage(chatId, messageId);
            if (existing == null || existing.deletedAt != null || isOwnMessage(existing.senderId)) {
                return;
            }
            long eventTime = deletedAt > 0 ? deletedAt : System.currentTimeMillis();
            int updated = db.folder42Dao().markMessageDeleted(chatId, messageId, eventTime);
            if (updated == 0) {
                return;
            }
            db.folder42Dao().activateChat(chatId, safeTitle, chatType, eventTime);
            Folder42Notifications.notifyMessageChanged(ApplicationLoader.applicationContext,
                    safeTitle, true, dialogId, (int) messageId);
        });
    }
}
