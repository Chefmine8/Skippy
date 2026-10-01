package com.skippy.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

/**
 * Runs every ~15 minutes (Android's minimum for periodic work):
 *  1. refreshes the timetable from Zeus if the last sync is older than 6 h (token renewed silently by MSAL);
 *  2. sends a "were you there?" notification for classes that ended in the last 6 h and are not answered yet
 *     (this part only needs the stored sessions, so it keeps working if the token has expired).
 */
class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): ListenableWorker.Result {
        val repo = Repo.get(applicationContext)
        val settings = repo.settings()
        if (settings.groupId <= 0 || settings.authMode.isEmpty()) return ListenableWorker.Result.success()

        val now = System.currentTimeMillis()
        if (now - settings.lastSync > 6 * HOUR) repo.sync(full = settings.lastFull == 0L)
        if (!settings.notifications) return ListenableWorker.Result.success()

        val att = repo.attendance()
        val notified = repo.notified()
        repo.sessions()
            .filter { it.end <= now && it.end > now - 6 * HOUR && att[it.uid] == null && it.uid !in notified }
            .take(5)
            .forEach {
                Notif.ask(applicationContext, it)
                repo.markNotified(it.uid)
            }
        return ListenableWorker.Result.success()
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
