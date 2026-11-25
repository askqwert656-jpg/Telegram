package com.example.tgarchive.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Private local database for Folder 42. No destructive migration fallback is configured:
 * future schema changes must provide explicit migrations to preserve saved records.
 */
@Database(
    entities = {ArchivedChatEntity.class, ArchivedMessageEntity.class, MessageEditEntity.class},
    version = 1,
    exportSchema = false
)
public abstract class Folder42Database extends RoomDatabase {
    private static volatile Folder42Database INSTANCE;

    public abstract Folder42Dao folder42Dao();

    public static Folder42Database getInstance(@NonNull Context context) {
        Folder42Database instance = INSTANCE;
        if (instance == null) {
            synchronized (Folder42Database.class) {
                instance = INSTANCE;
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.getApplicationContext(),
                        Folder42Database.class,
                        "potuzhnogram_folder_42.db"
                    ).build();
                    INSTANCE = instance;
                }
            }
        }
        return instance;
    }
}
