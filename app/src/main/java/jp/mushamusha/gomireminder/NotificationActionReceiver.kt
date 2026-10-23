package jp.mushamusha.gomireminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra("ruleId") ?: return
        val date = runCatching { LocalDate.parse(intent.getStringExtra("date")) }.getOrNull() ?: return
        if (date.isBefore(LocalDate.now())) return
        val prefs = AppPreferences(context)
        val rule = prefs.loadRules().firstOrNull { it.id == ruleId } ?: return
        if (!ScheduleMatcher.matches(rule, date)) return
        when (intent.action) {
            "DONE", "SKIP" -> {
                prefs.setSuppressed(ruleId, date, true)
                prefs.clearSnooze(ruleId, date)
                NotificationHelper.cancel(context, ruleId, date)
                AlarmScheduler(context).scheduleRule(rule)
                GomiWidgetProvider.updateAll(context)
            }
            "SNOOZE" -> {
                NotificationHelper.cancel(context, ruleId, date)
                AlarmScheduler(context).scheduleSnooze(ruleId, date)
            }
        }
    }
}
