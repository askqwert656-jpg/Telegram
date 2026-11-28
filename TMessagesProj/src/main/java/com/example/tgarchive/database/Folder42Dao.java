package com.example.tgarchive.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface Folder42Dao {
    @Upsert
    void insertOrUpdateChat(ArchivedChatEntity chat);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertChat(ArchivedChatEntity chat);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertMessage(ArchivedMessageEntity message);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertEdit(MessageEditEntity edit);

    @Query("SELECT * FROM folder42_chats WHERE is_active = 1 ORDER BY last_synced_at DESC, added_at DESC")
    List<ArchivedChatEntity> getChats();

    @Query("SELECT * FROM folder42_chats WHERE chat_id = :chatId LIMIT 1")
    ArchivedChatEntity getChat(String chatId);

    @Query("SELECT * FROM folder42_messages WHERE chat_id = :chatId AND archive_type = 0 AND (deleted_at IS NOT NULL OR edited_at > 0) ORDER BY date DESC")
    List<ArchivedMessageEntity> getMessages(String chatId);

    @Query("SELECT * FROM folder42_messages WHERE chat_id = :chatId AND archive_type = 1 ORDER BY date DESC")
    List<ArchivedMessageEntity> getFullMessages(String chatId);

    @Query("DELETE FROM folder42_messages WHERE chat_id = :chatId AND archive_type = 1")
    int clearFullMessages(String chatId);

    @Query("SELECT * FROM folder42_messages WHERE chat_id = :chatId AND message_id = :messageId AND archive_type = 0 LIMIT 1")
    ArchivedMessageEntity getMessage(String chatId, long messageId);

    @Query("SELECT * FROM folder42_messages WHERE message_id = :messageId AND archive_type = 0")
    List<ArchivedMessageEntity> getMessagesByMessageId(long messageId);

    @Query("UPDATE folder42_chats SET is_active = 1, title = :title, chat_type = :chatType, last_synced_at = :updatedAt WHERE chat_id = :chatId")
    int activateChat(String chatId, String title, int chatType, long updatedAt);

    @Query("UPDATE folder42_messages SET text = :text, edited_at = :editedAt WHERE chat_id = :chatId AND message_id = :messageId AND archive_type = 0")
    int updateMessageText(String chatId, long messageId, String text, long editedAt);

    @Query("UPDATE folder42_messages SET deleted_at = :deletedAt WHERE chat_id = :chatId AND message_id = :messageId AND archive_type = 0")
    int markMessageDeleted(String chatId, long messageId, long deletedAt);

    @Query("DELETE FROM folder42_message_edits WHERE message_record_id IN (SELECT record_id FROM folder42_messages WHERE date < :cutoff)")
    int deleteEditsBefore(long cutoff);

    @Query("DELETE FROM folder42_messages WHERE date < :cutoff")
    int deleteMessagesBefore(long cutoff);

    @Query("SELECT * FROM folder42_message_edits WHERE message_record_id = :messageRecordId ORDER BY edited_at ASC")
    List<MessageEditEntity> getEdits(String messageRecordId);

    @Query("DELETE FROM folder42_message_edits WHERE message_record_id IN (SELECT record_id FROM folder42_messages WHERE sender_id = :senderId AND archive_type = 0)")
    int removeEditsFromSender(long senderId);

    @Query("DELETE FROM folder42_messages WHERE sender_id = :senderId AND archive_type = 0")
    int removeMessagesFromSender(long senderId);

    @Query("DELETE FROM folder42_message_edits WHERE message_record_id = :recordId")
    int removeEdits(String recordId);

    @Query("DELETE FROM folder42_messages WHERE record_id = :recordId")
    int removeMessage(String recordId);

    @Query("DELETE FROM folder42_chats WHERE chat_id = :chatId")
    int removeChat(String chatId);
}
