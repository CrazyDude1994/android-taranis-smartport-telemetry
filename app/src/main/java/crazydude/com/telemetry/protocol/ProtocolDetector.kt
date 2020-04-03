package crazydude.com.telemetry.protocol

import android.util.Log
import crazydude.com.telemetry.protocol.decoder.DataDecoder

class ProtocolDetector(private val callback: Callback) {

    private val hits = arrayOf(0, 0, 0, 0)
    private val sportProtocol =
        FrSkySportProtocol(object : DataDecoder.Companion.DefaultDecodeListener() {
            override fun onSuccessDecode() {
                hits[0]++
            }
        })
    private val crsfProtocol =
        CrsfProtocol(object : DataDecoder.Companion.DefaultDecodeListener() {
            override fun onSuccessDecode() {
                hits[1]++
            }
        })
    private val ltmProtocol =
        LTMProtocol(object : DataDecoder.Companion.DefaultDecodeListener() {
            override fun onSuccessDecode() {
                hits[2]++
            }
        })
    private val sportPassthroughProtocol =
        FrskySportPassthroughProtocol(object : DataDecoder.Companion.DefaultDecodeListener() {
            override fun onSuccessDecode() {
                hits[3]++
            }
        })

    fun feedData(data: Int) {
        Log.d("ProtocolDetector", "MSzfeedingData")
        sportProtocol.process(data)
        crsfProtocol.process(data)
        ltmProtocol.process(data)
        sportPassthroughProtocol.process(data)


        hits.forEachIndexed { index, i ->
            if (i >= 1000) {
                when (index) {
                    0 -> callback.onProtocolDetected(sportProtocol)
                   // 1 -> callback.onProtocolDetected(crsfProtocol)
                    //2 -> callback.onProtocolDetected(ltmProtocol)
                    3 -> callback.onProtocolDetected(sportPassthroughProtocol)
                }
            }
        }
    }

    interface Callback {
        fun onProtocolDetected(protocol: Protocol?)
    }
}