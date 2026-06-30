package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    // --- Study Plans ---
    @Query("SELECT * FROM study_plans ORDER BY createdAt DESC")
    fun getAllStudyPlans(): Flow<List<StudyPlan>>

    @Query("SELECT * FROM study_plans WHERE id = :id")
    suspend fun getStudyPlanById(id: Long): StudyPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyPlan(plan: StudyPlan): Long

    @Delete
    suspend fun deleteStudyPlan(plan: StudyPlan)

    // --- Study Topics ---
    @Query("SELECT * FROM study_topics WHERE planId = :planId ORDER BY CASE priority WHEN 'Alta' THEN 1 WHEN 'Média' THEN 2 WHEN 'Baixa' THEN 3 ELSE 4 END")
    fun getTopicsForPlan(planId: Long): Flow<List<StudyTopic>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<StudyTopic>)

    @Query("UPDATE study_topics SET completed = :completed WHERE id = :topicId")
    suspend fun updateTopicCompletion(topicId: Long, completed: Boolean)

    // --- Mock Exams ---
    @Query("SELECT * FROM mock_exams ORDER BY createdAt DESC")
    fun getAllMockExams(): Flow<List<MockExam>>

    @Query("SELECT * FROM mock_exams WHERE planId = :planId ORDER BY createdAt DESC")
    fun getMockExamsForPlan(planId: Long): Flow<List<MockExam>>

    @Query("SELECT * FROM mock_exams WHERE id = :id")
    suspend fun getMockExamById(id: Long): MockExam?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMockExam(exam: MockExam): Long

    @Query("UPDATE mock_exams SET score = :score, isCompleted = :isCompleted WHERE id = :examId")
    suspend fun updateMockExamResult(examId: Long, score: Int, isCompleted: Boolean)

    @Delete
    suspend fun deleteMockExam(exam: MockExam)

    // --- Questions ---
    @Query("SELECT * FROM questions WHERE mockId = :mockId ORDER BY id ASC")
    fun getQuestionsForExam(mockId: Long): Flow<List<Question>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>)

    @Query("UPDATE questions SET userAnswer = :userAnswer WHERE id = :questionId")
    suspend fun updateQuestionAnswer(questionId: Long, userAnswer: String?)
}
