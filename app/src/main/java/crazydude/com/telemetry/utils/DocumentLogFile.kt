package crazydude.com.telemetry.utils

import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

class DocumentLogFile(private val file: DocumentFile, private val contentResolver: ContentResolver) : LogFile {

    override fun length(): Long = file.length()

    override val inputStream: InputStream
        get() = contentResolver.openInputStream(file.uri)!!

    override val outputStream: OutputStream
        get() = contentResolver.openOutputStream(file.uri)!!
    override val name: String
        get() = file.name!!

    override val uri: Uri?
        get() = file.uri
}