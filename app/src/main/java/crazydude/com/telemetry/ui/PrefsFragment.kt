package crazydude.com.telemetry.ui

import android.os.Bundle
import android.support.v7.preference.PreferenceFragmentCompat
import crazydude.com.telemetry.R

class PrefsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String) {
        preferenceManager.sharedPreferencesName = "settings"
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}