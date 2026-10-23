package org.maltweb.elogbook

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LogbookEntry::class, WeeklySummary::class], version = 2, exportSchema = false)
abstract class LogbookDatabase : RoomDatabase() {
    abstract fun entries(): LogbookDao
    abstract fun weeklies(): WeeklySummaryDao

    companion object {
        @Volatile private var INSTANCE: LogbookDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS weekly_summaries (id TEXT NOT NULL, weekStart TEXT NOT NULL, summary TEXT NOT NULL, skillsLearned TEXT NOT NULL, challenges TEXT NOT NULL, ownerUserId TEXT NOT NULL, syncStatus TEXT NOT NULL, supervisorComments TEXT NOT NULL, supervisorReviewedAt TEXT, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_weekly_summaries_ownerUserId_weekStart ON weekly_summaries (ownerUserId, weekStart)")
            }
        }

        fun get(context: Context): LogbookDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, LogbookDatabase::class.java, "elogbook-local.db")
                .addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
}
