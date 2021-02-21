package crazydude.com.telemetry.protocol

import crazydude.com.telemetry.protocol.decoder.DataDecoder

class ProtocolDetector(private val callback: Callback) {

    private val hits = arrayOf(0, 0, 0, 0, 0)
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
    private val mavLinkProtocol =
        MAVLinkProtocol(object : DataDecoder.Companion.DefaultDecodeListener() {
            override fun onSuccessDecode() {
                hits[3]++
            }
        })
    private val mavLink2Protocol =
        MAVLink2Protocol(object : DataDecoder.Companion.DefaultDecodeListener() {
            override fun onSuccessDecode() {
                hits[4]++
            }
        })

    fun feedData(data: Int) {
        sportProtocol.process(data)
        crsfProtocol.process(data)
        ltmProtocol.process(data)
        mavLinkProtocol.process(data)
        mavLink2Protocol.process(data)


        hits.forEachIndexed { index, i ->
            if (i >= 2) {
                when (index) {
                    0 -> callback.onProtocolDetected(sportProtocol)
                    1 -> callback.onProtocolDetected(crsfProtocol)
                    2 -> callback.onProtocolDetected(ltmProtocol)
                    3 -> callback.onProtocolDetected(mavLinkProtocol)
                    4 -> callback.onProtocolDetected(mavLink2Protocol)
                }
            }
        }
    }

    interface Callback {
        fun onProtocolDetected(protocol: Protocol?)
    }
}