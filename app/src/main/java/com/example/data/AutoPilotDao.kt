package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoPilotDao {
  @Query("SELECT * FROM ad_decisions ORDER BY timestamp DESC LIMIT 200")
  fun observeDecisions(): Flow<List<AdDecisionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDecision(decision: AdDecisionEntity): Long

  @Query("UPDATE ad_decisions SET userConfirmed = :confirmed WHERE id = :decisionId")
  suspend fun updateDecisionConfirmation(decisionId: Long, confirmed: Boolean)

  @Query("SELECT * FROM ad_decisions WHERE id = :decisionId LIMIT 1")
  suspend fun getDecisionById(decisionId: Long): AdDecisionEntity?

  @Query("DELETE FROM ad_decisions")
  suspend fun clearDecisions()

  @Query("SELECT * FROM learned_patterns ORDER BY lastUpdated DESC")
  fun observeLearnedPatterns(): Flow<List<LearnedPatternEntity>>

  @Query("SELECT * FROM learned_patterns")
  suspend fun getAllLearnedPatternsSnapshot(): List<LearnedPatternEntity>

  @Query(
    "SELECT * FROM learned_patterns WHERE normalizedText = :text AND resourceIdPattern = :resId AND regionBucket = :region LIMIT 1"
  )
  suspend fun findMatchingPattern(
    text: String,
    resId: String,
    region: String,
  ): LearnedPatternEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPattern(pattern: LearnedPatternEntity)

  @Query("DELETE FROM learned_patterns WHERE id = :patternId")
  suspend fun deletePatternById(patternId: Long)

  @Query("DELETE FROM learned_patterns")
  suspend fun clearPatterns()
}
