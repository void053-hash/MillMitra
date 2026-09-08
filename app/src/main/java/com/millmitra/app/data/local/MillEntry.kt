package com.millmitra.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a saved transaction (either a clipboard translation or voice shift report)
 * in the MillMitra local Room database.
 */
@Entity(tableName = "mill_history")
data class MillEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "entry_type")
    val entryType: EntryType,

    @ColumnInfo(name = "input_text")
    val inputText: String,

    @ColumnInfo(name = "output_text")
    val outputText: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "detected_tags")
    val detectedTags: String? = null
)

enum class EntryType {
    TRANSLATE_DECODE, // English -> Conversational Hinglish (Preserving mill terms)
    DICTATE_REPORT    // Hinglish audio -> Simple direct English shift report
}
