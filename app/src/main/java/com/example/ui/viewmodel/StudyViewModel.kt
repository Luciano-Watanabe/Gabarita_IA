package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
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
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StudyRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = StudyRepository(database.studyDao())
        
        // Inicializa o PDFBoxResourceLoader em segundo plano para aquecer a biblioteca de fontes
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(application)
                Log.d("StudyViewModel", "Successfully pre-initialized PDFBoxResourceLoader asynchronously in init")
            } catch (e: Throwable) {
                Log.e("StudyViewModel", "Failed to pre-initialize PDFBoxResourceLoader", e)
            }
        }
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

    private val _suggestedCargos = MutableStateFlow<List<String>>(emptyList())
    val suggestedCargos: StateFlow<List<String>> = _suggestedCargos.asStateFlow()

    private val _isAnalyzingCargos = MutableStateFlow(false)
    val isAnalyzingCargos: StateFlow<Boolean> = _isAnalyzingCargos.asStateFlow()

    private val _extractedSyllabusText = MutableStateFlow("")
    val extractedSyllabusText: StateFlow<String> = _extractedSyllabusText.asStateFlow()

    fun clearSuggestedCargos() {
        _suggestedCargos.value = emptyList()
    }

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

    // --- Two-Stage Interactive Contest Analysis (Cargo & Syllabus Discovery) ---
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )

            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        } catch (e: Exception) {
            OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    private fun stripHtml(html: String): String {
        val sb = java.lang.StringBuilder(html.length)
        var inTag = false
        var lastWasSpace = false
        for (char in html) {
            if (char == '<') {
                inTag = true
            } else if (char == '>') {
                inTag = false
                if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } else if (!inTag) {
                if (char.isWhitespace()) {
                    if (!lastWasSpace) {
                        sb.append(' ')
                        lastWasSpace = true
                    }
                } else {
                    sb.append(char)
                    lastWasSpace = false
                }
            }
        }
        return sb.toString().trim()
    }

    private suspend fun generateContentWithRetry(
        apiKey: String,
        initialSnippetSize: Int = 25000,
        buildPrompt: (Int) -> String
    ): com.example.data.api.GeminiResponse = withContext(Dispatchers.IO) {
        var currentSnippetSize = initialSnippetSize
        var attempt = 1
        var lastException: Throwable? = null
        
        while (attempt <= 4) {
            try {
                Log.d("StudyViewModel", "Tentativa $attempt: chamando Gemini com tamanho de recorte = $currentSnippetSize...")
                val prompt = buildPrompt(currentSnippetSize)
                
                val response = RetrofitClient.service.generateContent(
                    apiKey = apiKey,
                    request = GeminiRequest(
                        contents = listOf(Content(parts = listOf(Part(text = prompt))))
                    )
                )
                
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!text.isNullOrBlank()) {
                    Log.d("StudyViewModel", "Sucesso na tentativa $attempt com tamanho $currentSnippetSize!")
                    return@withContext response
                } else {
                    throw IllegalStateException("Resposta do Gemini vazia ou inválida.")
                }
            } catch (e: Throwable) {
                lastException = e
                Log.e("StudyViewModel", "Falha na tentativa $attempt com tamanho $currentSnippetSize. Erro: ${e.message}", e)
                
                attempt++
                if (attempt <= 4) {
                    currentSnippetSize = when (attempt) {
                        2 -> 15000
                        3 -> 5000
                        4 -> 0
                        else -> 0
                    }
                    kotlinx.coroutines.delay(1000L * (attempt - 1))
                }
            }
        }
        throw lastException ?: IllegalStateException("Erro ao chamar o Gemini após várias tentativas.")
    }

    private suspend fun extractSyllabusText(
        sourceInput: String,
        sourceType: String,
        pdfUri: Uri?
    ): String = withContext(Dispatchers.IO) {
        try {
            when (sourceType) {
                "TEXT" -> sourceInput
                "LINK" -> {
                    var url = sourceInput.trim()
                    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                        url = "https://$url"
                    }
                    Log.d("StudyViewModel", "Downloading content from URL: $url")
                    
                    val okHttpClient = getUnsafeOkHttpClient()
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "application/pdf,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        .build()
                        
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Erro ao fazer download do link: HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("Conteúdo do link vazio")
                    val bytes = body.bytes()
                    
                    // PDF Magic bytes check (%PDF)
                    val isPdfMagic = bytes.size >= 4 && 
                                     bytes[0] == '%'.code.toByte() && 
                                     bytes[1] == 'P'.code.toByte() && 
                                     bytes[2] == 'D'.code.toByte() && 
                                     bytes[3] == 'F'.code.toByte()
                                     
                    val isPdf = isPdfMagic || 
                                 url.contains(".pdf", ignoreCase = true) || 
                                 response.header("Content-Type")?.lowercase()?.contains("pdf") == true
                    
                    if (isPdf) {
                        Log.d("StudyViewModel", "Detected PDF from link (isPdfMagic=$isPdfMagic). Parsing with PDFBox...")
                        extractTextFromPdfBytes(bytes)
                    } else {
                        Log.d("StudyViewModel", "Detected web page HTML. Cleaning tags with safe linear parser...")
                        val html = String(bytes, Charsets.UTF_8)
                        stripHtml(html)
                    }
                }
                "PDF" -> {
                    if (pdfUri == null) throw IllegalStateException("Arquivo PDF não foi selecionado")
                    Log.d("StudyViewModel", "Reading local PDF URI: $pdfUri")
                    val context: Application = getApplication()
                    val inputStream = context.contentResolver.openInputStream(pdfUri)
                        ?: throw IllegalStateException("Não foi possível abrir o arquivo PDF selecionado")
                    val bytes = inputStream.use { it.readBytes() }
                    extractTextFromPdfBytes(bytes)
                }
                else -> sourceInput
            }
        } catch (e: Throwable) {
            Log.e("StudyViewModel", "Erro ao extrair conteúdo programático", e)
            throw e
        }
    }

    private fun extractTextFromPdfBytes(bytes: ByteArray, maxPages: Int = 30): String {
        try {
            val app: Application = getApplication()
            PDFBoxResourceLoader.init(app)
            Log.d("StudyViewModel", "Successfully initialized PDFBoxResourceLoader!")
        } catch (e: Throwable) {
            Log.e("StudyViewModel", "Failed to initialize PDFBoxResourceLoader", e)
        }
        return try {
            PDDocument.load(bytes).use { document ->
                val totalPages = document.numberOfPages
                val stripper = PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = maxPages.coerceAtMost(totalPages)
                val text = stripper.getText(document)
                Log.d("StudyViewModel", "Successfully extracted ${text.length} chars from PDF ($totalPages pages total)")
                text
            }
        } catch (e: Throwable) {
            Log.e("StudyViewModel", "Falha ao analisar estrutura do PDF com PDFBox", e)
            val msg = if (e is OutOfMemoryError) {
                "O arquivo PDF é muito grande para ser processado no celular. Tente copiar e colar o texto dele diretamente."
            } else {
                "Falha ao analisar a estrutura do arquivo PDF. Certifique-se de que é um documento válido."
            }
            throw IllegalStateException(msg)
        }
    }

    fun analyzeContestToFindCargos(
        targetExam: String,
        sourceInput: String,
        sourceType: String,
        pdfUri: Uri?,
        pdfName: String?,
        onCargosFound: () -> Unit
    ) {
        viewModelScope.launch {
            _isAnalyzingCargos.value = true
            _errorMessage.value = null
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isEmpty()) {
                    throw IllegalStateException("API Key do Gemini não está configurada! Por favor, configure-a no painel Secrets.")
                }

                // Extract text programmatically from the PDF, Webpage, or Text
                val extractedText = extractSyllabusText(sourceInput, sourceType, pdfUri)
                _extractedSyllabusText.value = extractedText

                val response = generateContentWithRetry(
                    apiKey = apiKey,
                    initialSnippetSize = 25000,
                    buildPrompt = { size ->
                        val textSnippet = if (extractedText.isNotEmpty()) {
                            if (size > 0) {
                                if (extractedText.length > size) extractedText.take(size) else extractedText
                            } else {
                                ""
                            }
                        } else {
                            ""
                        }
                        
                        val editalPart = if (textSnippet.isNotEmpty()) {
                            """
                            --- INÍCIO DOS DADOS DO EDITAL ---
                            $textSnippet
                            --- FIM DOS DADOS DO EDITAL ---
                            """.trimIndent()
                        } else {
                            "Não há dados específicos de edital disponíveis (use seu conhecimento geral)."
                        }

                        """
                        Você é um especialista em concursos públicos brasileiros. Com base no concurso '$targetExam' e nos seguintes dados reais extraídos do edital:
                        
                        $editalPart
                        
                        Identifique as vagas, cargos ou áreas de atuação oficialmente disponíveis neste concurso listadas no edital acima.
                        Selecione de 4 a 8 cargos/vagas principais ou mais relevantes listados nesses dados. Se o texto acima estiver muito incompleto ou for genérico, use o conhecimento prévio de provas reais desse concurso para complementar com os cargos reais.
                        
                        Retorne ESTREITAMENTE um array JSON contendo esses cargos reais encontrados, ordenados por relevância. Não inclua blocos markdown (NÃO inclua ```json ou similares), apenas a estrutura do array JSON válido.
                        Exemplo de retorno esperado:
                        [
                          "Técnico Judiciário - Área Administrativa",
                          "Analista Judiciário - Tecnologia da Informação"
                        ]
                        """.trimIndent()
                    }
                )

                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw IllegalStateException("Gemini não retornou nenhuma resposta para as vagas do concurso.")

                val cleanJson = cleanJsonResponse(responseText)
                Log.d("StudyViewModel", "Cleaned JSON Cargos: $cleanJson")

                val jsonArray = JSONArray(cleanJson)
                val cargosList = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    cargosList.add(jsonArray.getString(i))
                }

                if (cargosList.isEmpty()) {
                    throw IllegalStateException("Nenhuma vaga pôde ser extraída automaticamente. Tente digitar o cargo desejado.")
                }

                _suggestedCargos.value = cargosList
                _isAnalyzingCargos.value = false
                onCargosFound()
            } catch (e: Throwable) {
                Log.e("StudyViewModel", "Erro ao analisar cargos", e)
                val msg = if (e is OutOfMemoryError) {
                    "Limite de memória do aparelho excedido com este PDF. Tente copiar e colar apenas o texto do edital."
                } else {
                    e.localizedMessage ?: "Erro inesperado ao analisar o concurso."
                }
                _errorMessage.value = msg
                _isAnalyzingCargos.value = false
            }
        }
    }

    fun createStudyPlanForCargo(
        targetExam: String,
        cargo: String,
        examDate: String,
        sourceInput: String,
        sourceType: String,
        pdfName: String?,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isEmpty()) {
                    throw IllegalStateException("API Key do Gemini não está configurada! Por favor, configure-a no painel Secrets.")
                }

                val fullSyllabus = _extractedSyllabusText.value

                val response = generateContentWithRetry(
                    apiKey = apiKey,
                    initialSnippetSize = 25000,
                    buildPrompt = { size ->
                        val textSnippet = if (fullSyllabus.isNotEmpty()) {
                            if (size > 0) {
                                if (fullSyllabus.length > size) fullSyllabus.take(size) else fullSyllabus
                            } else {
                                ""
                            }
                        } else {
                            if (size > 0 && sourceInput.isNotEmpty()) {
                                if (sourceInput.length > size) sourceInput.take(size) else sourceInput
                            } else {
                                ""
                            }
                        }
                        
                        val editalPart = if (textSnippet.isNotEmpty()) {
                            """
                            --- INÍCIO DO EDITAL ---
                            $textSnippet
                            --- FIM DO EDITAL ---
                            """.trimIndent()
                        } else {
                            "Não há dados de edital detalhado disponíveis (use seu conhecimento para o cargo)."
                        }

                        """
                        Você é um especialista em concursos públicos no Brasil. Com base no concurso '$targetExam' para a vaga/cargo específico de '$cargo', e considerando as informações disponíveis:
                        
                        $editalPart
                        
                        Crie um plano de estudos altamente otimizado focado especificamente na vaga/cargo '$cargo' para o concurso '$targetExam' (Data prevista da prova: $examDate).
                        Com base no conteúdo programático oficial contido no edital acima para este cargo específico '$cargo', elabore um roteiro de estudos prático com 6 a 8 tópicos prioritários. Se o edital não contiver o conteúdo para este cargo, simule de forma extremamente realista com base nas matérias mais cobradas pela banca para este cargo específico '$cargo'.
                        O plano deve conter um título estratégico curto, uma descrição focada no perfil do cargo e uma lista de 6 a 8 tópicos prioritários.
                        Para cada tópico inclua:
                        - O nome do tópico (ex: "Noções de Direito Constitucional" ou "Informática - Banco de Dados").
                        - Uma descrição resumida do que estudar e focar.
                        - Prioridade baseada na recorrência em provas para este cargo específico: "Alta", "Média" ou "Baixa".
                        - Tempo de estudo recomendado em horas.
                        - Dicas exclusivas de estudo ou mnemônicos rápidos de memorização úteis para este tema.

                        O retorno deve ser ESTREITAMENTE em formato JSON válido, sem qualquer bloco markdown (NÃO inclua ```json ou similares), obedecendo fielmente esta estrutura:
                        {
                          "title": "Plano Estratégico - $cargo",
                          "description": "Análise focada nos conteúdos cruciais para passar na vaga de $cargo do concurso $targetExam",
                          "topics": [
                            {
                              "name": "Nome do Tópico",
                              "description": "Foco principal do estudo",
                              "priority": "Alta",
                              "recommendedHours": 15,
                              "studyTips": "Mnemônico de memorização..."
                            }
                          ]
                        }
                        """.trimIndent()
                    }
                )

                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw IllegalStateException("Gemini não retornou nenhuma resposta para o plano de estudos.")

                val cleanJson = cleanJsonResponse(responseText)
                Log.d("StudyViewModel", "Cleaned JSON Plan for Cargo: $cleanJson")

                // Parse and save to database
                val planId = parseAndSaveStudyPlan(cleanJson, "$targetExam - $cargo", examDate)
                _isLoading.value = false
                onSuccess(planId)
            } catch (e: Throwable) {
                Log.e("StudyViewModel", "Erro ao criar plano para o cargo", e)
                val msg = if (e is OutOfMemoryError) {
                    "Limite de memória do aparelho excedido. Tente usar apenas o texto do edital."
                } else {
                    e.localizedMessage ?: "Erro inesperado ao gerar plano de estudos."
                }
                _errorMessage.value = msg
                _isLoading.value = false
            }
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
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isEmpty()) {
                    throw IllegalStateException("API Key do Gemini não está configurada! Por favor, configure-a no painel Secrets.")
                }

                val response = generateContentWithRetry(
                    apiKey = apiKey,
                    initialSnippetSize = 25000,
                    buildPrompt = { size ->
                        val textSnippet = if (input.isNotEmpty()) {
                            if (size > 0) {
                                if (input.length > size) input.take(size) else input
                            } else {
                                ""
                            }
                        } else {
                            ""
                        }
                        
                        val editalPart = if (textSnippet.isNotEmpty()) {
                            """
                            --- INÍCIO DOS DADOS ---
                            $textSnippet
                            --- FIM DOS DADOS ---
                            """.trimIndent()
                        } else {
                            "Não há dados específicos detalhados (use seu conhecimento geral de concursos)."
                        }

                        """
                        Você é um especialista em concursos públicos no Brasil. Com base no seguinte edital, conteúdo programático ou link com dados de prova fornecido pelo usuário:
                        
                        $editalPart
                        
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
                    }
                )

                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw IllegalStateException("Gemini não retornou nenhuma resposta.")

                val cleanJson = cleanJsonResponse(responseText)
                Log.d("StudyViewModel", "Cleaned JSON Plan: $cleanJson")

                // Parse and save to database
                val planId = parseAndSaveStudyPlan(cleanJson, targetExam, examDate)
                _isLoading.value = false
                onSuccess(planId)
            } catch (e: Throwable) {
                Log.e("StudyViewModel", "Erro ao criar plano", e)
                val msg = if (e is OutOfMemoryError) {
                    "Limite de memória excedido. Tente usar uma entrada menor ou apenas o texto do edital."
                } else {
                    e.localizedMessage ?: "Erro inesperado ao gerar plano de estudos."
                }
                _errorMessage.value = msg
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

                val apiKey = BuildConfig.GEMINI_API_KEY

                val response = generateContentWithRetry(
                    apiKey = apiKey,
                    initialSnippetSize = 5000,
                    buildPrompt = {
                        """
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
                    }
                )

                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw IllegalStateException("Gemini não retornou nenhuma resposta para o simulado.")

                val cleanJson = cleanJsonResponse(responseText)
                Log.d("StudyViewModel", "Cleaned JSON Exam: $cleanJson")

                // Parse and save exam
                val examId = parseAndSaveMockExam(cleanJson, planId, plan.targetExam)
                _isLoading.value = false
                onSuccess(examId)
            } catch (e: Throwable) {
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
