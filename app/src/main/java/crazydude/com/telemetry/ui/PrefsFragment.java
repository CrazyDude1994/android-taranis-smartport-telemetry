package crazydude.com.telemetry.ui;

import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;
import crazydude.com.telemetry.R;

public class PrefsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName("settings");
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }
}