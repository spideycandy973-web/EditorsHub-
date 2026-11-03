package com.editorshub

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EditorsHubApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * WorkManager may initialise from a content provider before Hilt finishes
     * injecting the Application. In that case fall back to WorkManager's own
     * default factory instead of throwing UninitializedPropertyAccessException.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .let { builder ->
                if (::workerFactory.isInitialized) builder.setWorkerFactory(workerFactory)
                else builder
            }
            .build()
}
