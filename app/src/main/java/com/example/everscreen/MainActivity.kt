package com.example.everscreen

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.everscreen.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private enum class Mode { DURATION, UNTIL }

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var mode = Mode.DURATION
    private var selectedMinutes = 30          // used in DURATION mode
    private var untilHour = 22                 // used in UNTIL mode
    private var untilMinute = 30

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Start regardless; on API < 33 there's nothing to grant, and if the
            // user declines on 33+ the service still runs, it just can't show
            // its progress notification (Android may kill it sooner as a result).
            startScreenOnService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Default "until" time: one hour from now, rounded to the nearest 5 minutes.
        Calendar.getInstance().apply {
            add(Calendar.MINUTE, 60)
            untilHour = get(Calendar.HOUR_OF_DAY)
            untilMinute = (get(Calendar.MINUTE) / 5) * 5
        }

        setupModeToggle()
        setupDurationPanel()
        setupUntilPanel()

        binding.startStopButton.setOnClickListener {
            if (ScreenOnService.state.value.isRunning) {
                stopScreenOnService()
            } else {
                requestNotificationPermissionThenStart()
            }
        }

        // Reflect the service's live state whenever this screen is visible,
        // including right after opening the app while it's already running.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ScreenOnService.state.collect { state -> render(state) }
            }
        }
    }

    // ---------- Mode toggle (plain buttons, styled manually — no Material toggle-group quirks) ----------

    private fun setupModeToggle() {
        binding.durationModeButton.setOnClickListener { selectMode(Mode.DURATION) }
        binding.untilModeButton.setOnClickListener { selectMode(Mode.UNTIL) }
        applyModeStyling()
    }

    private fun selectMode(newMode: Mode) {
        mode = newMode
        binding.durationPanel.visibility = if (mode == Mode.DURATION) View.VISIBLE else View.GONE
        binding.untilPanel.visibility = if (mode == Mode.UNTIL) View.VISIBLE else View.GONE
        applyModeStyling()
    }

    private fun applyModeStyling() {
        val durationSelected = mode == Mode.DURATION
        binding.durationModeButton.setBackgroundResource(
            if (durationSelected) R.drawable.bg_segment_selected else R.drawable.bg_segment_unselected
        )
        binding.durationModeButton.setTextColor(
            ContextCompat.getColor(this, if (durationSelected) android.R.color.white else R.color.purple_500)
        )
        binding.untilModeButton.setBackgroundResource(
            if (!durationSelected) R.drawable.bg_segment_selected else R.drawable.bg_segment_unselected
        )
        binding.untilModeButton.setTextColor(
            ContextCompat.getColor(this, if (!durationSelected) android.R.color.white else R.color.purple_500)
        )
    }

    // ---------- Duration panel ----------

    private fun setupDurationPanel() {
        binding.durationSeekBar.progress = selectedMinutes - 1
        updateDurationLabel()

        binding.durationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedMinutes = progress + 1
                updateDurationLabel()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.preset15Button.setOnClickListener { setDurationMinutes(15) }
        binding.preset30Button.setOnClickListener { setDurationMinutes(30) }
        binding.preset60Button.setOnClickListener { setDurationMinutes(60) }
        binding.preset120Button.setOnClickListener { setDurationMinutes(120) }
    }

    private fun setDurationMinutes(minutes: Int) {
        selectedMinutes = minutes.coerceIn(1, 180)
        binding.durationSeekBar.progress = selectedMinutes - 1
        updateDurationLabel()
    }

    private fun updateDurationLabel() {
        binding.durationLabel.text = when {
            selectedMinutes % 60 == 0 -> "${selectedMinutes / 60}h"
            selectedMinutes > 60 -> "${selectedMinutes / 60}h ${selectedMinutes % 60}m"
            else -> "$selectedMinutes min"
        }
    }

    // ---------- Until panel ----------

    private fun setupUntilPanel() {
        updateUntilLabels()
        binding.pickTimeButton.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    untilHour = hour
                    untilMinute = minute
                    updateUntilLabels()
                },
                untilHour,
                untilMinute,
                DateFormat.is24HourFormat(this)
            ).show()
        }
    }

    private fun updateUntilLabels() {
        binding.untilTimeLabel.text = String.format(Locale.getDefault(), "%02d:%02d", untilHour, untilMinute)
        val minutes = minutesUntilTarget()
        val h = minutes / 60
        val m = minutes % 60
        binding.untilDurationLabel.text = when {
            h > 0 && m > 0 -> "in ${h}h ${m}m"
            h > 0 -> "in ${h}h"
            else -> "in ${m}m"
        }
    }

    /** Minutes from now until the chosen clock time; rolls to tomorrow if that time already passed today. */
    private fun minutesUntilTarget(): Int {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, untilHour)
            set(Calendar.MINUTE, untilMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        val diffMillis = target.timeInMillis - now.timeInMillis
        return (diffMillis / 60000L).toInt().coerceAtLeast(1)
    }

    // ---------- Start / stop ----------

    private fun requestNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startScreenOnService()
        }
    }

    private fun startScreenOnService() {
        // Refresh the "until" countdown right before starting, in case time has passed while configuring.
        if (mode == Mode.UNTIL) updateUntilLabels()
        val minutes = if (mode == Mode.DURATION) selectedMinutes else minutesUntilTarget()

        val intent = Intent(this, ScreenOnService::class.java).apply {
            action = ScreenOnService.ACTION_START
            putExtra(ScreenOnService.EXTRA_MINUTES, minutes)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopScreenOnService() {
        val intent = Intent(this, ScreenOnService::class.java).apply {
            action = ScreenOnService.ACTION_STOP
        }
        startService(intent)
    }

    private fun render(state: ScreenOnService.State) {
        if (state.isRunning) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // While running, the setup section (mode toggle + duration/until panels) is
        // hidden entirely rather than just disabled — nothing to configure once it's
        // already going, and this avoids a big dead patch of greyed-out controls.
        binding.setupGroup.visibility = if (state.isRunning) View.GONE else View.VISIBLE
        binding.startStopButton.text = if (state.isRunning) "Stop" else "Start"

        if (state.isRunning) {
            val minutes = state.remainingMillis / 60000
            val seconds = (state.remainingMillis / 1000) % 60
            binding.statusText.text = String.format(Locale.getDefault(), "Screen on — %02d:%02d remaining", minutes, seconds)
            binding.endTimeText.text = "Ends at ${formatClockTime(state.endTimeMillis)} — keeps working in the background"
        } else {
            binding.statusText.text = "Set a duration and tap Start"
            binding.endTimeText.text = ""
        }
    }
}
