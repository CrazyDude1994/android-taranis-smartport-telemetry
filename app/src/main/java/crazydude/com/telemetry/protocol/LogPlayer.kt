package crazydude.com.telemetry.protocol

import android.os.AsyncTask
import java.io.File
import java.io.FileInputStream

class LogPlayer(file: File, val dataReadyListener: DataReadyListener) {

    private val logFile = FileInputStream(file)
    private var cachedData = ArrayList<FrSkySportProtocol.Companion.TelemetryData>()

    companion object {
        private const val TAG = "LogPlayer"
    }

    init {
        object : AsyncTask<File, Long, ArrayList<FrSkySportProtocol.Companion.TelemetryData>>() {

            override fun doInBackground(vararg file: File): ArrayList<FrSkySportProtocol.Companion.TelemetryData> {
                val arrayList = ArrayList<FrSkySportProtocol.Companion.TelemetryData>()
                val protocol = FrSkySportProtocol(object : FrSkySportProtocol.Companion.DataListener {
                    override fun onNewData(data: FrSkySportProtocol.Companion.TelemetryData) {
                        arrayList.add(data)
                    }
                })

                val size = (file[0].length() / 100).toInt()
                val bytes = ByteArray(size)
                var bytesRead = logFile.read(bytes)
                var allBytes = bytesRead
                while (bytesRead == size) {
                    for (i in 0 until bytesRead) {
                        protocol.process(bytes[i].toInt())
                    }
                    publishProgress(((allBytes / file[0].length().toFloat()) * 100).toLong())
                    bytesRead = logFile.read(bytes)
                    allBytes += bytesRead
                }

                return arrayList
            }

            override fun onProgressUpdate(vararg values: Long?) {
                values?.let { dataReadyListener.onUpdate(values[0]?.toInt() ?: 0) }
            }

            override fun onPostExecute(result: ArrayList<FrSkySportProtocol.Companion.TelemetryData>) {
                dataReadyListener.onDataReady(result?.size)
                cachedData = result
            }

        }.execute(file)
    }

    interface DataReadyListener {
        fun onUpdate(percent: Int)
        fun onDataReady(size: Int)
    }
}