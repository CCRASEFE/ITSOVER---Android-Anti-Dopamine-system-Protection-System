package com.example.itsover02.telegram

enum class ControlState {
    AVAILABLE,
    ACTIVE_SESSION,
    COOLDOWN
}

data class TelegramPreferences(
    val telegramPackage: String,
    val controlState: ControlState,
    val sessionStartElapsed: Long,
    val cooldownExpiresWall: Long,
    val cooldownExpiresElapsed: Long,
    val bootId: String,
    val isSessionActive: Boolean
)