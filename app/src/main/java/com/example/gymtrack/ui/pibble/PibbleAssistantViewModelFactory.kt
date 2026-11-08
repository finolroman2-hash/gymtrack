package com.example.gymtrack.ui.pibble

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PibbleAssistantViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass != PibbleAssistantViewModel::class.java) {
            error("Unknown ViewModel class")
        }
        return PibbleAssistantViewModel(application) as T
    }
}
