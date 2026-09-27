package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.SmsLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {

    @Query("SELECT * FROM sms_logs ORDER BY createdAtMillis DESC")
    fun getAllLogs(): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs ORDER BY createdAtMillis DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<SmsLogEntity>>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'SUCCESS' OR status = 'SYNCED'")
    fun getSuccessCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status LIKE '%FAIL%' OR status LIKE '%RETRY%' OR status LIKE '%QUEUE%'")
    fun getFailedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SmsLogEntity): Long

    @Query("DELETE FROM sms_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sms_logs")
    suspend fun clearAll()

    @Query("DELETE FROM sms_logs WHERE createdAtMillis < :cutoffMillis")
    suspend fun deleteLogsOlderThan(cutoffMillis: Long): Int

    @Query("UPDATE sms_logs SET status = :status, httpCode = :httpCode, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateLogStatus(id: Long, status: String, httpCode: Int?, errorMessage: String?)

    @Query("SELECT * FROM sms_logs WHERE status LIKE '%FAIL%' OR status LIKE '%RETRY%' OR status LIKE '%QUEUE%' ORDER BY createdAtMillis ASC")
    suspend fun getFailedLogsList(): List<SmsLogEntity>
}
