package crazydude.com.telemetry.ui

import android.content.*
import android.os.Bundle
import android.widget.Toast
import androidx.preference.PreferenceFragmentCompat
import com.google.firebase.analytics.FirebaseAnalytics
import crazydude.com.telemetry.R
import crazydude.com.telemetry.manager.PreferenceManager
import crazydude.com.telemetry.utils.FileLogger
import java.io.IOException

class PrefsFragment : PreferenceFragmentCompat() {

    private lateinit var prefManager: PreferenceManager
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        updateSummary()
        FirebaseAnalytics.getInstance(context!!).setUserProperty(
            "telemetry_sharing_enable",
            prefManager.isSendDataEnabled().toString().toLowerCase()
        )
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "settings"
        setPreferencesFromResource(R.xml.preferences, rootKey)

        prefManager = PreferenceManager(context!!)

        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        findPreference("copy_debug_info").setOnPreferenceClickListener {
            context?.let {
                val clipboardManager =
                    it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                try {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText(null, FileLogger(it).copyLogFile()))
                } catch (e: IOException) {
                    Toast.makeText(it, "No log data available yet", Toast.LENGTH_SHORT).show()
                    return@let
                }
                Toast.makeText(it, "Debug data has been copied", Toast.LENGTH_SHORT).show()
            }
            return@setOnPreferenceClickListener false
        }
        findPreference("clear_debug_info").setOnPreferenceClickListener {
            context?.let {
                FileLogger(it).clearLogFile()
                Toast.makeText(it, "Debug data has been cleared", Toast.LENGTH_SHORT).show()
            }
            return@setOnPreferenceClickListener false
        }
        updateSummary()
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun updateSummary() {
        preferenceScreen.findPreference("callsign").summary = prefManager.getCallsign()
        preferenceScreen.findPreference("model").summary = prefManager.getModel()
    }
}