package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks the user's daily study activity to calculate streaks.
 * @param date A string representing the date (e.g., "2026-07-01") in local time.
 */
@Entity(tableName = "daily_activity")
data class DailyActivity(
    @PrimaryKey val date: String,
    val completedItems: Int
)
