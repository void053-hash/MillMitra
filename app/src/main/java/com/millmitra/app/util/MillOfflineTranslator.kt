package com.millmitra.app.util

import java.util.Locale

/**
 * Offline Paper Mill Rule & Dictionary Engine.
 * Provides instant fallback translations for Indian paper mill operations
 * when cellular/Wi-Fi signal drops in basement, pulper, or boiler sections.
 */
object MillOfflineTranslator {

    // Technical keywords to strictly preserve in English
    val PRESERVED_KEYWORDS = setOf(
        "machine", "chemical", "dosing", "wire", "felt", "couch roll", "press",
        "calender", "pulper", "refiner", "headbox", "boiler", "steam", "pressure",
        "vacuum", "bearing", "motor", "valve", "pump", "reel", "rewinder", "deckle",
        "gsm", "caliper", "downtime", "break", "broke", "speed", "production", "shift",
        "batch", "operator", "slice", "lubrication", "temperature", "tear", "burst",
        "moisture", "ph", "freeness", "effluent", "drainage"
    )

    /**
     * Offline Decode: English WhatsApp messages -> Simple Hindi with mill terms preserved in English.
     */
    fun decodeEnglishToHindiOffline(englishText: String): String {
        val lower = englishText.lowercase(Locale.ENGLISH).trim()

        // 1. Common Operational Templates
        val result = when {
            lower.contains("paper break") || lower.contains("break on machine") -> {
                "पेपर मशीन पर पेपर ब्रेक हो रहा है। कृपया वायर और काउच रोल सेक्शन चेक करें।"
            }
            lower.contains("wire") && (lower.contains("torn") || lower.contains("damage") || lower.contains("cut")) -> {
                "प्रेस सेक्शन में वायर डैमेज / फट गई है। तत्काल प्रोडक्शन रोककर चेक करें।"
            }
            lower.contains("felt") && (lower.contains("dirty") || lower.contains("blind") || lower.contains("choke")) -> {
                "फेल्ट चोक / गंदी हो गई है। हाई प्रेशर शॉवर और केमिकल वॉश चालू करें।"
            }
            lower.contains("steam") && (lower.contains("low") || lower.contains("drop") || lower.contains("pressure")) -> {
                "बॉयलर से स्टीम प्रेशर ड्रॉप हो गया है। ड्राइंग सिलेंडर्स का टेम्परेचर चेक करें।"
            }
            lower.contains("dosing") || (lower.contains("chemical") && lower.contains("pump")) -> {
                "केमिकल डोज़िंग पंप में समस्या है। फ्लो रेट और स्ट्रोक चेक करें।"
            }
            lower.contains("bearing") && (lower.contains("hot") || lower.contains("temp") || lower.contains("noise")) -> {
                "बेयरिंग ओवर-हीट हो रही है और वाइब्रेशन आ रही है। लुब्रिकेशन तुरंत चेक करें।"
            }
            lower.contains("pulper") && (lower.contains("jam") || lower.contains("overload")) -> {
                "पल्पर ओवरलोड / जाम हो गया है। फीडिंग तुरंत रोकें।"
            }
            lower.contains("couch roll") -> {
                "काउच रोल सेक्शन में समस्या आई है। वैक्यूम और अलाइनमेंट चेक करें।"
            }
            lower.contains("downtime") -> {
                "मशीन पर डाउनटाइम चल रहा है। मेंटेनेंस कार्य जारी है।"
            }
            else -> {
                // Heuristic translation transliterating technical terms into Devanagari
                translateGenericEnglishToHindi(englishText)
            }
        }

        return "$result\n\n[ऑफलाइन मोड: मिल डिक्शनरी]"
    }

