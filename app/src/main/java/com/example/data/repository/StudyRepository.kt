package com.example.data.repository

import com.example.data.database.*
import kotlinx.coroutines.flow.Flow

class StudyRepository(private val studyDao: StudyDao) {

    // --- Study Plans ---
    val allStudyPlans: Flow<List<StudyPlan>> = studyDao.getAllStudyPlans()

    suspend fun getStudyPlanById(id: Long): StudyPlan? = studyDao.getStudyPlanById(id)

    suspend fun saveStudyPlanWithTopics(plan: StudyPlan, topics: List<StudyTopic>): Long {
        val planId = studyDao.insertStudyPlan(plan)
        val topicsWithId = topics.map { it.copy(planId = planId) }
        studyDao.insertTopics(topicsWithId)
        return planId
    }

    suspend fun deleteStudyPlan(plan: StudyPlan) = studyDao.deleteStudyPlan(plan)

    // --- Study Topics ---
    fun getTopicsForPlan(planId: Long): Flow<List<StudyTopic>> = studyDao.getTopicsForPlan(planId)

    suspend fun updateTopicCompletion(topicId: Long, completed: Boolean) {
        studyDao.updateTopicCompletion(topicId, completed)
    }

    // --- Mock Exams ---
    val allMockExams: Flow<List<MockExam>> = studyDao.getAllMockExams()

    fun getMockExamsForPlan(planId: Long): Flow<List<MockExam>> = studyDao.getMockExamsForPlan(planId)

    suspend fun getMockExamById(id: Long): MockExam? = studyDao.getMockExamById(id)

    suspend fun saveMockExamWithQuestions(exam: MockExam, questions: List<Question>): Long {
        val examId = studyDao.insertMockExam(exam)
        val questionsWithId = questions.map { it.copy(mockId = examId) }
        studyDao.insertQuestions(questionsWithId)
        // Update exam total count
        val updatedExam = exam.copy(id = examId, totalQuestions = questions.size)
        studyDao.insertMockExam(updatedExam)
        return examId
    }

    suspend fun deleteMockExam(exam: MockExam) = studyDao.deleteMockExam(exam)

    // --- Questions ---
    fun getQuestionsForExam(mockId: Long): Flow<List<Question>> = studyDao.getQuestionsForExam(mockId)

    suspend fun updateQuestionAnswer(questionId: Long, userAnswer: String?) {
        studyDao.updateQuestionAnswer(questionId, userAnswer)
    }

    suspend fun finishMockExam(examId: Long, questions: List<Question>) {
        val score = questions.count { it.userAnswer == it.correctOption }
        studyDao.updateMockExamResult(examId, score, isCompleted = true)
    }
}
