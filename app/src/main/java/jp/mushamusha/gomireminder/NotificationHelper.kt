package jp.mushamusha.gomireminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate

object NotificationHelper {
    private const val CHANNEL_ID = "gomi_reminder"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "ゴミ出し通知", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    fun show(context: Context, rule: CollectionRule, date: LocalDate) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val id = ("${rule.id}|$date".hashCode() and 0x7fffffff)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${rule.trashType}の日です")
            .setContentText(if (rule.area.isBlank()) date.toString() else "${rule.area}・$date")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "出した", action(context, rule.id, date, "DONE"))
            .addAction(0, "30分後", action(context, rule.id, date, "SNOOZE"))
            .addAction(0, "今回は通知不要", action(context, rule.id, date, "SKIP"))
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, ruleId: String, date: LocalDate) {
        NotificationManagerCompat.from(context).cancel(("$ruleId|$date".hashCode() and 0x7fffffff))
    }

    private fun action(context: Context, ruleId: String, date: LocalDate, action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra("ruleId", ruleId)
            putExtra("date", date.toString())
        }
        val code = ("$ruleId|$date|$action".hashCode() and 0x7fffffff)
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
