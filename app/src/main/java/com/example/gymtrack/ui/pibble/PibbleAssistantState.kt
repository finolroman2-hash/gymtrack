package com.example.gymtrack.ui.pibble

import com.example.gymtrack.assistant.PibbleNavAction

sealed class PibbleAssistantState {
    data object Idle : PibbleAssistantState()
    data object Thinking : PibbleAssistantState()
    data class Success(
        val text: String,
        val navAction: PibbleNavAction? = null,
    ) : PibbleAssistantState()
    data class Error(val message: String) : PibbleAssistantState()
}
