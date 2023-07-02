package crazydude.com.telemetry.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import crazydude.com.telemetry.BuildConfig
import crazydude.com.telemetry.R

class SettingsActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        val toolbar : Toolbar = findViewById(R.id.toolbar)

        toolbar?.title = "Settings v " + BuildConfig.VERSION_NAME

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.parent, PrefsFragment())
            .commit()
    }
}