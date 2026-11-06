package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.SequenceTracker

@Dao
interface SequenceDao {

    @Query("SELECT * FROM sequence_tracker WHERE year = :year LIMIT 1")
    suspend fun getTrackerForYear(year: Int): SequenceTracker?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTracker(tracker: SequenceTracker)

    @Transaction
    suspend fun getNextSequenceNumber(year: Int): Int {
        val current = getTrackerForYear(year)
        val nextSeq = if (current != null) current.lastSequenceNumber + 1 else 1
        saveTracker(SequenceTracker(year = year, lastSequenceNumber = nextSeq))
        return nextSeq
    }
}
