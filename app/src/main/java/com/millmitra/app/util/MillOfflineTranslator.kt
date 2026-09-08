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
                "Paper machine पर paper break हो रहा है। कृपया wire और couch roll section चेक करें।"
            }
            lower.contains("wire") && (lower.contains("torn") || lower.contains("damage") || lower.contains("cut")) -> {
                "Press section में wire damage / phat गई है। तत्काल production रोककर चेक करें।"
            }
            lower.contains("felt") && (lower.contains("dirty") || lower.contains("blind") || lower.contains("choke")) -> {
                "Felt choke / dirty हो गई है। high pressure shower और chemical wash चालू करें।"
            }
            lower.contains("steam") && (lower.contains("low") || lower.contains("drop") || lower.contains("pressure")) -> {
                "Boiler से steam pressure drop हो गया है। drying cylinders का temperature चेक करें।"
            }
            lower.contains("dosing") || (lower.contains("chemical") && lower.contains("pump")) -> {
                "Chemical dosing pump में समस्या है। flow rate और stroke चेक करें।"
            }
            lower.contains("bearing") && (lower.contains("hot") || lower.contains("temp") || lower.contains("noise")) -> {
                "Bearing over-heat हो रही है और noise आ रही है। lubrication और vibration तुरंत चेक करें।"
            }
            lower.contains("pulper") && (lower.contains("jam") || lower.contains("overload")) -> {
                "Pulper overload / jam हो गया है। feeding तुरंत रोकें।"
            }
            lower.contains("couch roll") -> {
                "Couch roll section में समस्या आई है। vacuum और alignment चेक करें।"
            }
            lower.contains("downtime") -> {
                "Machine पर downtime चल रहा है। maintenance कार्य जारी है।"
            }
            else -> {
                // Heuristic translation keeping technical terms in English
                translateGenericEnglishToHindi(englishText)
            }
        }

        return "$result\n\n[OFFLINE MODE: Mill Dictionary]"
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
        // Replace common helper words while preserving mill keywords
        val dictionary = mapOf(
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

        return "Mill Message: $processed"
    }
}
