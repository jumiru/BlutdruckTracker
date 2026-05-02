package com.jrgames.blutdruck.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: MeasurementSession): Long

    @Query("SELECT * FROM measurement_sessions ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<MeasurementSession>>

    @Query("""
        SELECT * FROM measurement_sessions
        WHERE timestampMillis >= :fromMs AND timestampMillis < :toMs
        ORDER BY timestampMillis DESC
    """)
    fun observeInRange(fromMs: Long, toMs: Long): Flow<List<MeasurementSession>>

    @Query("SELECT * FROM measurement_sessions ORDER BY timestampMillis DESC LIMIT 1")
    fun observeLatest(): Flow<MeasurementSession?>

    @Query("DELETE FROM measurement_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        SELECT note FROM measurement_sessions
        WHERE note IS NOT NULL AND TRIM(note) != ''
        ORDER BY timestampMillis DESC
    """)
    fun observeRecentNotes(): Flow<List<String>>
}
