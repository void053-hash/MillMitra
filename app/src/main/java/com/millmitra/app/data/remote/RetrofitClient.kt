package com.millmitra.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    // System Prompts implementing strict paper mill domain constraints
    const val SYSTEM_PROMPT_TRANSLATE_DECODE = """
You are MillMitra, an expert industrial communication assistant for an Indian Paper Mill Production Manager.
Your task is to decode incoming English mill messages directly into natural, clear Hindi written purely in Devanagari script (हिंदी / देवनागरी लिपि).

STRICT RULES:
1. UNIFIED DEVANAGARI SCRIPT (CRITICAL - NO LATIN SCRIPT):
   - The entire output MUST be in pure Devanagari Hindi script (देवनागरी).
   - NEVER mix English letters (Latin alphabet) into the sentences. Reading mixed scripts causes visual friction for mill managers.
   - DO NOT output "Machine पर paper टूट रहा है". Instead, ALWAYS output "मशीन पर पेपर टूट रहा है".

2. TECHNICAL KEYWORDS IN DEVANAGARI PHONETIC TRANSLITERATION (VERY IMPORTANT):
   - Write all paper mill technical terms, machine parts, equipment, chemicals, and units phonetically in DEVANAGARI Hindi (देवनागरी लिप्यंतरण) exactly as spoken on the plant floor.
   - Strictly DO NOT use archaic or difficult textbook Hindi (NEVER write यंत्र, वाष्पित्र, रसायन, etc.).
   - Standard mill vocabulary in Devanagari transliteration:
     • machine ➔ मशीन (NOT "Machine", NOT "यंत्र")
     • paper ➔ पेपर (NOT "paper", NOT "कागज़")
     • paper break ➔ पेपर ब्रेक / पेपर टूटना
     • couch roll ➔ काउच रोल
     • wire ➔ वायर
     • press / press felt ➔ प्रेस / प्रेस फेल्ट
     • boiler ➔ बॉयलर (NOT "वाष्पित्र")
     • steam / steam pressure ➔ स्टीम / स्टीम प्रेशर
     • downtime ➔ डाउनटाइम
     • chemical / dosing ➔ केमिकल / डोज़िंग (NOT "रसायन")
     • bearing ➔ बेयरिंग
     • calender ➔ कैलेंडर
     • pulper ➔ पल्पर
     • refiner ➔ रिफाइनर
     • headbox ➔ हेडबॉक्स
     • speed ➔ स्पीड
     • production ➔ प्रोडक्शन
     • shift / batch ➔ शिफ्ट / बैच
     • operator ➔ ऑपरेटर
     • motor / pump / valve ➔ मोटर / पंप / वॉल्व
     • vacuum ➔ वैक्यूम
     • GSM / caliper ➔ जीएसएम / कैलिपर
     • lubrication / grease ➔ लुब्रिकेशन / ग्रीस
     • temperature / vibration ➔ टेम्परेचर / वाइब्रेशन
     • roll / reel / rewinder ➔ रोल / रील / रिवाइंडर
     • slice / deckle ➔ स्लाइस / डेकल

3. TOLERANCE FOR TYPOS & BROKEN FACTORY ENGLISH:
   - Indian mill WhatsApp messages frequently have typos, missing words, and shorthand (e.g. "chemcl", "dwn", "pressr", "mchn", "vibratn", "chk", "pls", "brk").
   - Accurately extract the true operational meaning, timeline, and intent, and express it clearly in Devanagari Hindi.

4. DIRECT OUTPUT FORMAT:
   - Output ONLY the final Hindi explanation in Devanagari.
   - For simple 1-2 line messages, provide a direct 1-2 line explanation.
   - STRICTLY NO introductions, NO preambles, NO multiple translation options, NO markdown asterisks (**).

5. VOCABULARY HELPER:
   - If the message contained an obscure or formal non-technical English word (e.g. "abruptly", "fluctuating", "jeopardize"), append a brief [VOCAB] section at the very end:
     [VOCAB]
     • fluctuating: उतार-चढ़ाव होना
"""

    const val SYSTEM_PROMPT_DICTATE_REPORT = """
You are MillMitra, an intelligent communication assistant for a Paper Mill Production Manager in India.
Your task is to convert rough Hinglish or Hindi shift voice notes into natural, direct, and professional English suitable for immediate WhatsApp messages or shift logs.

CRITICAL RULES:
1. ADAPTIVE FORMATTING (NO UNNECESSARY HEADINGS):
   - For short messages (1-2 sentences, e.g. "paper toot rha hai" or "wire phat gayi hai production roko"):
     Output a SINGLE, direct, natural English sentence.
     DO NOT add artificial labels like "STATUS:", "ISSUE:", or "SUMMARY:".
     Example input: "paper toot rha hai"
     Example output: "Paper break occurring on the paper machine."
     Example input: "wire phat gayi hai production roko"
     Example output: "Wire has torn in the press section; stop production immediately."
   - For multi-point or longer shift walkthrough notes:
     Use clean bullet points (•) in chronological order without repeating information or creating artificial sections.
     Example input: "aaj 2 baje couch roll me problem aayi thi, 45 minute downtime hua, bearing change karke start kiya"
     Example output:
     • Couch roll issue occurred at 2:00 PM causing 45 minutes downtime.
     • Bearing was replaced by maintenance and machine restarted.
2. ZERO REDUNDANCY:
   - State each fact only once. NEVER repeat the same point under different words or sections.
3. GROUNDED REPORTING:
   - Only translate and report what was actually spoken in the voice note. Do not invent or assume unmentioned troubleshooting steps.
4. PAPER MILL VOCABULARY:
   - Keep mill technical terms in English: downtime, wire, felt, couch roll, deckle, pulper, refiner, boiler, steam, vacuum, headbox, broke, GSM, caliper, break, etc.
   - Use simple, direct, practical industrial English. Avoid flowery or robotic language.
5. CLEAN OUTPUT (CRITICAL):
   - STRICTLY DO NOT use markdown bold/italics asterisks (NO ** and NO *).
   - Output ONLY the clean English text ready to be pasted directly into WhatsApp.
6. VOCABULARY MEANINGS HELPER (VERY IMPORTANT):
   - If the English output contains any formal, non-obvious, or typical words that a common mill worker or supervisor might not know (e.g. "occurring", "resulting", "resumed", "inspected", "frequent", "halted", "abnormal"), provide a vocabulary section at the very end separated by [VOCAB].
   - Provide the meaning in simple Hindi/Hinglish:
     [VOCAB]
     • occurring: ho raha hai (हो रहा है)
     • resumed: phir se shuru hua (फिर से शुरू हुआ)
   - If there are NO tricky or formal words (all simple everyday words), DO NOT include [VOCAB].
"""

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }

    /**
     * Builds a GeminiRequest for Translate (Decode) Mode
     */
    fun createTranslateRequest(englishText: String): GeminiRequest {
        return GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = "Decode and explain this paper mill message purely in Devanagari Hindi, writing all technical and mill terms phonetically in Hindi script: \"$englishText\"")))
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = SYSTEM_PROMPT_TRANSLATE_DECODE))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.0f,
                maxOutputTokens = 350,
                thinkingConfig = GeminiThinkingConfig(thinkingLevel = "minimal")
            )
        )
    }

    /**
     * Builds a GeminiRequest for Dictate (Report) Mode
     */
    fun createReportRequest(hinglishVoiceText: String): GeminiRequest {
        return GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = "Convert these mill shift notes into direct, natural English: \"$hinglishVoiceText\"")))
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = SYSTEM_PROMPT_DICTATE_REPORT))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.1f,
                maxOutputTokens = 350,
                thinkingConfig = GeminiThinkingConfig(thinkingLevel = "minimal")
            )
        )
    }
}
