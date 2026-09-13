package ru.installclicker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs

    private lateinit var enabledSwitch: Switch
    private lateinit var anyPackageCheck: CheckBox
    private lateinit var verboseCheck: CheckBox
    private lateinit var gestureCheck: CheckBox
    private lateinit var statusText: TextView
    private lateinit var deviceText: TextView
    private lateinit var logText: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val refreshLoop = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)

        enabledSwitch = findViewById(R.id.switch_enabled)
        anyPackageCheck = findViewById(R.id.check_any_package)
        verboseCheck = findViewById(R.id.check_verbose)
        gestureCheck = findViewById(R.id.check_gesture)
        statusText = findViewById(R.id.text_status)
        deviceText = findViewById(R.id.text_device)
        logText = findViewById(R.id.text_log)

        bind(enabledSwitch) { prefs.enabled = it }
        bind(anyPackageCheck) { prefs.anyPackage = it }
        bind(verboseCheck) { prefs.verboseLog = it }
        bind(gestureCheck) { prefs.gestureFallback = it }

        findViewById<Button>(R.id.button_settings).setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.button_clear_log).setOnClickListener {
            EventLog.clear()
            refreshStatus()
        }
        findViewById<Button>(R.id.button_copy_log).setOnClickListener { copyLog() }

        deviceText.text = getString(
            R.string.device_info,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
            Build.MODEL,
        )
    }

    override fun onResume() {
        super.onResume()
        syncFromPrefs()
        handler.post(refreshLoop)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshLoop)
        super.onPause()
    }

    /**
     * Слушатель ставится один раз, а программное изменение состояния
     * помечается тегом, чтобы не записывать настройку саму в себя.
     */
    private fun bind(button: CompoundButton, save: (Boolean) -> Unit) {
        button.setOnCheckedChangeListener { view, checked ->
            if (view.tag == TAG_SYNCING) return@setOnCheckedChangeListener
            save(checked)
            refreshStatus()
        }
    }

    private fun syncFromPrefs() {
        setChecked(enabledSwitch, prefs.enabled)
        setChecked(anyPackageCheck, prefs.anyPackage)
        setChecked(verboseCheck, prefs.verboseLog)
        setChecked(gestureCheck, prefs.gestureFallback)
    }

    private fun setChecked(button: CompoundButton, value: Boolean) {
        button.tag = TAG_SYNCING
        button.isChecked = value
        button.tag = null
    }

    private fun refreshStatus() {
        val enabledInSettings = InstallClickerService.isEnabledInSettings(this)

        statusText.text = when {
            !enabledInSettings -> getString(R.string.status_service_off)
            !InstallClickerService.isRunning -> getString(R.string.status_service_starting)
            !prefs.enabled -> getString(R.string.status_autoclick_off)
            else -> getString(R.string.status_ready)
        }

        val log = EventLog.snapshot()
        logText.text = if (log.isEmpty()) getString(R.string.log_empty) else log.joinToString("\n")
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun copyLog() {
        val log = EventLog.snapshot()
        val payload = buildString {
            appendLine(deviceText.text)
            appendLine(statusText.text)
            appendLine()
            append(if (log.isEmpty()) getString(R.string.log_empty) else log.joinToString("\n"))
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), payload))
        Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 1000L
        const val TAG_SYNCING = "syncing"
    }
}
