package timemachine

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import timemachine.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val tick: Runnable = Runnable {
        val lockAt = LockScheduler.lockAtMillis(this)
        if (lockAt != null && lockAt <= System.currentTimeMillis()) {
            LockScheduler.ensureArmed(this)
        }
        render()
        if (LockScheduler.lockAtMillis(this) != null) {
            handler.postDelayed(tick, 1_000L)
        }
    }

    private val enableAdmin = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        render()
    }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.enableAdminButton.setOnClickListener { promptAdmin() }
        binding.startButton.setOnClickListener { startTimer() }
        binding.cancelButton.setOnClickListener {
            LockScheduler.cancel(this)
            render()
        }
        binding.preset10s.setOnClickListener { setDuration(0, 10) }
        binding.preset1m.setOnClickListener { setDuration(1, 0) }
        binding.preset15m.setOnClickListener { setDuration(15, 0) }
        binding.preset30m.setOnClickListener { setDuration(30, 0) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        LockScheduler.ensureArmed(this)
        render()
        handler.removeCallbacks(tick)
        if (LockScheduler.lockAtMillis(this) != null) {
            handler.postDelayed(tick, 1_000L)
        }
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun startTimer() {
        if (!DeviceLock.isAdminActive(this)) {
            promptAdmin()
            return
        }
        if (!LockScheduler.canScheduleExact(this)) {
            binding.statusText.text = getString(R.string.error_exact_alarm)
            promptExactAlarm()
            return
        }

        val minutes = binding.minutesInput.text?.toString()?.toLongOrNull() ?: 0L
        val seconds = binding.secondsInput.text?.toString()?.toLongOrNull() ?: 0L
        if (seconds !in 0..59) {
            binding.statusText.text = getString(R.string.error_bad_seconds)
            return
        }
        val totalMs = (minutes * 60 + seconds) * 1_000L
        when {
            totalMs < 5_000L -> {
                binding.statusText.text = getString(R.string.error_too_short)
                return
            }
            totalMs > 24 * 60 * 60 * 1_000L -> {
                binding.statusText.text = getString(R.string.error_too_long)
                return
            }
        }

        if (!LockScheduler.schedule(this, System.currentTimeMillis() + totalMs)) {
            binding.statusText.text = getString(R.string.error_exact_alarm)
            promptExactAlarm()
            return
        }
        render()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1_000L)
    }

    private fun render() {
        val admin = DeviceLock.isAdminActive(this)
        binding.adminStatus.text = getString(if (admin) R.string.admin_on else R.string.admin_off)
        binding.enableAdminButton.visibility = if (admin) View.GONE else View.VISIBLE

        val lockAt = LockScheduler.lockAtMillis(this)
        val remaining = lockAt?.minus(System.currentTimeMillis()) ?: 0L
        val running = lockAt != null && remaining > 0L
        binding.cancelButton.visibility = if (running) View.VISIBLE else View.GONE
        binding.statusText.text = when {
            running -> getString(R.string.status_running, formatRemaining(remaining))
            lockAt != null -> getString(R.string.status_locking)
            !admin -> getString(R.string.status_need_admin)
            else -> getString(R.string.status_idle)
        }
    }

    private fun setDuration(minutes: Int, seconds: Int) {
        binding.minutesInput.setText(minutes.toString())
        binding.secondsInput.setText(seconds.toString())
    }

    private fun promptAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DeviceLock.adminComponent(this))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.admin_explanation),
            )
        enableAdmin.launch(intent)
    }

    private fun promptExactAlarm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:$packageName")),
        )
    }

    private fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
