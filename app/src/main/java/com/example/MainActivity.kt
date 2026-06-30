package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StudyViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val viewModel: StudyViewModel = viewModel()
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "onboarding"
        ) {
            // 1. Onboarding Screen
            composable("onboarding") {
                OnboardingScreen(
                    onNavigateToHome = {
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            // 2. Main Dashboard (Home) Screen
            composable("home") {
                val studyPlans by viewModel.studyPlans.collectAsStateWithLifecycle()
                val mockExams by viewModel.mockExams.collectAsStateWithLifecycle()
                val streak by viewModel.studyStreak.collectAsStateWithLifecycle()

                HomeScreen(
                    studyPlans = studyPlans,
                    mockExams = mockExams,
                    streak = streak,
                    onNavigateToCreatePlan = { navController.navigate("create_plan") },
                    onNavigateToPlanDetail = { planId ->
                        viewModel.selectStudyPlan(planId)
                        navController.navigate("plan_detail/$planId")
                    },
                    onNavigateToExamReview = { examId ->
                        viewModel.selectMockExam(examId)
                        navController.navigate("exam_review/$examId")
                    },
                    onDeletePlan = { plan -> viewModel.deleteStudyPlan(plan) },
                    onDeleteExam = { exam -> viewModel.deleteMockExam(exam) }
                )
            }

            // 3. Create Study Plan Screen
            composable("create_plan") {
                val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
                val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

                CreatePlanScreen(
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onBack = { navController.popBackStack() },
                    onClearError = { viewModel.clearError() },
                    onSubmit = { rawInput, targetExam, examDate ->
                        viewModel.createStudyPlanFromInput(rawInput, targetExam, examDate) { planId ->
                            viewModel.selectStudyPlan(planId)
                            navController.navigate("plan_detail/$planId") {
                                popUpTo("home")
                            }
                        }
                    }
                )
            }

            // 4. Study Plan Detail Screen
            composable(
                route = "plan_detail/{planId}",
                arguments = listOf(navArgument("planId") { type = NavType.LongType })
            ) { backStackEntry ->
                val planId = backStackEntry.arguments?.getLong("planId") ?: 0L
                val selectedPlan by viewModel.selectedPlan.collectAsStateWithLifecycle()
                val topics by viewModel.currentTopics.collectAsStateWithLifecycle()
                val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
                val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

                PlanDetailScreen(
                    plan = selectedPlan,
                    topics = topics,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onBack = { navController.popBackStack() },
                    onToggleTopic = { topicId, completed -> viewModel.toggleTopicCompletion(topicId, completed) },
                    onClearError = { viewModel.clearError() },
                    onGenerateMock = {
                        viewModel.generateMockExamForPlan(planId) { examId ->
                            viewModel.selectMockExam(examId)
                            navController.navigate("mock_exam/$examId")
                        }
                    }
                )
            }

            // 5. Interactive Mock Exam Screen
            composable(
                route = "mock_exam/{examId}",
                arguments = listOf(navArgument("examId") { type = NavType.LongType })
            ) {
                val selectedExam by viewModel.selectedExam.collectAsStateWithLifecycle()
                val questions by viewModel.currentQuestions.collectAsStateWithLifecycle()
                val currentIndex by viewModel.currentQuestionIndex.collectAsStateWithLifecycle()

                MockExamScreen(
                    exam = selectedExam,
                    questions = questions,
                    currentIndex = currentIndex,
                    onBack = { navController.popBackStack() },
                    onAnswerSelected = { qId, ans -> viewModel.answerQuestion(qId, ans) },
                    onNext = { viewModel.nextQuestion() },
                    onPrev = { viewModel.prevQuestion() },
                    onSubmit = {
                        val examId = selectedExam?.id ?: 0L
                        viewModel.submitMockExam(examId) {
                            navController.navigate("exam_review/$examId") {
                                popUpTo("plan_detail/{planId}") { inclusive = false }
                            }
                        }
                    }
                )
            }

            // 6. Scorecard & Review Screen
            composable(
                route = "exam_review/{examId}",
                arguments = listOf(navArgument("examId") { type = NavType.LongType })
            ) {
                val selectedExam by viewModel.selectedExam.collectAsStateWithLifecycle()
                val questions by viewModel.currentQuestions.collectAsStateWithLifecycle()

                HistoryScreen(
                    exam = selectedExam,
                    questions = questions,
                    onBack = { navController.popBackStack() }
                )
            }
        }
      }
    }
  }
}
