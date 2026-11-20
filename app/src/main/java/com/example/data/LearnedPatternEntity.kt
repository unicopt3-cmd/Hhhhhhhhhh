package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learned_patterns")
data class LearnedPatternEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val normalizedText: String,
  val resourceIdPattern: String,
  val regionBucket: String,
  val contextPackage: String,
  val isSafeClose: Boolean,
  val confirmationCount: Int = 1,
  val lastUpdated: Long = System.currentTimeMillis(),
)
