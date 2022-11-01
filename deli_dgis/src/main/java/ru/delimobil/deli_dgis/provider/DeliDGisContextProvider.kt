package ru.delimobil.deli_dgis.provider

import android.content.Context
import ru.delimobil.deli_dgis.alias.DGisContext
import ru.delimobil.deli_dgis.data.DGisLogException
import ru.dgis.sdk.*

class DeliDGisContextProvider(
    private val appContext: Context,
    private val crashlytics: Crashlytics
) {

    private val dGisContext: DGisContext by lazy {
        DGis.initialize(
            appContext = appContext,
            dataCollectConsent = PersonalDataCollectionConsent.GRANTED,
            logOptions = LogOptions(
                customLevel = LogLevel.ERROR,
                customSink = object : LogSink {
                    override fun write(message: LogMessage) {
                        crashlytics.logError(
                            DGisLogException(message)
                        )
                    }
                }
            )
        )
    }

    fun getContext() = dGisContext
}