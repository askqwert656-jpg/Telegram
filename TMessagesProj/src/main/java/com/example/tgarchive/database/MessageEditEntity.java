package com.example.tgarchive.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Immutable snapshot of a message edit; eventId makes ingestion idempotent. */
@Entity(
    tableName = "folder42_message_edits",
    foreignKeys = @ForeignKey(
        entity = ArchivedMessageEntity.class,
        parentColumns = "record_id",
        childColumns = "message_record_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index(value = "message_record_id"), @Index(value = "edited_at")}
)
public class MessageEditEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "event_id")
    public String eventId;

    @NonNull
    @ColumnInfo(name = "message_record_id")
    public String messageRecordId;

    @NonNull
    @ColumnInfo(name = "previous_text")
    public String previousText;

    @NonNull
    @ColumnInfo(name = "new_text")
    public String newText;

    @ColumnInfo(name = "edited_at")
    public long editedAt;

    public MessageEditEntity(@NonNull String eventId, @NonNull String messageRecordId,
                             @NonNull String previousText, @NonNull String newText, long editedAt) {
        this.eventId = eventId;
        this.messageRecordId = messageRecordId;
        this.previousText = previousText;
        this.newText = newText;
        this.editedAt = editedAt;
    }
}
