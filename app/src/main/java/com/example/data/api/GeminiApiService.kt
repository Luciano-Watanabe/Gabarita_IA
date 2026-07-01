package com.example.data.api

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST("upload/v1beta/files")
    suspend fun uploadFile(
        @Query("key") apiKey: String,
        @Header("X-Goog-Upload-Protocol") uploadProtocol: String = "raw",
        @Header("X-Goog-Upload-Header-Content-Length") contentLength: String,
        @Header("X-Goog-Upload-Header-Content-Type") contentType: String,
        @Body content: RequestBody
    ): UploadResponse
}
