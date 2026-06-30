package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LightAccent

data class TemplateData(
    val name: String,
    val cargo: String,
    val date: String,
    val content: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanScreen(
    isLoading: Boolean,
    isAnalyzingCargos: Boolean,
    suggestedCargos: List<String>,
    errorMessage: String?,
    onBack: () -> Unit,
    onClearError: () -> Unit,
    onClearCargos: () -> Unit,
    onAnalyzeContest: (targetExam: String, sourceInput: String, sourceType: String, pdfUri: Uri?, pdfName: String?, onCargosFound: () -> Unit) -> Unit,
    onSubmitPlan: (targetExam: String, cargo: String, examDate: String, sourceInput: String, sourceType: String, pdfName: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var targetExam by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf("") }
    var rawInput by remember { mutableStateOf("") }
    var linkInput by remember { mutableStateOf("") }

    // PDF picker state
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfName by remember { mutableStateOf<String?>(null) }
    var pdfSize by remember { mutableStateOf<String?>(null) }

    var sourceType by remember { mutableStateOf("TEXT") } // "TEXT", "LINK", "PDF"

    // Multi-stage flow state: "INPUT" or "CARGO_SELECTION"
    var currentStage by remember { mutableStateOf("INPUT") }
    var selectedCargo by remember { mutableStateOf("") }
    var customCargoInput by remember { mutableStateOf("") }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        pdfUri = uri
        if (uri != null) {
            var name = "Documento_Edital.pdf"
            var size = "Desconhecido"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) name = cursor.getString(nameIndex)
                        if (sizeIndex != -1) {
                            val bytes = cursor.getLong(sizeIndex)
                            size = if (bytes > 1024 * 1024) {
                                String.format("%.2f MB", bytes.toFloat() / (1024 * 1024))
                            } else {
                                String.format("%.2f KB", bytes.toFloat() / 1024)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            pdfName = name
            pdfSize = size
            if (targetExam.isBlank()) {
                val cleanName = name.replace(".pdf", "", ignoreCase = true)
                    .replace("_", " ")
                    .replace("-", " ")
                    .trim()
                targetExam = cleanName
            }
        }
    }

    val handleBack = {
        if (currentStage == "CARGO_SELECTION") {
            currentStage = "INPUT"
            selectedCargo = ""
            customCargoInput = ""
            onClearCargos()
        } else {
            onBack()
        }
    }

    val templates = listOf(
        TemplateData(
            name = "Banco do Brasil",
            cargo = "Escriturário (Agente de Tecnologia)",
            date = "25/10/2026",
            content = "Língua Portuguesa (compreensão de textos, gramática), Matemática Financeira (juros, descontos, rendas), Noções de TI (banco de dados, python, redes, segurança da informação), Atendimento Bancário (vendas, ética, CDC), Conhecimentos Bancários (sistema financeiro nacional, garantias, taxas de juros, COPOM)."
        ),
        TemplateData(
            name = "INSS",
            cargo = "Técnico do Seguro Social",
            date = "15/11/2026",
            content = "Direito Previdenciário (Seguridade Social, Regime Geral de Previdência Social, Benefícios, Financiamento da Seguridade Social), Direito Administrativo (Atos administrativos, servidores públicos, licitações lei 14.133), Direito Constitucional (Direitos e garantias fundamentais, Administração Pública), Língua Portuguesa, Informática Básica."
        ),
        TemplateData(
            name = "Correios",
            cargo = "Agente de Correios - Carteiro",
            date = "06/09/2026",
            content = "Língua Portuguesa (ortografia, concordância, interpretação), Matemática (porcentagem, frações, geometria básica, raciocínio lógico), Conhecimentos Gerais sobre a empresa Correios, Noções de Informática (Windows, Internet, pacote Office)."
        ),
        TemplateData(
            name = "Receita Federal",
            cargo = "Analista Tributário",
            date = "14/02/2027",
            content = "Direito Tributário (sistema tributário nacional, impostos federais, obrigações), Contabilidade Geral (balanço patrimonial, DRE, escrituração), Direito Constitucional e Administrativo, Língua Portuguesa, Raciocínio Lógico-Matemático, Língua Inglesa."
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentStage == "CARGO_SELECTION") "Escolha de Cargo / Vaga" else "Novo Plano de Estudos",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                if (currentStage == "INPUT") {
                    // Explanatory card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Carregue o edital em PDF, cole um link ou o conteúdo programático. Nossa Inteligência Artificial irá mapear as vagas e estruturar seu cronograma personalizado.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Input basic fields
                    OutlinedTextField(
                        value = targetExam,
                        onValueChange = { targetExam = it },
                        label = { Text("Nome do Concurso / Órgão") },
                        placeholder = { Text("Ex: INSS, Banco do Brasil, TRE-SP...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("target_exam_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )

                    OutlinedTextField(
                        value = examDate,
                        onValueChange = { examDate = it },
                        label = { Text("Data Prevista da Prova (Opcional)") },
                        placeholder = { Text("Ex: 15/11/2026") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exam_date_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )

                    // Source Selection
                    Text(
                        text = "Selecione a Origem dos Dados da Prova:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceTypeChip(
                            title = "Texto/Edital",
                            icon = Icons.Default.Description,
                            selected = sourceType == "TEXT",
                            onClick = { sourceType = "TEXT" },
                            modifier = Modifier.weight(1f)
                        )
                        SourceTypeChip(
                            title = "Link do Edital",
                            icon = Icons.Default.Link,
                            selected = sourceType == "LINK",
                            onClick = { sourceType = "LINK" },
                            modifier = Modifier.weight(1f)
                        )
                        SourceTypeChip(
                            title = "Arquivo PDF",
                            icon = Icons.Default.UploadFile,
                            selected = sourceType == "PDF",
                            onClick = { sourceType = "PDF" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Contextual Inputs
                    when (sourceType) {
                        "TEXT" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(
                                    value = rawInput,
                                    onValueChange = { rawInput = it },
                                    label = { Text("Conteúdo Programático ou Disciplinas") },
                                    placeholder = { Text("Cole aqui as disciplinas do edital ou o conteúdo que você deseja focar...") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .testTag("syllabus_input"),
                                    shape = RoundedCornerShape(16.dp)
                                )

                                // Templates
                                Text(
                                    text = "Modelos Populares para Preencher Rápido:",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    templates.take(2).forEach { temp ->
                                        TemplateChip(
                                            title = temp.name,
                                            subtitle = "Técnico/Agente",
                                            onClick = {
                                                targetExam = "${temp.name} - ${temp.cargo}"
                                                examDate = temp.date
                                                rawInput = temp.content
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    templates.drop(2).take(2).forEach { temp ->
                                        TemplateChip(
                                            title = temp.name,
                                            subtitle = "Analista",
                                            onClick = {
                                                targetExam = "${temp.name} - ${temp.cargo}"
                                                examDate = temp.date
                                                rawInput = temp.content
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                        "LINK" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = linkInput,
                                    onValueChange = { linkInput = it },
                                    label = { Text("Link / URL do Concurso ou Edital") },
                                    placeholder = { Text("Ex: https://www.pciconcursos.com.br/concurso/inss") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("link_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp)
                                )

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            targetExam = "IBFC - Edital 01/2026"
                                            examDate = "15/12/2026"
                                            linkInput = "https://servidor-arquivos.ibfc.org.br/arquivos-publicos/edital-01-2026-ibfc-11.06-final-marcadores.pdf"
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Testar com Edital IBFC 2026 (PDF Link)",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Toque para preencher automaticamente com o link de exemplo da IBFC.",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "PDF" -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clickable { pdfPickerLauncher.launch("application/pdf") },
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (pdfUri == null) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(40.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Selecionar PDF do Edital",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Suporta arquivos locais .pdf",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = pdfName ?: "Edital Selecionado",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Tamanho: ${pdfSize ?: "Concluído"}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    pdfUri = null
                                                    pdfName = null
                                                    pdfSize = null
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remover arquivo",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Submission error display
                    if (errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Ocorreu um erro",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = onClearError,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Fechar Alerta")
                                }
                            }
                        }
                    }

                    // Action Button Stage 1
                    val isStage1Ready = targetExam.isNotBlank() && (
                        (sourceType == "TEXT" && rawInput.isNotBlank()) ||
                        (sourceType == "LINK" && linkInput.isNotBlank()) ||
                        (sourceType == "PDF" && pdfUri != null)
                    )

                    Button(
                        onClick = {
                            val finalSource = when (sourceType) {
                                "TEXT" -> rawInput
                                "LINK" -> linkInput
                                "PDF" -> pdfName ?: "Arquivo PDF"
                                else -> rawInput
                            }
                            onAnalyzeContest(targetExam, finalSource, sourceType, pdfUri, pdfName) {
                                currentStage = "CARGO_SELECTION"
                            }
                        },
                        enabled = isStage1Ready && !isLoading && !isAnalyzingCargos,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("analyze_contest_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Analisar Concurso & Achar Vagas",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                } else if (currentStage == "CARGO_SELECTION") {
                    // Explanatory Cargo Selection
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Work,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Vagas Identificadas pela IA!",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Mapeamos os seguintes cargos para o concurso '${targetExam}'. Selecione para qual deseja prestar a prova:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Suggested Cargos Card List
                    suggestedCargos.forEach { cargo ->
                        val isSelected = selectedCargo == cargo && customCargoInput.isBlank()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCargo = cargo
                                    customCargoInput = ""
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Work,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = cargo,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedCargo = cargo
                                        customCargoInput = ""
                                    }
                                )
                            }
                        }
                    }

                    // Custom input field
                    OutlinedTextField(
                        value = customCargoInput,
                        onValueChange = {
                            customCargoInput = it
                            if (it.isNotBlank()) selectedCargo = ""
                        },
                        label = { Text("Outro cargo (Personalizado)") },
                        placeholder = { Text("Ex: Auditor de Controle, Professor, etc.") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_cargo_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Error display if any
                    if (errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Ocorreu um erro",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = onClearError,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Fechar Alerta")
                                }
                            }
                        }
                    }

                    // Action Button Stage 2
                    val isStage2Ready = selectedCargo.isNotBlank() || customCargoInput.isNotBlank()

                    Button(
                        onClick = {
                            val finalCargo = if (customCargoInput.isNotBlank()) customCargoInput else selectedCargo
                            val finalDate = examDate.ifBlank { "A definir" }
                            val finalSource = when (sourceType) {
                                "TEXT" -> rawInput
                                "LINK" -> linkInput
                                "PDF" -> pdfName ?: "Arquivo PDF"
                                else -> rawInput
                            }
                            onSubmitPlan(targetExam, finalCargo, finalDate, finalSource, sourceType, pdfName)
                        },
                        enabled = isStage2Ready && !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("generate_plan_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gerar Roteiro Estratégico Completo",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }

            // Stage 1 (Analyzing Cargos) Loading Overlay
            if (isAnalyzingCargos) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Mapeando o Edital...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                              )
                              Spacer(modifier = Modifier.height(8.dp))
                              Text(
                                  text = "Buscando vagas, cargos e disposições iniciais. Aguarde enquanto a IA organiza as informações encontradas.",
                                  style = MaterialTheme.typography.bodyMedium,
                                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                  textAlign = TextAlign.Center
                              )
                          }
                      }
                  }
              }

              // Stage 2 (Generating Plan) Loading Overlay
              if (isLoading) {
                  Box(
                      modifier = Modifier
                          .fillMaxSize()
                          .background(Color.Black.copy(alpha = 0.55f))
                          .clickable(enabled = false) {},
                      contentAlignment = Alignment.Center
                  ) {
                      Card(
                          modifier = Modifier
                              .fillMaxWidth(0.85f)
                              .padding(16.dp),
                          shape = RoundedCornerShape(20.dp),
                          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                      ) {
                          Column(
                              modifier = Modifier.padding(24.dp),
                              horizontalAlignment = Alignment.CenterHorizontally,
                              verticalArrangement = Arrangement.Center
                          ) {
                              CircularProgressIndicator(
                                  color = MaterialTheme.colorScheme.primary,
                                  modifier = Modifier.size(56.dp)
                              )
                              Spacer(modifier = Modifier.height(20.dp))
                              Text(
                                  text = "Gabarita AI está gerando...",
                                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                  color = MaterialTheme.colorScheme.onSurface,
                                  textAlign = TextAlign.Center
                              )
                              Spacer(modifier = Modifier.height(8.dp))
                              Text(
                                  text = "Criando os tópicos com base no conteúdo programático do cargo selecionado, estimando as horas ideais e gerando mnemônicos e dicas exclusivas.",
                                  style = MaterialTheme.typography.bodyMedium,
                                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                  textAlign = TextAlign.Center
                              )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourceTypeChip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Card(
        modifier = modifier
            .height(50.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TemplateChip(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(64.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
