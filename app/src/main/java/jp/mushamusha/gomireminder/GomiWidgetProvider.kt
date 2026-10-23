package jp.mushamusha.gomireminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate

class GomiWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GomiWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = AppPreferences(context)
            val today = LocalDate.now()
            val next = prefs.loadRules().asSequence().filter { it.enabled }.mapNotNull { rule ->
                ScheduleMatcher.nextOccurrence(rule, today)?.let { rule to it }
            }.filterNot { (rule, date) -> prefs.isSuppressed(rule.id, date) }
                .minByOrNull { it.second }

            val views = RemoteViews(context.packageName, R.layout.gomi_widget)
            views.setTextViewText(R.id.widget_body, next?.let { (r, d) -> "${d.monthValue}/${d.dayOfMonth}  ${r.trashType}" } ?: "予定なし")
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_body, open)
            manager.updateAppWidget(id, views)
        }
    }
}
