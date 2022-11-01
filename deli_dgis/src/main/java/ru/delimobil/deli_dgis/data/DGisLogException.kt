package ru.delimobil.deli_dgis.data

import ru.dgis.sdk.LogMessage

data class DGisLogException(
    private val logMessage: LogMessage
) : RuntimeException(logMessage.text) {
    override fun getStackTrace(): Array<StackTraceElement> {
        return arrayOf(
            StackTraceElement("", "", logMessage.file, logMessage.line)
        )
    }
}