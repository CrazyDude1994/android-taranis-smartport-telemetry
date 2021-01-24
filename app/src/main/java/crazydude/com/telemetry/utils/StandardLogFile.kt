package crazydude.com.telemetry.utils

import android.net.Uri
import java.io.*

class StandardLogFile(private val file: File) : LogFile {

    override fun length(): Long = file.length()

    override val inputStream: InputStream
        get() = FileInputStream(file)

    override val outputStream: OutputStream
        get() = FileOutputStream(file)

    override val name: String
        get() = file.nameWithoutExtension

    override val uri: Uri?
        get() = null
}