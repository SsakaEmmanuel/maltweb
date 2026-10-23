package org.maltweb.elogbook

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "weekly_summaries",
    indices = [Index(value = ["ownerUserId", "weekStart"], unique = true)]
)
data class WeeklySummary(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val weekStart: String,
    val summary: String,
    val skillsLearned: String = "",
    val challenges: String = "",
    val ownerUserId: String = "local",
    val syncStatus: String = "pending",
    val supervisorComments: String = "",
    val supervisorReviewedAt: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
