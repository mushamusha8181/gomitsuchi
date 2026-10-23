package jp.mushamusha.gomireminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AppPreferences(context).loadRules().none { it.enabled }) return
        AlarmScheduler(context).rebuildAll()
    }
}
