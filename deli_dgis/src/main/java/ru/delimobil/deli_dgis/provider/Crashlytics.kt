package ru.delimobil.deli_dgis.provider

interface Crashlytics {
    fun setUserId(userId: String?)
    fun logError(error: Throwable)
}