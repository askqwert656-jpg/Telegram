package com.example.tgarchive.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A locally stored message record. Media bytes are stored separately by the vault. */
@Entity(
    tableName = "folder42_messages",
    foreignKeys = @ForeignKey(
        entity = ArchivedChatEntity.class,
        parentColumns = "chat_id",
        childColumns = "chat_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index(value = "chat_id"), @Index(value = {"chat_id", "message_id"}, unique = true)}
)
public class ArchivedMessageEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "record_id")
    public String recordId;

    @NonNull
    @ColumnInfo(name = "chat_id")
    public String chatId;

    @ColumnInfo(name = "message_id")
    public long messageId;

    @ColumnInfo(name = "sender_id")
    public long senderId;

    @NonNull
    @ColumnInfo(name = "text")
    public String text = "";

    @ColumnInfo(name = "date")
    public long date;

    @ColumnInfo(name = "edited_at")
    public long editedAt;

    @ColumnInfo(name = "deleted_at")
    public Long deletedAt;

    @ColumnInfo(name = "media_metadata")
    public String mediaMetadata;

    public ArchivedMessageEntity(@NonNull String recordId, @NonNull String chatId, long messageId,
                                 long senderId, @NonNull String text, long date, long editedAt,
                                 Long deletedAt, String mediaMetadata) {
        this.recordId = recordId;
        this.chatId = chatId;
        this.messageId = messageId;
        this.senderId = senderId;
        this.text = text;
        this.date = date;
        this.editedAt = editedAt;
        this.deletedAt = deletedAt;
        this.mediaMetadata = mediaMetadata;
    }
}
