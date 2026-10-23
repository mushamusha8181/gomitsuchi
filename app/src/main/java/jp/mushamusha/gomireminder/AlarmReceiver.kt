package jp.mushamusha.gomireminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra("ruleId") ?: return
        val date = runCatching { LocalDate.parse(intent.getStringExtra("date")) }.getOrNull() ?: return
        val kind = runCatching { AlarmKind.valueOf(intent.getStringExtra("kind") ?: "") }.getOrNull() ?: return
        if (date.isBefore(LocalDate.now())) return
        val prefs = AppPreferences(context)
        val rule = prefs.loadRules().firstOrNull { it.id == ruleId } ?: return
        if (!ScheduleMatcher.matches(rule, date) || prefs.isSuppressed(ruleId, date)) return
        if (kind == AlarmKind.SNOOZE) prefs.clearSnooze(ruleId, date)
        NotificationHelper.show(context, rule, date)
        AlarmScheduler(context).scheduleRule(rule)
    }
}
