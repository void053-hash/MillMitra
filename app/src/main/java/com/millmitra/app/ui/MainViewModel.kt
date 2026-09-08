package com.millmitra.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.millmitra.app.data.local.MillEntry
import com.millmitra.app.data.repository.MillRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: MillRepository
) : ViewModel() {

    // Emits entries strictly within the 7-day retention cutoff
    val historyList: StateFlow<List<MillEntry>> = repository.get7DayHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteEntryById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    fun pruneOldHistory() {
        viewModelScope.launch {
            repository.pruneOldRecords()
        }
    }
}

class MainViewModelFactory(
    private val repository: MillRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
