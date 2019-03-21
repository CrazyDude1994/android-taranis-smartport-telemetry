package crazydude.com.telemetry.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import crazydude.com.telemetry.R

class SettingsActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.parent, PrefsFragment())
            .commit()
    }
}