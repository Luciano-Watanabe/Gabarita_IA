package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_plans")
data class StudyPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetExam: String,
    val examDate: String,
    val description: String,
    val rawSyllabus: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_topics")
data class StudyTopic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val name: String,
    val description: String,
    val priority: String, // "Alta", "Média", "Baixa"
    val completed: Boolean = false,
    val recommendedHours: Int = 10,
    val studyTips: String = ""
)

@Entity(tableName = "mock_exams")
data class MockExam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val examTitle: String,
    val createdAt: Long = System.currentTimeMillis(),
    val score: Int = 0,
    val totalQuestions: Int = 0,
    val isCompleted: Boolean = false
)

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mockId: Long,
    val text: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val optionE: String,
    val correctOption: String, // "A", "B", "C", "D", "E"
    val explanation: String,
    val userAnswer: String? = null // Nullable to track if answered
)
