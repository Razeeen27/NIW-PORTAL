package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sequence_tracker")
data class SequenceTracker(
    @PrimaryKey
    val year: Int,
    val lastSequenceNumber: Int
)
