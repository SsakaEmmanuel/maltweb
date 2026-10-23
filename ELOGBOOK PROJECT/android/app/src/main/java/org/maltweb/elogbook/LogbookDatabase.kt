package org.maltweb.elogbook

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LogbookEntry::class], version = 1, exportSchema = false)
abstract class LogbookDatabase : RoomDatabase() {
    abstract fun entries(): LogbookDao

    companion object {
        @Volatile private var INSTANCE: LogbookDatabase? = null

        fun get(context: Context): LogbookDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                LogbookDatabase::class.java,
                "elogbook-local.db"
            ).build().also { INSTANCE = it }
        }
    }
}
