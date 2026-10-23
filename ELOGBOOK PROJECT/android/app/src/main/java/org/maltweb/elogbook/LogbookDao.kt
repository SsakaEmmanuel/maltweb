package org.maltweb.elogbook

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogbookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: LogbookEntry)

    @Query("SELECT * FROM daily_entries WHERE ownerUserId = :ownerId AND entryDate = :entryDate LIMIT 1")
    suspend fun findByOwnerAndDate(ownerId: String, entryDate: String): LogbookEntry?

    @Query("SELECT * FROM daily_entries WHERE ownerUserId = :ownerId ORDER BY entryDate DESC, updatedAt DESC")
    fun observeAllForOwner(ownerId: String): Flow<List<LogbookEntry>>

    @Query("SELECT * FROM daily_entries WHERE syncStatus = 'pending' AND ownerUserId = :ownerId ORDER BY updatedAt ASC")
    suspend fun pendingSync(ownerId: String): List<LogbookEntry>

    @Query("UPDATE daily_entries SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE daily_entries SET ownerUserId = :userId WHERE ownerUserId = 'local'")
    suspend fun claimLocalEntries(userId: String)

    @Query("UPDATE daily_entries SET supervisorComments = :comments, supervisorReviewedAt = :reviewedAt WHERE ownerUserId = :ownerId AND entryDate = :entryDate")
    suspend fun updateReview(ownerId: String, entryDate: String, comments: String, reviewedAt: String?)

    @Query("SELECT COUNT(*) FROM daily_entries WHERE syncStatus = 'pending' AND ownerUserId = :ownerId")
    fun pendingCount(ownerId: String): Flow<Int>
}
