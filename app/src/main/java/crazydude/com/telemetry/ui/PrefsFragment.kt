package crazydude.com.telemetry.ui

import android.app.Activity.RESULT_OK
import android.content.*
import android.os.Bundle
import android.widget.Toast
import androidx.preference.PreferenceFragmentCompat
import com.google.firebase.analytics.FirebaseAnalytics
import crazydude.com.telemetry.R
import crazydude.com.telemetry.manager.PreferenceManager
import crazydude.com.telemetry.utils.FileLogger
import java.io.IOException
import java.lang.Exception

class PrefsFragment : PreferenceFragmentCompat() {

    companion object {
        private const val REQUEST_FILE_TREE = 0
    }

    private lateinit var prefManager: PreferenceManager
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        updateSummary()
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
    }
}