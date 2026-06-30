package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GeminiRequest
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.database.*
import com.example.data.repository.StudyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StudyRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = StudyRepository(database.studyDao())
    }

    // --- State Streams ---
    val studyPlans: StateFlow<List<StudyPlan>> = repository.allStudyPlans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mockExams: StateFlow<List<MockExam>> = repository.allMockExams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPlan = MutableStateFlow<StudyPlan?>(null)
    val selectedPlan: StateFlow<StudyPlan?> = _selectedPlan.asStateFlow()

    private val _selectedExam = MutableStateFlow<MockExam?>(null)
    val selectedExam: StateFlow<MockExam?> = _selectedExam.asStateFlow()

    private val _currentTopics = MutableStateFlow<List<StudyTopic>>(emptyList())
    val currentTopics: StateFlow<List<StudyTopic>> = _currentTopics.asStateFlow()

    private val _currentQuestions = MutableStateFlow<List<Question>>(emptyList())
    val currentQuestions: StateFlow<List<Question>> = _currentQuestions.asStateFlow()

    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Streaks & Stats
    val studyStreak: StateFlow<Int> = studyPlans.map { plans ->
        // Just a simple dynamic gamification metric based on plans/completed topics
        if (plans.isEmpty()) 0 else {
            var completedCount = 0
            plans.forEach { _ -> completedCount++ }
            completedCount * 3 + 1
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Selects a Study Plan and collects its topics in real-time
    fun selectStudyPlan(planId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            val plan = repository.getStudyPlanById(planId)
            _selectedPlan.value = plan
            if (plan != null) {
                repository.getTopicsForPlan(planId).collect { topics ->
                    _currentTopics.value = topics
                    _isLoading.value = false
                }
            } else {
                _isLoading.value = false
            }
        }
    }

    // Selects a Mock Exam and collects its questions in real-time
    fun selectMockExam(examId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            val exam = repository.getMockExamById(examId)
            _selectedExam.value = exam
            _currentQuestionIndex.value = 0
            if (exam != null) {
                repository.getQuestionsForExam(examId).collect { questions ->
                    _currentQuestions.value = questions
                    _isLoading.value = false
                }
            } else {
                _isLoading.value = false
            }
        }
    }

    // Toggle topic completion state
    fun toggleTopicCompletion(topicId: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.updateTopicCompletion(topicId, completed)
            // Trigger refresh if we have an active selected plan
            val plan = _selectedPlan.value
            if (plan != null) {
                repository.getTopicsForPlan(plan.id).take(1).collect { topics ->
                    _currentTopics.value = topics
                }
            }
        }
    }

    // Answer question inside active mock exam
    fun answerQuestion(questionId: Long, answer: String) {
        viewModelScope.launch {
            repository.updateQuestionAnswer(questionId, answer)
            // Refresh local questions list
            val currentExam = _selectedExam.value
            if (currentExam != null) {
                repository.getQuestionsForExam(currentExam.id).take(1).collect { questions ->
                    _currentQuestions.value = questions
                }
            }
        }
    }

    // Submit mock exam and calculate results
    fun submitMockExam(examId: Long, onFinished: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val questions = _currentQuestions.value
            repository.finishMockExam(examId, questions)
            // Refresh exam model
            val exam = repository.getMockExamById(examId)
            _selectedExam.value = exam
            _isLoading.value = false
            onFinished()
        }
    }

    // --- Study Plan Generation with Gemini API ---
    fun createStudyPlanFromInput(
        input: String,
        targetExam: String,
        examDate: String,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val prompt = """
                    Você é um especialista em concursos públicos no Brasil. Com base no seguinte edital, conteúdo programático ou link com dados de prova fornecido pelo usuário:
                    ---
                    $input
                    ---
                    Crie um plano de estudos altamente otimizado focado no concurso '$targetExam' (Data prevista da prova: $examDate).
                    O plano deve conter um título atrativo, uma descrição geral e uma lista de 5 a 8 tópicos prioritários.
                    Para cada tópico inclua:
                    - O nome do tópico (ex: "Direito Constitucional - Direitos Fundamentais").
                    - Uma descrição resumida do que estudar.
                    - Prioridade baseada na frequência do assunto em provas anteriores: "Alta", "Média" ou "Baixa".
                    - Tempo de estudo recomendado em horas (ex: 12).
                    - Dicas exclusivas de estudo ou mnemônicos rápidos de memorização para o tema.

                    O retorno deve ser ESTREITAMENTE em formato JSON válido, sem qualquer bloco markdown (NÃO inclua ```json ou similares), obedecendo fielmente esta estrutura:
                    {
                      "title": "Título estratégico do plano de estudos",
                      "description": "Uma visão geral focada nos pontos cruciais para passar na prova",
                      "topics": [
                        {
                          "name": "Nome do Tópico",
                          "description": "O que focar neste assunto",
                          "priority": "Alta",
                          "recommendedHours": 12,
                          "studyTips": "Dicas de memorização úteis"
                        }
                      ]
                    }
                """.trimIndent()

                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isEmpty()) {
                    throw IllegalStateException("API Key do Gemini não está configurada! Por favor, configure-a no painel Secrets.")
                }

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(
                        apiKey = apiKey,
                        request = GeminiRequest(
                            contents = listOf(Content(parts = listOf(Part(text = prompt))))
                        )
                    )
                }

                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw IllegalStateException("Gemini não retornou nenhuma resposta.")

                val cleanJson = cleanJsonResponse(responseText)
                Log.d("StudyViewModel", "Cleaned JSON Plan: $cleanJson")

                // Parse and save to database
                val planId = parseAndSaveStudyPlan(cleanJson, targetExam, examDate)
                _isLoading.value = false
                onSuccess(planId)
            } catch (e: Exception) {
                Log.e("StudyViewModel", "Erro ao criar plano", e)
                _errorMessage.value = e.localizedMessage ?: "Erro inesperado ao gerar plano de estudos."
                _isLoading.value = false
            }
        }
    }

    // --- Mock Exam Generation with Gemini API ---
    fun generateMockExamForPlan(planId: Long, onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val plan = repository.getStudyPlanById(planId) ?: throw IllegalArgumentException("Plano não encontrado.")
                
                // Fetch topics to give context
                val topics = _currentTopics.value.ifEmpty {
                    val deferredTopics = repository.getTopicsForPlan(planId).first()
                    _currentTopics.value = deferredTopics
                    deferredTopics
                }

                val topicsSummary = topics.joinToString("\n") { "- ${it.name} (Prioridade: ${it.priority})" }

                val prompt = """
                    Você é um experiente elaborador de provas de concursos públicos brasileiros.
                    Crie um simulado de aprendizado focado no concurso '${plan.targetExam}' contendo 5 questões inéditas ou adaptadas de provas reais de anos anteriores (de bancas como CESPE/Cebraspe, FGV, FCC, CESGRANRIO, etc.), totalmente baseadas nos seguintes tópicos de estudos previstos para a prova:
                    $topicsSummary

                    Cada questão DEVE ter 5 alternativas (A, B, C, D, E) com apenas uma correta.
                    Ao final de cada questão, inclua uma EXPLICACÃO DETALHADA e comentada de por que a resposta é a correta e por que as outras alternativas estão incorretas, facilitando a memorização e o aprendizado completo.

                    O retorno deve ser ESTREITAMENTE em formato JSON válido, sem qualquer bloco markdown (NÃO inclua ```json ou similares), obedecendo fielmente esta estrutura de array JSON:
                    [
                      {
                        "text": "Enunciado completo da questão, citando ano, banca e cargo se possível.",
                        "optionA": "Opção A",
                        "optionB": "Opção B",
                        "optionC": "Opção C",
                        "optionD": "Opção D",
                        "optionE": "Opção E",
                        "correctOption": "C",
                        "explanation": "Explicação extremamente detalhada e comentada de cada alternativa, explicando por que a correta é C e as outras estão incorretas."
                      }
                    ]
                """.trimIndent()

                val apiKey = BuildConfig.GEMINI_API_KEY
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(
                        apiKey = apiKey,
                        request = GeminiRequest(
                            contents = listOf(Content(parts = listOf(Part(text = prompt))))
                        )
                    )
                }

                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw IllegalStateException("Gemini não retornou nenhuma resposta para o simulado.")

                val cleanJson = cleanJsonResponse(responseText)
                Log.d("StudyViewModel", "Cleaned JSON Exam: $cleanJson")

                // Parse and save exam
                val examId = parseAndSaveMockExam(cleanJson, planId, plan.targetExam)
                _isLoading.value = false
                onSuccess(examId)
            } catch (e: Exception) {
                Log.e("StudyViewModel", "Erro ao criar simulado", e)
                _errorMessage.value = e.localizedMessage ?: "Erro inesperado ao gerar simulado."
                _isLoading.value = false
            }
        }
    }

    // --- Helpers to Clean and Parse ---
    private fun cleanJsonResponse(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json")
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        return clean.trim()
    }

    private suspend fun parseAndSaveStudyPlan(jsonStr: String, targetExam: String, examDate: String): Long {
        return withContext(Dispatchers.IO) {
            val jsonObject = JSONObject(jsonStr)
            val title = jsonObject.getString("title")
            val description = jsonObject.getString("description")

            val plan = StudyPlan(
                title = title,
                targetExam = targetExam,
                examDate = examDate,
                description = description,
                rawSyllabus = jsonStr
            )

            val topicsArray = jsonObject.getJSONArray("topics")
            val topicsList = mutableListOf<StudyTopic>()
            for (i in 0 until topicsArray.length()) {
                val topicObj = topicsArray.getJSONObject(i)
                topicsList.add(
                    StudyTopic(
                        planId = 0, // Will be replaced in repository save
                        name = topicObj.getString("name"),
                        description = topicObj.getString("description"),
                        priority = topicObj.getString("priority"),
                        recommendedHours = topicObj.optInt("recommendedHours", 10),
                        studyTips = topicObj.optString("studyTips", "")
                    )
                )
            }

            repository.saveStudyPlanWithTopics(plan, topicsList)
        }
    }

    private suspend fun parseAndSaveMockExam(jsonStr: String, planId: Long, examTitle: String): Long {
        return withContext(Dispatchers.IO) {
            val jsonArray = JSONArray(jsonStr)
            val exam = MockExam(
                planId = planId,
                examTitle = "Simulado: $examTitle",
                totalQuestions = jsonArray.length()
            )

            val questionsList = mutableListOf<Question>()
            for (i in 0 until jsonArray.length()) {
                val qObj = jsonArray.getJSONObject(i)
                questionsList.add(
                    Question(
                        mockId = 0, // Will be replaced in repository save
                        text = qObj.getString("text"),
                        optionA = qObj.getString("optionA"),
                        optionB = qObj.getString("optionB"),
                        optionC = qObj.getString("optionC"),
                        optionD = qObj.getString("optionD"),
                        optionE = qObj.getString("optionE"),
                        correctOption = qObj.getString("correctOption").uppercase().trim(),
                        explanation = qObj.getString("explanation")
                    )
                )
            }

            repository.saveMockExamWithQuestions(exam, questionsList)
        }
    }

    // --- Deletions ---
    fun deleteStudyPlan(plan: StudyPlan) {
        viewModelScope.launch {
            repository.deleteStudyPlan(plan)
            if (_selectedPlan.value?.id == plan.id) {
                _selectedPlan.value = null
                _currentTopics.value = emptyList()
            }
        }
    }

    fun deleteMockExam(exam: MockExam) {
        viewModelScope.launch {
            repository.deleteMockExam(exam)
            if (_selectedExam.value?.id == exam.id) {
                _selectedExam.value = null
                _currentQuestions.value = emptyList()
            }
        }
    }

    // Navigation and index management
    fun nextQuestion() {
        if (_currentQuestionIndex.value < _currentQuestions.value.size - 1) {
            _currentQuestionIndex.value += 1
        }
    }

    fun prevQuestion() {
        if (_currentQuestionIndex.value > 0) {
            _currentQuestionIndex.value -= 1
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
