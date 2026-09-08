package com.millmitra.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApiService {

    /**
     * Generates content using Google Gemini REST API.
     * Supports dynamic model specification for graceful fallback (gemini-2.5-flash, etc.).
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String = "gemini-flash-lite-latest",
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
