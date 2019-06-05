package crazydude.com.telemetry.ui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.google.firebase.analytics.FirebaseAnalytics
import crazydude.com.telemetry.R
import crazydude.com.telemetry.manager.PreferenceManager

class PrefsFragment : PreferenceFragmentCompat() {

    private lateinit var prefManager: PreferenceManager
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        updateSummary()
        FirebaseAnalytics.getInstance(context!!).setUserProperty("telemetry_sharing_enable", prefManager.isSendDataEnabled().toString().toLowerCase())
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "settings"
        setPreferencesFromResource(R.xml.preferences, rootKey)

        prefManager = PreferenceManager(context!!)

        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
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