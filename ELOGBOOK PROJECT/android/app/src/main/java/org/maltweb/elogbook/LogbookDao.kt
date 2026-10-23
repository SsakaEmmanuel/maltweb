package org.maltweb.elogbook
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface LogbookDao {
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(entry: LogbookEntry)
 @Query("SELECT * FROM daily_entries ORDER BY entryDate DESC, updatedAt DESC") fun observeAll(): Flow<List<LogbookEntry>>
 @Query("SELECT * FROM daily_entries WHERE syncStatus = 'pending' ORDER BY updatedAt ASC") suspend fun pendingSync(): List<LogbookEntry>
 @Query("UPDATE daily_entries SET syncStatus = 'synced' WHERE id = :id") suspend fun markSynced(id: String)
 @Query("SELECT COUNT(*) FROM daily_entries WHERE syncStatus = 'pending'") fun pendingCount(): Flow<Int>
}
