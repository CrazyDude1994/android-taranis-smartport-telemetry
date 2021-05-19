package crazydude.com.telemetry.utils

import android.content.Context
import android.os.Environment
import com.google.firebase.analytics.FirebaseAnalytics
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class FileLogger(val context: Context) : Logger {

    companion object {
        val format: SimpleDateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm")
    }

    override fun log(message: String) {
        val stream =
            FileOutputStream(File(context.filesDir, "log.txt"), true).bufferedWriter()
        stream.write("[${format.format(Date())}] $message")
        stream.newLine()
        stream.close()
    }

    fun copyLogFile() : String {
        val bufferedReader =
            FileInputStream(File(context.filesDir, "log.txt")).bufferedReader().readLines()
        return bufferedReader.joinToString("\r\n")
    }

    fun clearLogFile() {
        val stream =
            FileOutputStream(File(context.filesDir, "log.txt")).bufferedWriter()
        stream.close()
    }
}