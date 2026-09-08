package com.millmitra.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for MillMitra operations.
 * Supports a strict 7-day retention cycle with auto-pruning.
 */
@Dao
interface MillEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MillEntry): Long

    /**
     * Retrieves all records generated in the last 7 days, sorted latest first.
     */
    @Query("SELECT * FROM mill_history WHERE timestamp >= :cutoffTimestamp ORDER BY timestamp DESC")
    fun getEntriesWithinLast7Days(cutoffTimestamp: Long): Flow<List<MillEntry>>

    /**
     * Retrieves all records regardless of time (for full logs if needed).
     */
    @Query("SELECT * FROM mill_history ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<MillEntry>>

    /**
     * Purges records older than 7 days from the device.
     * @return Number of deleted rows.
     */
    @Query("DELETE FROM mill_history WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOldRecords(cutoffTimestamp: Long): Int

    @Query("DELETE FROM mill_history WHERE id = :entryId")
    suspend fun deleteEntryById(entryId: Long)

    @Query("DELETE FROM mill_history")
    suspend fun clearAllHistory()
}
