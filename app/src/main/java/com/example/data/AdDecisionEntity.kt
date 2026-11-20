package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ad_decisions")
data class AdDecisionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long = System.currentTimeMillis(),
  val gamePackage: String,
  val adContext: String,
  val detectedText: String,
  val elementId: String,
  val source: String,
  val regionBucket: String,
  val boundsCompact: String,
  val confidenceScore: Float,
  val decisionType: String,
  val reason: String,
  val signalDetails: String,
  val userConfirmed: Boolean? = null,
)
