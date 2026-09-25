package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_logs")
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val messageBody: String,
    val timestamp: String,
    val simSlot: String = "SIM_1",
    val deviceId: String,
    val status: String, // "SUCCESS", "FAILED", "IGNORED"
    val httpCode: Int? = null,
    val errorMessage: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)
