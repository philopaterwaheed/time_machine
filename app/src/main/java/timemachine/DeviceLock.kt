package timemachine

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

object DeviceLock {
    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, AdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean {
        val policy = context.getSystemService(DevicePolicyManager::class.java)
        return policy.isAdminActive(adminComponent(context))
    }

    fun lock(context: Context): Boolean {
        val policy = context.getSystemService(DevicePolicyManager::class.java)
        val admin = adminComponent(context)
        if (!policy.isAdminActive(admin)) return false
        policy.lockNow()
        return true
    }
}
