package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VerificationDao {
    @Query("SELECT * FROM verification_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<VerificationLogEntity>>

    @Query("SELECT * FROM verification_logs WHERE verdictName != 'VERIFIED_GENUINE' ORDER BY timestamp DESC")
    fun getFraudAlerts(): Flow<List<VerificationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: VerificationLogEntity): Long

    @Query("DELETE FROM verification_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM verification_logs")
    suspend fun clearAll()
}
