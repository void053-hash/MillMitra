package com.millmitra.app.data.repository

import com.millmitra.app.data.local.EntryType
import com.millmitra.app.data.local.MillEntry
import com.millmitra.app.data.local.MillEntryDao
import com.millmitra.app.data.local.MillMitraDatabase
import com.millmitra.app.data.remote.GeminiApiService
import com.millmitra.app.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

import com.millmitra.app.data.remote.GeminiRequest
import org.json.JSONObject
import java.io.IOException

class MillRepository(
    private val millEntryDao: MillEntryDao,
    private val apiService: GeminiApiService = RetrofitClient.apiService
) {

    // Primary active modern models verified with user's key (Flash Lite for sub-second responses)
    private val candidateModels = listOf(
        "gemini-flash-lite-latest",
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-3.6-flash"
    )

    /**
     * Translates English text to Conversational Hinglish, keeping mill terms in English.
     */
    suspend fun translateEnglishToHinglish(englishText: String, apiKey: String): Result<String> {
        val request = RetrofitClient.createTranslateRequest(englishText)
        return executeWithFallback(
            apiKey = apiKey,
            request = request,
            entryType = EntryType.TRANSLATE_DECODE,
            inputText = englishText
        )
    }

    /**
     * Converts rough Hinglish speech to a simple, direct English shift report.
     */
    suspend fun convertSpeechToReport(hinglishVoiceText: String, apiKey: String): Result<String> {
        val request = RetrofitClient.createReportRequest(hinglishVoiceText)
        return executeWithFallback(
            apiKey = apiKey,
            request = request,
            entryType = EntryType.DICTATE_REPORT,
            inputText = hinglishVoiceText
        )
    }

    /**
     * Executes Gemini API request with automatic model fallback cascade
     * and human-friendly error messages.
     */
    private suspend fun executeWithFallback(
        apiKey: String,
        request: GeminiRequest,
        entryType: EntryType,
        inputText: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            var lastErrorMessage = "Failed to communicate with AI server."

            for (model in candidateModels) {
                try {
                    val response = apiService.generateContent(
                        model = model,
                        apiKey = apiKey,
                        request = request
                    )

                    if (response.isSuccessful && response.body() != null) {
                        val text = response.body()?.extractText()
                        if (!text.isNullOrBlank()) {
                            val parsed = com.millmitra.app.util.TextSanitizer.splitReportAndVocab(text)
                            // Automatically record to 7-day local history (save the clean report)
                            saveHistoryEntry(
                                entryType = entryType,
                                inputText = inputText,
                                outputText = parsed.reportText
                            )
                            return@withContext Result.success(text)
                        } else {
                            lastErrorMessage = "AI returned an empty response."
                        }
                    } else {
                        val code = response.code()
                        val rawError = response.errorBody()?.string()
                        lastErrorMessage = parseUserFriendlyErrorMessage(code, rawError)

                        // If model is 404 (not found) or 500/502/503/504 (server temporary overload), try next model in cascade
                        if (code == 404 || code == 500 || code == 502 || code == 503 || code == 504) {
                            android.util.Log.w("MillMitra", "Model $model returned $code, trying fallback...")
                            continue
                        }

                        // For auth / invalid key errors or rate limits, don't retry with other models
                        if (code == 400 || code == 401 || code == 403 || code == 429) {
                            return@withContext Result.failure(Exception(lastErrorMessage))
                        }
                    }
                } catch (e: IOException) {
                    lastErrorMessage = "Network error: Please check your internet connection and try again."
                } catch (e: Exception) {
                    lastErrorMessage = e.localizedMessage ?: "Unexpected error during AI processing."
                }
            }

            // Offline Resilience: If network is unreachable, provide offline paper mill dictionary fallback!
            try {
                val offlineFallback = when (entryType) {
                    EntryType.TRANSLATE_DECODE -> com.millmitra.app.util.MillOfflineTranslator.decodeEnglishToHindiOffline(inputText)
                    EntryType.DICTATE_REPORT -> com.millmitra.app.util.MillOfflineTranslator.dictateHinglishToEnglishOffline(inputText)
                }
                saveHistoryEntry(
                    entryType = entryType,
                    inputText = inputText,
                    outputText = offlineFallback
                )
                return@withContext Result.success(offlineFallback)
            } catch (e: Exception) {
                Result.failure(Exception(lastErrorMessage))
            }
        }
    }

    /**
     * Parses raw API error responses into clear, actionable, user-friendly messages.
     */
    private fun parseUserFriendlyErrorMessage(code: Int, rawBody: String?): String {
        var apiMessage = ""
        if (!rawBody.isNullOrBlank()) {
            try {
                val json = JSONObject(rawBody)
                if (json.has("error")) {
                    val errObj = json.getJSONObject("error")
                    apiMessage = errObj.optString("message", "")
                }
            } catch (e: Exception) {
                // Ignore JSON parse errors
            }
        }

        return when (code) {
            400 -> {
                if (apiMessage.contains("API_KEY_INVALID", ignoreCase = true) || 
                    apiMessage.contains("key not valid", ignoreCase = true)) {
                    "Invalid Gemini API Key. Please verify your API key in MillMitra settings."
                } else if (apiMessage.isNotBlank()) {
                    "Request Error: $apiMessage"
                } else {
                    "Request format was not accepted by the AI service."
                }
            }
            401, 403 -> "API Key unauthorized or expired. Please enter a valid Gemini API key in MillMitra settings."
            404 -> "AI model temporarily unavailable. Please try again."
            429 -> "Gemini API rate limit exceeded. Please wait a moment and try again."
            500, 503 -> "Google Gemini service is momentarily busy. Please try again in a few seconds."
            else -> if (apiMessage.isNotBlank()) apiMessage else "Server error ($code). Please try again."
        }
    }

    /**
     * Stores entry in local Room database and triggers an auto-pruning of records older than 7 days.
     */
    suspend fun saveHistoryEntry(entryType: EntryType, inputText: String, outputText: String): Long {
        return withContext(Dispatchers.IO) {
            // Prune records older than 7 days first
            val cutoff = MillMitraDatabase.getSevenDayCutoffTimestamp()
            millEntryDao.pruneOldRecords(cutoff)

            val entry = MillEntry(
                entryType = entryType,
                inputText = inputText,
                outputText = outputText,
                timestamp = System.currentTimeMillis()
            )
            millEntryDao.insertEntry(entry)
        }
    }

    /**
     * Flow of history records strictly within the 7-day retention period.
     */
    fun get7DayHistory(): Flow<List<MillEntry>> {
        val cutoff = MillMitraDatabase.getSevenDayCutoffTimestamp()
        return millEntryDao.getEntriesWithinLast7Days(cutoff)
    }

    /**
     * Manual trigger to prune records older than 7 days.
     */
    suspend fun pruneOldRecords(): Int {
        return withContext(Dispatchers.IO) {
            val cutoff = MillMitraDatabase.getSevenDayCutoffTimestamp()
            millEntryDao.pruneOldRecords(cutoff)
        }
    }

    suspend fun deleteEntryById(id: Long) {
        withContext(Dispatchers.IO) {
            millEntryDao.deleteEntryById(id)
        }
    }

    suspend fun clearAllHistory() {
        withContext(Dispatchers.IO) {
            millEntryDao.clearAllHistory()
        }
    }
}
