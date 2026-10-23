package org.maltweb.elogbook

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "daily_entries",
    indices = [Index(value = ["ownerUserId", "entryDate"], unique = true)]
)
data class LogbookEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryDate: String,
    val workDone: String,
    val toolsUsed: String,
    val skillsLearned: String,
    val challenges: String,
    val recommendations: String,
    val ownerUserId: String = "local",
    val syncStatus: String = "pending",
    val updatedAt: Long = System.currentTimeMillis()
)
