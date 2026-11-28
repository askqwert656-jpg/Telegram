package org.telegram.messenger;

import android.text.TextUtils;

import com.example.tgarchive.database.ArchivedChatEntity;
import com.example.tgarchive.database.ArchivedMessageEntity;
import com.example.tgarchive.database.Folder42Database;
import com.example.tgarchive.database.MessageEditEntity;

import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Local persistence entry points for Folder 42 events.
 * Call these from Telegram's message/update processing after resolving the dialog metadata.
 */
public final class Folder42ArchiveManager {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final String PREFS = "folder42_archive_settings";
    private static final String KEY_RETENTION_DAYS = "retention_days";

    private Folder42ArchiveManager() {
    }

    public static int getRetentionDays(android.content.Context context) {
        return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getInt(KEY_RETENTION_DAYS, 0);
    }

    public static void setRetentionDays(android.content.Context context, int days) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putInt(KEY_RETENTION_DAYS, Math.max(0, days)).apply();
    }

    public static void cleanupExpiredMessages(android.content.Context context) {
        if (context == null) return;
        final int retentionDays = getRetentionDays(context);
        final long cutoff = retentionDays > 0 ? System.currentTimeMillis() / 1000L - retentionDays * 86400L : 0;
        executor.execute(() -> {
            Folder42Database db = Folder42Database.getInstance(context.getApplicationContext());
            db.runInTransaction(() -> {
                long ownUserId = UserConfig.getInstance(UserConfig.selectedAccount).clientUserId;
                db.folder42Dao().removeEditsFromSender(ownUserId);
                db.folder42Dao().removeMessagesFromSender(ownUserId);
                if (retentionDays > 0) {
                    db.folder42Dao().deleteEditsBefore(cutoff);
                    db.folder42Dao().deleteMessagesBefore(cutoff);
                }
            });
        });
    }

    private static void cleanupExpiredMessagesNow(Folder42Database db) {
        long ownUserId = UserConfig.getInstance(UserConfig.selectedAccount).clientUserId;
        db.folder42Dao().removeEditsFromSender(ownUserId);
        db.folder42Dao().removeMessagesFromSender(ownUserId);
        int days = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getInt(KEY_RETENTION_DAYS, 0);
        if (days <= 0) return;
        long cutoff = System.currentTimeMillis() / 1000L - days * 86400L;
        db.folder42Dao().deleteEditsBefore(cutoff);
        db.folder42Dao().deleteMessagesBefore(cutoff);
    }


    public static void saveMessage(long dialogId, String chatTitle, int chatType,
                                   long messageId, long senderId, String text, long date, String mediaMetadata) {
        if (dialogId == 0 || messageId == 0 || ApplicationLoader.applicationContext == null ||
                senderId == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
            return;
        }
        final String chatId = String.valueOf(dialogId);
        final String safeTitle = TextUtils.isEmpty(chatTitle) ? "Переписка " + chatId : chatTitle;
        final String safeText = text == null ? "" : text;
        executor.execute(() -> {
            Folder42Database db = Folder42Database.getInstance(ApplicationLoader.applicationContext);
            cleanupExpiredMessagesNow(db);
            final boolean[] changed = {false};
            db.runInTransaction(() -> {
                long now = System.currentTimeMillis();
                db.folder42Dao().insertChat(new ArchivedChatEntity(
                        chatId, safeTitle, chatType, now, now, false));
                ArchivedMessageEntity previous = db.folder42Dao().getMessage(chatId, messageId);
                if (previous == null) {
                    db.folder42Dao().insertMessage(new ArchivedMessageEntity(
                            chatId + ":" + messageId, chatId, messageId, senderId, safeText,
                            date, 0, null, mediaMetadata));
                } else if (previous.archiveType == 0 && previous.deletedAt == null &&
                        !TextUtils.equals(previous.text, safeText)) {
                    long eventTime = System.currentTimeMillis();
                    String eventId = chatId + ":" + messageId + ":edit:" + eventTime + ":" + safeText.hashCode();
                    db.folder42Dao().insertEdit(new MessageEditEntity(
                            eventId, previous.recordId, previous.text, safeText, eventTime));
                    db.folder42Dao().updateMessageText(chatId, messageId, safeText, eventTime);
                    db.folder42Dao().activateChat(chatId, safeTitle, chatType, eventTime);
                    changed[0] = true;
                }
            });
            if (changed[0]) {
                Folder42Notifications.notifyMessageChanged(ApplicationLoader.applicationContext,
                        safeTitle, false, dialogId, (int) messageId);
            }
        });
    }

    private static String archiveMessageMedia(int account, long dialogId, TLRPC.Message message) {
        TLRPC.MessageMedia media = MessageObject.getMedia(message);
        if (media == null) return null;
        String mediaType = media.getClass().getSimpleName();
        File source = FileLoader.getInstance(account).getPathToMessage(message, false);
        boolean canDownload = media instanceof TLRPC.TL_messageMediaDocument
                || media instanceof TLRPC.TL_messageMediaPhoto
                || media instanceof TLRPC.TL_messageMediaWebPage
                && (((TLRPC.TL_messageMediaWebPage) media).webpage.document != null
                || ((TLRPC.TL_messageMediaWebPage) media).webpage.photo != null);
        if (!source.isFile() && !canDownload) {
            return mediaType + "|not_supported";
        }
        if (!source.isFile()) {
            try {
                if (media instanceof TLRPC.TL_messageMediaDocument) {
                    FileLoader.getInstance(account).loadFile(((TLRPC.TL_messageMediaDocument) media).document, message, 1, 0);
                } else if (media instanceof TLRPC.TL_messageMediaPhoto) {
                    TLRPC.Photo photo = ((TLRPC.TL_messageMediaPhoto) media).photo;
                    TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(
                            photo.sizes, AndroidUtilities.getPhotoSize(true), false, null, true);
                    if (size != null) {
                        FileLoader.getInstance(account).loadFile(ImageLocation.getForObject(size, photo), message, null, 1, 0);
                    }
                } else if (media instanceof TLRPC.TL_messageMediaWebPage) {
                    TLRPC.WebPage page = ((TLRPC.TL_messageMediaWebPage) media).webpage;
                    if (page.document != null) {
                        FileLoader.getInstance(account).loadFile(page.document, message, 1, 0);
                    } else if (page.photo != null) {
                        TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(
                                page.photo.sizes, AndroidUtilities.getPhotoSize(true), false, null, true);
                        if (size != null) {
                            FileLoader.getInstance(account).loadFile(ImageLocation.getForObject(size, page.photo), message, null, 1, 0);
                        }
                    }
                }
                long deadline = System.currentTimeMillis() + 45000;
                while (!source.isFile() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(250);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (!source.isFile() || source.length() <= 0) {
            return mediaType + "|not_downloaded";
        }
        try {
            File root = new File(ApplicationLoader.applicationContext.getFilesDir(), "folder42_media/" + dialogId);
            if (!root.exists() && !root.mkdirs()) {
                return mediaType + "|not_saved";
            }
            File destination = new File(root, message.id + "_" + source.getName());
            if (!destination.isFile() || destination.length() != source.length()) {
                try (FileInputStream input = new FileInputStream(source);
                     FileOutputStream output = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    output.getFD().sync();
                }
            }
            return mediaType + "|" + destination.getAbsolutePath();
        } catch (Exception e) {
            FileLog.e(e);
            return mediaType + "|not_saved";
        }
    }

    public static void saveFullConversation(int account, long dialogId, String chatTitle, int chatType,
                                            java.util.ArrayList<TLRPC.Message> messages, Runnable completion) {
        if (dialogId == 0 || messages == null || ApplicationLoader.applicationContext == null) {
            if (completion != null) AndroidUtilities.runOnUIThread(completion);
            return;
        }
        final String chatId = String.valueOf(dialogId);
        final String safeTitle = TextUtils.isEmpty(chatTitle) ? "Переписка " + chatId : chatTitle;
        final long ownUserId = UserConfig.getInstance(account).clientUserId;
        executor.execute(() -> {
            Folder42Database db = Folder42Database.getInstance(ApplicationLoader.applicationContext);
            Map<Integer, Future<String>> mediaTasks = new HashMap<>();
            ExecutorService mediaExecutor = Executors.newFixedThreadPool(3);
            try {
                for (TLRPC.Message message : messages) {
                    if (message == null || message.id == 0 || MessageObject.getMedia(message) == null) continue;
                    mediaTasks.put(message.id, mediaExecutor.submit(() -> archiveMessageMedia(account, dialogId, message)));
                }
                long mediaDeadline = System.currentTimeMillis() + 90000;
                Map<Integer, String> mediaFiles = new HashMap<>();
                for (Map.Entry<Integer, Future<String>> entry : mediaTasks.entrySet()) {
                    long remaining = mediaDeadline - System.currentTimeMillis();
                    if (remaining <= 0) break;
                    try {
                        mediaFiles.put(entry.getKey(), entry.getValue().get(remaining, TimeUnit.MILLISECONDS));
                    } catch (Exception e) {
                        entry.getValue().cancel(true);
                    }
                }
                db.runInTransaction(() -> {
                    long now = System.currentTimeMillis();
                    db.folder42Dao().insertOrUpdateChat(new ArchivedChatEntity(
                            chatId, safeTitle, chatType, now, now, true));
                    db.folder42Dao().clearFullMessages(chatId);
                    for (TLRPC.Message message : messages) {
                        if (message == null || message.id == 0) continue;
                        long senderId = ownUserId;
                        if (message.from_id != null) {
                            if (message.from_id.user_id != 0) senderId = message.from_id.user_id;
                            else if (message.from_id.channel_id != 0) senderId = -message.from_id.channel_id;
                            else if (message.from_id.chat_id != 0) senderId = -message.from_id.chat_id;
                        } else if (!message.out && dialogId > 0) {
                            senderId = dialogId;
                        }
                        String body = message.message == null ? "" : message.message;
                        String media = mediaFiles.get(message.id);
                        if (media == null && MessageObject.getMedia(message) != null) {
                            media = MessageObject.getMedia(message).getClass().getSimpleName() + "|not_downloaded";
                        }
                        if (TextUtils.isEmpty(body) && media != null) body = "Вложение: " + media.substring(0, media.indexOf('|') >= 0 ? media.indexOf('|') : media.length());
                        if (TextUtils.isEmpty(body)) body = "Сообщение без текста";
                        db.folder42Dao().insertMessage(new ArchivedMessageEntity(
                                "full:" + chatId + ":" + message.id, chatId, message.id, senderId,
                                body, message.date, 0, null, media, 1));
                    }
                });
            } finally {
                mediaExecutor.shutdownNow();
            }
            if (completion != null) AndroidUtilities.runOnUIThread(completion);
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
            cleanupExpiredMessagesNow(db);
            final boolean[] changed = {false};
            db.runInTransaction(() -> {
                ArchivedMessageEntity previous = db.folder42Dao().getMessage(chatId, messageId);
                // Without a pre-edit copy we cannot identify the author reliably.
                // Never create a speculative record for an unknown sender.
                if (previous == null || previous.archiveType != 0 ||
                        previous.senderId == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
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
            cleanupExpiredMessagesNow(db);
            ArchivedMessageEntity existing = db.folder42Dao().getMessage(chatId, messageId);
            if (existing == null || existing.archiveType != 0 || existing.deletedAt != null ||
                    existing.senderId == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
                return;
            }
            ArchivedChatEntity archivedChat = db.folder42Dao().getChat(chatId);
            String notificationTitle = TextUtils.isEmpty(chatTitle) && archivedChat != null ? archivedChat.title : safeTitle;
            int resolvedChatType = archivedChat != null ? archivedChat.chatType : chatType;
            long eventTime = deletedAt > 0 ? deletedAt : System.currentTimeMillis();
            int updated = db.folder42Dao().markMessageDeleted(chatId, messageId, eventTime);
            if (updated == 0) {
                return;
            }
            db.folder42Dao().activateChat(chatId, notificationTitle, resolvedChatType, eventTime);
            Folder42Notifications.notifyMessageChanged(ApplicationLoader.applicationContext,
                    notificationTitle, true, dialogId, (int) messageId);
        });
    }
}
