package org.assistix.proto.nativeapp.data.local

import android.content.Context
import android.util.Log
import org.assistix.proto.nativeapp.data.ProtoDataStoreFactory
import org.assistix.proto.nativeapp.data.ProtoPersistentStorage
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [
        MessageEntity::class,
        OutboxEntity::class,
        ConversationEntity::class,
        AssistixThreadEntity::class,
        AssistixMessageEntity::class,
        MessageTranslationEntity::class,
        MediaLocalEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
abstract class ProtoDatabase : RoomDatabase() {
    abstract fun dao(): ProtoDao

    companion object {
        private const val TAG = "ProtoDatabase"
        private const val DB_NAME = "proto_local.db"

        @Volatile
        private var instance: ProtoDatabase? = null

        fun reset() {
            synchronized(this) {
                runCatching { instance?.close() }
                instance = null
            }
        }

        fun get(context: Context): ProtoDatabase {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val appCtx = context.applicationContext
                instance = openWithRecovery(appCtx)
                return instance!!
            }
        }

        private fun openWithRecovery(context: Context): ProtoDatabase {
            repeat(3) { attempt ->
                try {
                    return build(context)
                } catch (e: Exception) {
                    Log.w(TAG, "open attempt ${attempt + 1} failed", e)
                    wipeDatabaseFiles(context)
                    reset()
                }
            }
            return build(context, useInternalFallback = true)
        }

        private fun wipeDatabaseFiles(context: Context) {
            runCatching { context.deleteDatabase(DB_NAME) }
            runCatching {
                val external = ProtoPersistentStorage.databaseFile(context)
                deleteSqliteFamily(external)
            }
            runCatching {
                deleteSqliteFamily(context.getDatabasePath(DB_NAME))
            }
            ProtoDataStoreFactory.invalidateAll()
        }

        private fun deleteSqliteFamily(dbFile: File) {
            if (dbFile.exists()) dbFile.delete()
            File(dbFile.path + "-shm").takeIf { it.exists() }?.delete()
            File(dbFile.path + "-wal").takeIf { it.exists() }?.delete()
        }

        private val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS message_translations (
                            messageId INTEGER NOT NULL,
                            targetLang TEXT NOT NULL,
                            sourceHash TEXT NOT NULL,
                            translatedText TEXT NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(messageId, targetLang)
                        )
                        """.trimIndent(),
                    )
                }
            }

        private val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE conversations ADD COLUMN channelNick TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE conversations ADD COLUMN channelVerified INTEGER NOT NULL DEFAULT 0")
                }
            }

        private val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS media_local (
                            uploadId TEXT NOT NULL PRIMARY KEY,
                            localPath TEXT NOT NULL,
                            mime TEXT NOT NULL DEFAULT '',
                            fileName TEXT NOT NULL DEFAULT '',
                            sizeBytes INTEGER NOT NULL DEFAULT 0,
                            savedAt INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent(),
                    )
                }
            }

        private fun build(context: Context, useInternalFallback: Boolean = false): ProtoDatabase {
            runCatching { ProtoPersistentStorage.initAndMigrate(context) }
                .onFailure { e -> Log.w(TAG, "storage migrate skipped", e) }
            val dbPath =
                if (useInternalFallback) {
                    context.getDatabasePath(DB_NAME).absolutePath
                } else {
                    ProtoPersistentStorage.databaseFile(context).absolutePath
                }
            File(dbPath).parentFile?.mkdirs()
            return Room.databaseBuilder(context, ProtoDatabase::class.java, dbPath)
                .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .build()
        }
    }
}
