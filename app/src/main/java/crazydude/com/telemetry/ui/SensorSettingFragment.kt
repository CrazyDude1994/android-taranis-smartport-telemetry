package crazydude.com.telemetry.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class SensorSettingFragment : PreferenceFragmentCompat() {

    private var prefs: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = arguments?.getInt("prefs")
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "settings"
        setPreferencesFromResource(prefs!!, rootKey)
    }
}