package com.example.tgarchive.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface Folder42Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertOrUpdateChat(ArchivedChatEntity chat);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertChat(ArchivedChatEntity chat);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertMessage(ArchivedMessageEntity message);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertEdit(MessageEditEntity edit);

    @Query("SELECT * FROM folder42_chats ORDER BY added_at DESC")
    List<ArchivedChatEntity> getChats();

    @Query("SELECT * FROM folder42_messages WHERE chat_id = :chatId ORDER BY date DESC")
    List<ArchivedMessageEntity> getMessages(String chatId);

    @Query("SELECT * FROM folder42_messages WHERE chat_id = :chatId AND message_id = :messageId LIMIT 1")
    ArchivedMessageEntity getMessage(String chatId, long messageId);

    @Query("UPDATE folder42_messages SET text = :text, edited_at = :editedAt WHERE chat_id = :chatId AND message_id = :messageId")
    int updateMessageText(String chatId, long messageId, String text, long editedAt);

    @Query("UPDATE folder42_messages SET deleted_at = :deletedAt WHERE chat_id = :chatId AND message_id = :messageId")
    int markMessageDeleted(String chatId, long messageId, long deletedAt);

    @Query("SELECT * FROM folder42_message_edits WHERE message_record_id = :messageRecordId ORDER BY edited_at ASC")
    List<MessageEditEntity> getEdits(String messageRecordId);

    @Query("DELETE FROM folder42_chats WHERE chat_id = :chatId")
    int removeChat(String chatId);
}
