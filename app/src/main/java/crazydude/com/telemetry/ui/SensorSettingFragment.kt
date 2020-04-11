package crazydude.com.telemetry.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import crazydude.com.telemetry.R

class SensorSettingFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            // Get the layout inflater
            val inflater = requireActivity().layoutInflater;

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            builder.setView(inflater.inflate(R.layout.dialog_sensor_settings, null))
                // Add action buttons
                .setPositiveButton(R.string.ok
                ) { dialog, _ ->

                }
                .setNegativeButton(
                    R.string.cancel
                ) { dialog, id ->
                    getDialog().cancel()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}