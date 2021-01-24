package crazydude.com.telemetry.utils

import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

interface LogFile {
    fun length(): Long

    val inputStream: InputStream
    val outputStream: OutputStream
    val name: String
    val uri: Uri?
}