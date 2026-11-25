package com.example.tgarchive.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface Folder42Dao {
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

    @Query("SELECT * FROM folder42_message_edits WHERE message_record_id = :messageRecordId ORDER BY edited_at ASC")
    List<MessageEditEntity> getEdits(String messageRecordId);

    @Query("DELETE FROM folder42_chats WHERE chat_id = :chatId")
    int removeChat(String chatId);
}
