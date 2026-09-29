package timemachine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LockAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LockScheduler.ACTION_LOCK) return
        LockScheduler.clear(context)
        if (!DeviceLock.lock(context)) {
            LockScheduler.showAdminMissing(context)
        }
    }
}
