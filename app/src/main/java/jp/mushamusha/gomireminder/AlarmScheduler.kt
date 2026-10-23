package jp.mushamusha.gomireminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = AppPreferences(context)

    fun rebuildAll() {
        val rules = prefs.loadRules()
        prefs.clearTransientStateForMissingRules(rules.map { it.id }.toSet())
        rules.forEach { scheduleRule(it) }
        GomiWidgetProvider.updateAll(context)
    }

    fun scheduleRule(rule: CollectionRule) {
        cancelRule(rule.id)
        if (!rule.enabled) return
        val today = LocalDate.now()
        for (offset in 0L..45L) {
            val date = today.plusDays(offset)
            if (!ScheduleMatcher.matches(rule, date) || prefs.isSuppressed(rule.id, date)) continue
            if (rule.notifyPreviousDay) {
                val at = date.minusDays(1).atTime(rule.previousDayTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (at > System.currentTimeMillis()) schedule(rule.id, date, AlarmKind.PREVIOUS_DAY, at)
            }
            if (rule.notifyMorning) {
                val at = date.atTime(rule.morningTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (at > System.currentTimeMillis()) schedule(rule.id, date, AlarmKind.MORNING, at)
            }
            prefs.getSnooze(rule.id, date)?.let { at -> if (at > System.currentTimeMillis()) schedule(rule.id, date, AlarmKind.SNOOZE, at) }
        }
    }

    fun scheduleSnooze(ruleId: String, date: LocalDate, delayMillis: Long = 30 * 60_000L) {
        if (prefs.isSuppressed(ruleId, date)) return
        val rule = prefs.loadRules().firstOrNull { it.id == ruleId } ?: return
        if (!ScheduleMatcher.matches(rule, date)) return
        val at = System.currentTimeMillis() + delayMillis
        prefs.putSnooze(ruleId, date, at)
        schedule(ruleId, date, AlarmKind.SNOOZE, at)
    }

    fun cancelRule(ruleId: String) {
        val start = LocalDate.now().minusDays(2)
        for (offset in 0L..50L) {
            val date = start.plusDays(offset)
            AlarmKind.entries.forEach { kind ->
                existingPendingIntent(ruleId, date, kind)?.let(alarmManager::cancel)
            }
        }
    }

    private fun schedule(ruleId: String, date: LocalDate, kind: AlarmKind, atMillis: Long) {
        val pi = createPendingIntent(ruleId, date, kind)
        try {
            if (Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun alarmIntent(ruleId: String, date: LocalDate, kind: AlarmKind) =
        Intent(context, AlarmReceiver::class.java).apply {
            action = "jp.mushamusha.gomireminder.ALARM"
            putExtra("ruleId", ruleId)
            putExtra("date", date.toString())
            putExtra("kind", kind.name)
        }

    private fun createPendingIntent(ruleId: String, date: LocalDate, kind: AlarmKind): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            stableRequestCode(ruleId, date, kind),
            alarmIntent(ruleId, date, kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun existingPendingIntent(ruleId: String, date: LocalDate, kind: AlarmKind): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            stableRequestCode(ruleId, date, kind),
            alarmIntent(ruleId, date, kind),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

    private fun stableRequestCode(ruleId: String, date: LocalDate, kind: AlarmKind): Int =
        ("$ruleId|$date|${kind.name}".hashCode() and 0x7fffffff)
}
