package com.millmitra.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.millmitra.app.data.local.MillMitraDatabase
import com.millmitra.app.data.repository.MillRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter

class MillMitraApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { MillMitraDatabase.getDatabase(this) }
    val repository by lazy { MillRepository(database.millEntryDao()) }

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Install Global Crash Logger to diagnose any background or foreground crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()
                Log.e("MillMitraCrash", "CRASH in thread ${thread.name}: $stackTrace")
                prefs.edit().putString(KEY_LAST_CRASH, stackTrace).commit()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Automatically clean up records older than 7 days on startup
        applicationScope.launch {
            repository.pruneOldRecords()
        }
    }

    fun getApiKey(): String {
        val key = prefs.getString(KEY_GEMINI_API_KEY, "")
        return if (!key.isNullOrBlank()) key else BuildConfig.DEFAULT_GEMINI_API_KEY
    }

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }

    fun getLastCrash(): String? {
        return prefs.getString(KEY_LAST_CRASH, null)
    }

    fun clearLastCrash() {
        prefs.edit().remove(KEY_LAST_CRASH).apply()
    }

    companion object {
        lateinit var instance: MillMitraApp
            private set

        private const val PREFS_NAME = "millmitra_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_LAST_CRASH = "last_crash_trace"
    }
}
