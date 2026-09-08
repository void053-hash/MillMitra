package com.millmitra.app.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Request payload for Google Gemini REST API v1beta generateContent
 */
data class GeminiRequest(
    @SerializedName("contents")
    val contents: List<GeminiContent>,

    @SerializedName("systemInstruction")
    val systemInstruction: GeminiContent? = null,

    @SerializedName("generationConfig")
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    @SerializedName("parts")
    val parts: List<GeminiPart>,

    @SerializedName("role")
    val role: String? = "user"
)

data class GeminiPart(
    @SerializedName("text")
    val text: String
)

data class GeminiThinkingConfig(
    @SerializedName("thinkingLevel")
    val thinkingLevel: String = "minimal"
)

data class GeminiGenerationConfig(
    @SerializedName("temperature")
    val temperature: Float = 0.2f,

    @SerializedName("topK")
    val topK: Int = 40,

    @SerializedName("topP")
    val topP: Float = 0.95f,

    @SerializedName("maxOutputTokens")
    val maxOutputTokens: Int = 1024,

    @SerializedName("thinkingConfig")
    val thinkingConfig: GeminiThinkingConfig? = null
)

/**
 * Response payload from Google Gemini REST API v1beta generateContent
 */
data class GeminiResponse(
    @SerializedName("candidates")
    val candidates: List<GeminiCandidate>?,

    @SerializedName("usageMetadata")
    val usageMetadata: GeminiUsageMetadata?,

    @SerializedName("error")
    val error: GeminiApiError?
) {
    /**
     * Extracts concatenated text from all parts of the first candidate response.
     */
    fun extractText(): String? {
        val candidate = candidates?.firstOrNull()
        val parts = candidate?.content?.parts
        return parts?.joinToString(separator = "\n") { it.text }?.trim()
    }
}

data class GeminiCandidate(
    @SerializedName("content")
    val content: GeminiContent?,

    @SerializedName("finishReason")
    val finishReason: String?,

    @SerializedName("index")
    val index: Int?
)

data class GeminiUsageMetadata(
    @SerializedName("promptTokenCount")
    val promptTokenCount: Int?,
    @SerializedName("candidatesTokenCount")
    val candidatesTokenCount: Int?,
    @SerializedName("totalTokenCount")
    val totalTokenCount: Int?
)

data class GeminiApiError(
    @SerializedName("code")
    val code: Int?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: String?
)
