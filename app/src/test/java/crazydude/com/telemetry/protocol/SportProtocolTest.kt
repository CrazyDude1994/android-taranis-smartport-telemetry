package crazydude.com.telemetry.protocol

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.mockito.internal.verification.Calls

class SportProtocolTest {

    @Test
    fun protocolTest() {
        val inputStream = this.javaClass.classLoader.getResourceAsStream("sport.log")
        Assert.assertNotNull(inputStream)
        val mock = mock(DataDecoder.Listener::class.java)
        val protocol = FrSkySportProtocol(mock)
        do {
            val data = inputStream.read()
            protocol.process(data.toUByte().toInt())
        } while (data != -1)

        verify(mock, times(1)).onFuelData(1)
        verify(mock, times(1)).onFuelData(255)
        verify(mock, times(1)).onGPSData(0.0, 0.0)
        verify(mock, times(1)).onGPSData(12.3456, 12.3456)
        verify(mock, times(1)).onGPSData(-12.3456, 12.3456)
        verify(mock, times(1)).onGPSData(-12.3456, -12.3456)
        verify(mock, times(1)).onGPSData(-12.3456, -12.3456)
        verify(mock, times(1)).onVBATData(16.80f)
        verify(mock, times(1)).onCellVoltageData(4.20f)
        verify(mock, times(1)).onCurrentData(5.1f)
        verify(mock, times(1)).onHeadingData(180.25f)
//        verifyNoMoreInteractions(mock)
    }
}