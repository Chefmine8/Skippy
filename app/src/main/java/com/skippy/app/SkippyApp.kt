package com.skippy.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SkippyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notif.ensureChannels(this)
        val request = PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("check", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
