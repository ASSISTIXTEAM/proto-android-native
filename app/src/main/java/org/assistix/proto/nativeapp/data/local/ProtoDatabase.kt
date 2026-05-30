package org.assistix.proto.nativeapp.data.local

import android.content.Context
import android.util.Log
import org.assistix.proto.nativeapp.data.ProtoPersistentStorage
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MessageEntity::class,
        OutboxEntity::class,
        ConversationEntity::class,
        AssistixThreadEntity::class,
        AssistixMessageEntity::class,
        MessageTranslationEntity::class,
    ],
    version = 11,
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
            repeat(2) { attempt ->
                try {
                    return build(context)
                } catch (e: Exception) {
                    Log.w(TAG, "open attempt ${attempt + 1} failed", e)
                    runCatching { context.deleteDatabase(DB_NAME) }
                    reset()
                }
            }
            return build(context)
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

        private fun build(context: Context): ProtoDatabase {
            ProtoPersistentStorage.initAndMigrate(context)
            val dbFile = ProtoPersistentStorage.databaseFile(context)
            return Room.databaseBuilder(context, ProtoDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_9_10, MIGRATION_10_11)
                .build()
        }
    }
}
