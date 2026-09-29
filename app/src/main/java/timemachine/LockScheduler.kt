package timemachine

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Arms an exact alarm that survives the activity being closed.
 * AlarmManager keeps the request in the system, so swiping the app away
 * does not cancel it. A reboot does; [ensureArmed] puts it back.
 */
object LockScheduler {
    const val ACTION_LOCK = "timemachine.LOCK"

    private const val PREFS = "time_machine"
    private const val KEY_LOCK_AT = "lock_at"
    private const val ALARM_REQUEST = 42
    private const val CHANNEL_ID = "lock_timer"
    private const val NOTIFICATION_ID = 7
    private const val ADMIN_NOTIFICATION_ID = 8
    private const val EXPIRED_GRACE_MS = 15 * 60 * 1_000L

    fun lockAtMillis(context: Context): Long? {
        val value = prefs(context).getLong(KEY_LOCK_AT, 0L)
        return value.takeIf { it > 0L }
    }

    fun schedule(context: Context, triggerAtMillis: Long): Boolean {
        if (!arm(context, triggerAtMillis)) return false
        prefs(context).edit().putLong(KEY_LOCK_AT, triggerAtMillis).commit()
        showRunningNotification(context, triggerAtMillis)
        return true
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(lockPendingIntent(context))
        clear(context)
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_LOCK_AT).commit()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun ensureArmed(context: Context) {
        val triggerAt = lockAtMillis(context) ?: return
        val now = System.currentTimeMillis()
        if (triggerAt > now) {
            if (arm(context, triggerAt)) showRunningNotification(context, triggerAt)
            return
        }
        val expiredBy = now - triggerAt
        clear(context)
        if (expiredBy > EXPIRED_GRACE_MS) return
        if (!DeviceLock.lock(context)) showAdminMissing(context)
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun showAdminMissing(context: Context) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context, notifications)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(context.getString(R.string.notification_admin_title))
            .setContentText(context.getString(R.string.notification_admin_text))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        notifications.notify(ADMIN_NOTIFICATION_ID, notification)
    }

    private fun arm(context: Context, triggerAtMillis: Long): Boolean {
        return try {
            context.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                lockPendingIntent(context),
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun lockPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LockAlarmReceiver::class.java).setAction(ACTION_LOCK)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showRunningNotification(context: Context, triggerAtMillis: Long) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context, notifications)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(
                context.getString(R.string.notification_text, formatTrigger(triggerAtMillis)),
            )
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context, notifications: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = context.getString(R.string.channel_description)
        notifications.createNotificationChannel(channel)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatTrigger(triggerAtMillis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(triggerAtMillis))
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return time
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(triggerAtMillis))
        return "$date $time"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
