package org.maltweb.elogbook

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(summary: WeeklySummary)

    @Query("SELECT * FROM weekly_summaries WHERE ownerUserId = :ownerId ORDER BY weekStart DESC, updatedAt DESC")
    fun observeAllForOwner(ownerId: String): Flow<List<WeeklySummary>>

    @Query("SELECT * FROM weekly_summaries WHERE syncStatus = 'pending' AND ownerUserId = :ownerId ORDER BY updatedAt ASC")
    suspend fun pendingSync(ownerId: String): List<WeeklySummary>

    @Query("UPDATE weekly_summaries SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE weekly_summaries SET ownerUserId = :userId WHERE ownerUserId = 'local'")
    suspend fun claimLocalSummaries(userId: String)

    @Query("UPDATE weekly_summaries SET supervisorComments = :comments, supervisorReviewedAt = :reviewedAt WHERE ownerUserId = :ownerId AND weekStart = :weekStart")
    suspend fun updateReview(ownerId: String, weekStart: String, comments: String, reviewedAt: String?)
}