    /**
     * Offline Dictate: Hinglish/Hindi shift notes -> Clean English shift report.
     */
    fun dictateHinglishToEnglishOffline(hinglishText: String): String {
        val lower = hinglishText.lowercase(Locale.ENGLISH).trim()

        val result = when {
            lower.contains("paper toot") || lower.contains("paper tut") || lower.contains("break hua") -> {
                "Paper break occurring on the paper machine."
            }
            lower.contains("wire phat") || lower.contains("wire cut") || lower.contains("wire damage") -> {
                "Wire damaged in the press section; machine stopped for replacement."
            }
            lower.contains("felt") && (lower.contains("kharab") || lower.contains("choke") || lower.contains("gandi")) -> {
                "Felt blinding observed; chemical washing initiated."
            }
            lower.contains("steam") && (lower.contains("kam") || lower.contains("drop") || lower.contains("down")) -> {
                "Boiler steam pressure dropped, affecting dryer cylinder temperature."
            }
            lower.contains("bearing") && (lower.contains("garam") || lower.contains("awaj") || lower.contains("noise")) -> {
                "Abnormal heating and vibration detected on bearing; maintenance notified."
            }
            lower.contains("chemical") && (lower.contains("kam") || lower.contains("dosing") || lower.contains("band")) -> {
                "Chemical dosing pump malfunctioned; flow restored manually."
            }
            lower.contains("downtime") || lower.contains("ruk gaya") || lower.contains("band kiya") -> {
                "Machine downtime recorded for operational maintenance."
            }
            lower.contains("couch roll") -> {
                "Couch roll vacuum issue reported and inspected by shift team."
            }
            lower.contains("pulper") -> {
                "Pulper consistency issue addressed; batch resumed."
            }
            else -> {
                // Capitalize and format spoken text as a shift observation
                "Shift log: $hinglishText"
            }
        }

        return "$result\n\n[OFFLINE MODE: Mill Dictionary]"
    }

    private fun translateGenericEnglishToHindi(text: String): String {
        var processed = text
        // Replace mill terms and common helper words with Devanagari script
        val dictionary = mapOf(
            // Mill terms phonetic transliteration
            "machine" to "मशीन",
            "paper" to "पेपर",
            "wire" to "वायर",
            "felt" to "फेल्ट",
            "press" to "प्रेस",
            "couch roll" to "काउच रोल",
            "couch" to "काउच",
            "roll" to "रोल",
            "boiler" to "बॉयलर",
            "steam" to "स्टीम",
            "pressure" to "प्रेशर",
            "downtime" to "डाउनटाइम",
            "chemical" to "केमिकल",
            "dosing" to "डोज़िंग",
            "pump" to "पंप",
            "motor" to "मोटर",
            "bearing" to "बेयरिंग",
            "valve" to "वॉल्व",
            "pulper" to "पल्पर",
            "refiner" to "रिफाइनर",
            "calender" to "कैलेंडर",
            "speed" to "स्पीड",
            "production" to "प्रोडक्शन",
            "shift" to "शिफ्ट",
            "operator" to "ऑपरेटर",
            "break" to "ब्रेक",
            "vacuum" to "वैक्यूम",
            "temperature" to "टेम्परेचर",
            "temp" to "टेम्परेचर",
            "vibration" to "वाइब्रेशन",
            "noise" to "नॉइज़",
            "gsm" to "जीएसएम",
            "caliper" to "कैलिपर",
            "deckle" to "डेकल",
            "rewinder" to "रिवाइंडर",
            // Helper operational words
            "problem" to "समस्या",
            "issue" to "दिक्कत",
            "check" to "चेक करें",
            "please" to "कृपया",
            "immediately" to "तुरंत",
            "urgent" to "जरूरी",
            "stopped" to "रुक गई है",
            "running" to "चल रही है",
            "slow" to "धीरे",
            "high" to "ज्यादा",
            "low" to "कम",
            "replaced" to "बदल दिया गया",
            "started" to "चालू किया गया",
            "clean" to "साफ करें",
            "leakage" to "लीकेज"
        )

        for ((en, hi) in dictionary) {
            val regex = Regex("(?i)\\b$en\\b")
            processed = processed.replace(regex, hi)
        }

        return processed
    }
}
