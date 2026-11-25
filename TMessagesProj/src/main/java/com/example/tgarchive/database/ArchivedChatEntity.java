package com.example.tgarchive.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** A locally indexed chat stored in PotuzhnoGram Folder 42. */
@Entity(tableName = "folder42_chats")
public class ArchivedChatEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "chat_id")
    public String chatId;

    @NonNull
    @ColumnInfo(name = "title")
    public String title = "";

    @ColumnInfo(name = "chat_type")
    public int chatType;

    @ColumnInfo(name = "last_synced_at")
    public long lastSyncedAt;

    @ColumnInfo(name = "added_at")
    public long addedAt;

    @ColumnInfo(name = "is_active", defaultValue = "1")
    public boolean isActive = true;

    @Ignore
    public ArchivedChatEntity(@NonNull String chatId, @NonNull String title, int chatType, long lastSyncedAt, long addedAt) {
        this(chatId, title, chatType, lastSyncedAt, addedAt, true);
    }

    public ArchivedChatEntity(@NonNull String chatId, @NonNull String title, int chatType, long lastSyncedAt, long addedAt, boolean isActive) {
        this.chatId = chatId;
        this.title = title;
        this.chatType = chatType;
        this.lastSyncedAt = lastSyncedAt;
        this.addedAt = addedAt;
        this.isActive = isActive;
    }
}
