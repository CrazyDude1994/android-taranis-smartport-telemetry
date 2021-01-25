package crazydude.com.telemetry.ui

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.google.firebase.analytics.FirebaseAnalytics
import crazydude.com.telemetry.R
import crazydude.com.telemetry.manager.PreferenceManager

class PrefsFragment : PreferenceFragmentCompat() {

    companion object {
        private const val REQUEST_FILE_TREE = 0
    }

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
        findPreference("log_folder").apply {
            isVisible = shouldUseStorageAPI()
        }.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            startActivityForResult(intent, REQUEST_FILE_TREE)
            return@setOnPreferenceClickListener true
        }
        updateSummary()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_TREE && resultCode == RESULT_OK) {
            prefManager.setLogsStorageFolder(data?.dataString)
            context?.contentResolver?.takePersistableUriPermission(
                data?.data!!,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            updateSummary()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun updateSummary() {
        preferenceScreen.findPreference("log_folder").summary =
            prefManager.getLogsStorageFolder() ?: "No directory has been set yet"
        preferenceScreen.findPreference("callsign").summary = prefManager.getCallsign()
        preferenceScreen.findPreference("model").summary = prefManager.getModel()
    }
}