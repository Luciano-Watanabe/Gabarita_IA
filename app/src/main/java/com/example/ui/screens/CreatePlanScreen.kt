package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LightAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSubmit: (String, String, String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var targetExam by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf("") }
    var rawInput by remember { mutableStateOf("") }

    // Popular templates for quick selection/fill
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
                title = { Text("Novo Plano de Estudos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                            text = "Escolha um dos modelos prontos abaixo para preencher os campos rapidamente ou digite as informações do seu edital.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                // Templates row
                Text(
                    text = "Modelos Populares Disponíveis:",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
                            subtitle = "Cargo Técnico",
                            onClick = {
                                targetExam = "${temp.name} - ${temp.cargo}"
                                examDate = temp.date
                                rawInput = temp.content
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input fields
                OutlinedTextField(
                    value = targetExam,
                    onValueChange = { targetExam = it },
                    label = { Text("Nome do Concurso / Órgão") },
                    placeholder = { Text("Ex: Banco do Brasil - Escriturário") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("target_exam_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = examDate,
                    onValueChange = { examDate = it },
                    label = { Text("Data Prevista da Prova") },
                    placeholder = { Text("Ex: 24/05/2026") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_date_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("Conteúdo do Edital, Link da Prova ou Informações") },
                    placeholder = { Text("Cole aqui as disciplinas do edital, um link com as informações da prova ou o conteúdo que você deseja focar nos seus estudos...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .testTag("syllabus_input"),
                    shape = RoundedCornerShape(16.dp)
                )

                // Error message
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

                // Action Button
                Button(
                    onClick = {
                        if (targetExam.isNotBlank() && rawInput.isNotBlank()) {
                            val finalDate = examDate.ifBlank { "A definir" }
                            onSubmit(rawInput, targetExam, finalDate)
                        }
                    },
                    enabled = targetExam.isNotBlank() && rawInput.isNotBlank() && !isLoading,
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
                        text = if (isLoading) "Analisando Edital com IA..." else "Gerar Plano de Estudos por IA",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))
            }

            // Loading Overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
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
                                text = "Gabarita AI está analisando...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aguarde. Nossa IA está estruturando as prioridades, estimando as horas de dedicação e criando dicas exclusivas com mnemônicos baseados nas provas anteriores.",
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

data class TemplateData(
    val name: String,
    val cargo: String,
    val date: String,
    val content: String
)
