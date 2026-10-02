package com.skippy.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notif {
    private const val CH_ASK = "ask"
    private const val CH_ALERT = "alert"
    const val ACTION_MARK = "com.skippy.app.MARK"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ASK, ctx.getString(R.string.channel_ask), NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, ctx.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun warnText(ctx: Context, st: SubjectStats, pct: Int): String =
        if (st.margin < 0) ctx.getString(R.string.warn_below, st.title)
        else ctx.resources.getQuantityString(R.plurals.warn_left, st.margin, st.title, st.margin)

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** "Were you at X?" with Present / Absent / Excused buttons. */
    @SuppressLint("MissingPermission")
    fun ask(ctx: Context, s: Session) {
        if (!canPost(ctx)) return
        fun action(st: Status, label: Int): Triple<Int, CharSequence, PendingIntent> {
            val i = Intent(ctx, AttendanceReceiver::class.java)
                .setAction(ACTION_MARK)
                .putExtra("uid", s.uid)
                .putExtra("st", st.code)
            val pi = PendingIntent.getBroadcast(
                ctx, s.uid.hashCode() * 3 + st.ordinal, i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return Triple(0, ctx.getString(label), pi)
        }
        val b = NotificationCompat.Builder(ctx, CH_ASK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_title, "${s.subject} · ${Repo.get(ctx).typeName(s.typeId)}"))
            .setContentText(ctx.getString(R.string.notif_text, fmtTime(s.end)))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
        listOf(
            action(Status.PRESENT, R.string.status_present),
            action(Status.ABSENT, R.string.status_absent),
            action(Status.JUSTIFIED, R.string.status_justified),
        ).forEach { (icon, label, pi) -> b.addAction(icon, label, pi) }
        NotificationManagerCompat.from(ctx).notify(s.uid.hashCode(), b.build())
    }

    @SuppressLint("MissingPermission")
    fun upcoming(ctx: Context, s: Session) {
        if (!canPost(ctx)) return
        val b = NotificationCompat.Builder(ctx, CH_ASK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_upcoming_title, "${s.subject} · ${Repo.get(ctx).typeName(s.typeId)}"))
            .setContentText(ctx.getString(R.string.notif_upcoming_text, fmtTime(s.start)))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
        NotificationManagerCompat.from(ctx).notify(("upcoming-" + s.uid).hashCode(), b.build())
    }

    @SuppressLint("MissingPermission")
    fun alert(ctx: Context, st: SubjectStats, pct: Int) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.alert_title, st.title))
            .setContentText(warnText(ctx, st, pct))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(("alert-" + st.key).hashCode(), n)
    }

    /** After an absence was recorded: warn if that subject is close to (or under) the limit. */
    fun checkAlerts(ctx: Context, uid: String) {
        val repo = Repo.get(ctx)
        val settings = repo.settings()
        if (!settings.notifications) return
        val sessions = repo.sessions()
        val session = sessions.firstOrNull { it.uid == uid } ?: return
        val names = repo.typeNames()
        val st = Stats.compute(
            sessions, repo.attendance(), repo.prefs(), repo.examMappings(), repo.availableExams(),
            System.currentTimeMillis(), settings.requiredPct, names, repo::typeName
        ).firstOrNull { it.subject == session.subject && it.typeId == session.typeId } ?: return
        if (st.absent >= 1 && st.margin <= settings.alertAt) alert(ctx, st, settings.requiredPct)
    }
}

/** Handles the Present / Absent / Excused buttons of the notification. */
class AttendanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getStringExtra("uid") ?: return
        val status = Status.from(intent.getStringExtra("st")) ?: return
        Repo.get(context).setStatus(uid, status)
        NotificationManagerCompat.from(context).cancel(uid.hashCode())
        if (status == Status.ABSENT) Notif.checkAlerts(context, uid)
    }
}
