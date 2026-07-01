# Gabarita AI 📚🤖

**Gabarita AI** é um aplicativo Android desenvolvido em Kotlin com Jetpack Compose, integrado nativamente com a API do **Google Gemini**. A principal proposta é ajudar estudantes (de concursos, vestibulares, entre outros) a criarem **planos de estudos personalizados** e realizarem **simulados interativos** com questões comentadas pela Inteligência Artificial.

## 🚀 Funcionalidades Principais

- **Criação de Planos de Estudos:** Envie o edital ou temas da sua prova e a IA organizará os cargos e um cronograma sugerido de tópicos a serem estudados.
- **Acompanhamento (To-Do List):** Marque tópicos como concluídos e acompanhe seu progresso de estudos diários.
- **Simulados Interativos:** Gere questões dinamicamente focadas nos temas do seu plano atual.
- **Correção Inteligente:** Receba a pontuação e os comentários detalhados da IA explicando os motivos das respostas certas e erradas.
- **Histórico Offline:** Todo o seu progresso, planos e resultados de simulados são salvos localmente.

## 🛠 Tecnologias Utilizadas

- **Linguagem:** Kotlin
- **UI & Navegação:** Jetpack Compose / Navigation Compose
- **Integração com IA:** Retrofit (conecta com a API do Gemini `GEMINI_API_KEY`)
- **Persistência de Dados:** Room Database
- **CI/CD:** GitHub Actions (pipeline para build automático do APK)

## ⚙️ Como executar localmente

**Pré-requisitos:** [Android Studio](https://developer.android.com/studio) instalado.

1. Clone o repositório ou extraia os arquivos do projeto.
2. Abra o **Android Studio** e selecione o diretório do projeto.
3. Aguarde o Gradle sincronizar e instalar as dependências.
4. Crie um arquivo chamado `.env` na raiz do projeto (ao lado do `.env.example`).
5. No arquivo `.env`, insira a sua chave da API do Gemini da seguinte forma:
   ```env
   GEMINI_API_KEY=sua_chave_de_api_aqui
   ```
   > **Nota:** Você pode gerar uma chave gratuitamente no [Google AI Studio](https://aistudio.google.com/app/apikey).
6. Rode o aplicativo no seu emulador ou dispositivo físico clicando no botão **Run** ▶️.

## 📦 Como gerar o APK (via GitHub Actions)

Este repositório já está automatizado para gerar o aplicativo para você.
1. Sempre que você fizer um commit (`push`) ou um `pull request` para a branch `main` ou `master`, o GitHub inciará a compilação.
2. Acesse a aba **Actions** na página do seu repositório.
3. Aguarde o workflow "Android Build" finalizar.
4. Role até a parte inferior do log e faça o download do **Gabarita-AI-Debug-APK** na seção de "Artifacts".
