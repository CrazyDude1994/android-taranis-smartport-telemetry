package crazydude.com.telemetry.protocol

import android.util.Log
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import crazydude.com.telemetry.protocol.decoder.FrskyPassthroughDataDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder


class FrskySportPassthroughProtocol_dzialaAleCosZlePisze : Protocol {

    constructor(dataListener: DataDecoder.Listener) : super(FrskyPassthroughDataDecoder(dataListener))
    constructor(dataDecoder: DataDecoder) : super(dataDecoder)

    private var state: State = Companion.State.IDLE
    private var bufferIndex: Int = 0
    private var buffer: IntArray = IntArray(PACKET_SIZE)

    companion object {
        enum class State {
            IDLE, DATA, XOR
        }

        const val PACKET_SIZE = 0x09

        const val START_BYTE = 0x7E
        const val DATA_START = 0x10
        const val DATA_STUFF = 0x7D
        const val STUFF_MASK = 0x20

        const val VFAS_SENSOR = 0x0210
        const val CELL_SENSOR = 0x0910
        const val VSPEED_SENSOR = 0x0110
        const val GSPEED_SENSOR = 0x0830
        const val ALT_SENSOR = 0x0100
        const val GALT_SENSOR = 0x0820
        const val DISTANCE_SENSOR = 0x0420
        const val FUEL_SENSOR = 0x0600
        const val GPS_SENSOR = 0x0800
        const val CURRENT_SENSOR = 0x200
        const val HEADING_SENSOR = 0x0840
        const val RSSI_SENSOR = 0xF101
        const val FLYMODE_SENSOR = 0x0400
        const val GPS_STATE_SENSOR = 0x0410
        const val PITCH_SENSOR = 0x0430
        const val ROLL_SENSOR = 0x0440
        const val AIRSPEED_SENSOR = 0x0A00

        private val TAG: String = "FrSky Protocol"
    }

    //const val START_BYTE = 0x7E
    //const val DATA_START = 0x10
    //const val DATA_STUFF = 0x7D
    //const val STUFF_MASK = 0x20



    /*

void DecodeFrSky() {
   if (FT) {                  // Sync to the first start/stop character 0x7E
    chr = NextChar();
    while (!(chr==0x7E)) {
      chr = NextChar();
     }
    FT=false;
  }
  // Candidate found

  pBuff[0]=chr;            // Start-Stop character 0x7E
  pBuff[1]=NextChar();     // Sensor-ID

  chr=NextChar();                 // Start-Stop or Data-Frame Header

  if (chr==0x10) {                // If data frame header
    pBuff[2]=chr;
    boolean goodPacket=ParseFrsky();
    if (goodPacket) ProcessFrsky();
  //  DisplayTheBuffer(10);
    chr=NextChar();   //  Should be the next Start-Stop
    }
 // else DisplayTheBuffer(2);

  if (!(chr==0x7E)) FT=true;  //  If next char is not start-stop then the frame sync has been lost. Resync
}



    boolean ParseFrsky() {
 crc=0;
  Add_Crc(pBuff[2]);           // data frame char into crc

  for (int i=3; i<=8; i++) {
    chr = NextChar();
    pBuff[i]=chr;
    Add_Crc(chr);
  }
  chr=NextChar();
  pBuff[9]=chr;  //  crc

  if (chr==(0xFF-crc)){
    crc_bad = false;
 //  Serial.println("CRC Good");
  }
  else {
    crc_bad=true;
//   Serial.println("CRC Bad");
  }
  return !crc_bad;
}

     */


    /*

     */

    fun ProcessFrsky() {
        // Do the sensor packets according to fr_payload type. We are expecting Mavlink Passthrough only
        val fr_payloadType = Unpack_uint16 (3)
        val fr_payload = Unpack_uint32(5)
        Log.d(TAG, "MSz payloadtype=" + fr_payloadType)
        Log.d(TAG, "MSz payload=" + fr_payload)
        //   Serial.print(" fr_payloadType=");
        //    Serial.println(fr_payloadType, HEX);
        when(fr_payloadType.toInt()) {
             0x5006-> Log.d(TAG, "MSz 5006 pitch!")
            0x5003-> Log.d(TAG, "MSz 5003 bateria!")

        }
    }


    fun Unpack_uint32 (posn : Int) : ULong {

        //  The number starts at byte "posn" of the received packet and is four bytes long.
        //  GPS payload fields are little-endian, i.e they need an end-to-end byte swap

        val  b1 = pBuff[posn+3];
        val  b2 = pBuff[posn+2];
        val  b3 = pBuff[posn+1];
        val  b4 = pBuff[posn];

        val highWord : ULong = b1.toULong() shl 8 or b2.toULong()
        val lowWord  = b3.toULong() shl 8 or b4.toULong()

        // Now combine the four bytes into an unsigned 32bit integer

        val myvar: ULong = highWord shl 16 or lowWord
        return myvar;
    }



    fun Unpack_int32 (posn : Int) : Long {

        //  The number starts at byte "posn" of the received packet and is four bytes long.
        //  GPS payload fields are little-endian, i.e they need an end-to-end byte swap

        val b1 = pBuff[posn+3];
        val b2 = pBuff[posn+2];
        val b3 = pBuff[posn+1];
        val b4 = pBuff[posn];

        val highWord = b1.toLong() shl 8 or b2.toLong()
        val lowWord  = b3.toLong() shl 8 or b4.toLong()

        // Now combine the four bytes into an unsigned 32bit integer

         val myvar : Long = highWord shl 16 or lowWord;
        return myvar
    }

     fun Unpack_uint16 (posn : Int) : UShort {

        //  The number starts at byte "posn" of the received packet and is two bytes long
        //  GPS payload fields are little-endian, i.e they need an end-to-end byte swap

        val  b1 = pBuff[posn+1]
        val  b2 = pBuff[posn]

        // Now convert the 2 bytes into an unsigned 16bit integer
        val myvar  = (b1.toInt() shl 8) or b2.toInt()
        return myvar.toUShort()
    }


    fun bit32Extract( dword : UInt, displ : UByte,  lth: UByte) : UInt {
        var r : UInt = (dword and createMask(displ,(displ+lth-1U).toUByte())) shr displ.toInt()
//  Serial.print(" Result=");
        // Serial.println(r);
        return r
    }

    fun createMask( lo: UByte, hi: UByte) : UInt {
        var r: UInt = 0U
        for (i in lo..hi)
            r = r or (1U shl i.toInt())
//  Serial.print(" Mask 0x=");
//  Serial.println(r, HEX);
        return r;
    }


    //fun Add_Crc (uint8_t byte) {
    var packetSize = 70
     var pBuff = ByteArray(70)
var licznik = 0
    var crc: Int = 0
    var chr: Int = 0;
    var FT: Boolean = true;

    fun Add_Crc (byte : Byte) : Unit {
        crc += byte       //0-1FF
        //crc += crc >> 8   //0-100
        crc += crc shr 8   //0-100
        //crc &= 0x00ff
        crc = crc and 0x00ff
        //crc += crc >> 8   //0-0FF
        crc += crc shr 8   //0-0FF
        //crc &= 0x00ff
        crc = crc and 0x00ff
    }


    fun  ParseFrsky(c: Byte)  : Pair<Boolean,Boolean> {
        crc=0;
        Add_Crc(pBuff[2]);           // data frame char into crc

        var i: Int = 0
        if(licznik <= 8){
        //for (i=3; i<=8; i++) {

            Log.d(TAG, "MSz licznik=" + licznik + "wrzucam do pBuff to:" + c)
            pBuff[licznik]=c
            licznik ++;
            Add_Crc(c);
            return Pair(false, false)
        }

        pBuff[9]=c  //  crc

        if (c==(0xFF-crc).toByte()){
            //crc_bad = false;
            return Pair(true, false)
            //  Serial.println("CRC Good");
        }
        else {
            //crc_bad=true;
//   Serial.println("CRC Bad");
        }
        //return !crc_bad;
        return Pair(true, true)
    }


    override fun process(data: Int) {


        Log.d("TAG", "MSzprocessing data Passthrough, got data:" + data)

        if (FT) {                  // Sync to the first start/stop character 0x7E

            if(chr!=0x7E){
                chr = data
                return
            }
            FT=false;
            licznik = 0
        }
        Log.d("TAG", "MSzsynchronized!")
        // Candidate found

        if(licznik == 0) {
            pBuff[0] = chr.toByte()            // Start-Stop character 0x7E
            pBuff[1] = data.toByte()     // Sensor-ID
            Log.d("TAG", "0, setting licznik to 1!")
            licznik = 1
            return
        }


        chr=data                 // Start-Stop or Data-Frame Header

        if (chr==0x10 && licznik == 1) {                // If data frame header
            Log.d("TAG", "MSz data frame header!")
            pBuff[2] = chr.toByte()
            licznik++
            return
        }
        if(licznik > 1 && licznik <= 10) {
            val wynik = ParseFrsky(data.toByte())
            Log.d(TAG, "MSz wynik=" + wynik + " licznik= " + licznik)
            if(wynik.first && wynik.second){
                Log.d(TAG, "MSz procesuje!")
                ProcessFrsky()
            }

            if(licznik==9) {
                licznik++
                return
            }
            if(licznik==10) {
                licznik++
                return
            }
        }
            //boolean goodPacket=ParseFrsky();
            //if (goodPacket) ProcessFrsky();
            //  DisplayTheBuffer(10);
            //chr=NextChar();   //  Should be the next Start-Stop



        if (licznik >= 11){
            Log.d(TAG, "MSz licznik==" + licznik)
            if(chr!=0x7E){
                Log.d(TAG, "MSz FT znowu true, koniec ramki")
                FT=true
        }  //  If next char is not start-stop then the frame sync has been lost. Resync
        }
        return

        when (state) {
            Companion.State.IDLE -> {
                if (data == START_BYTE) {
                    state = Companion.State.DATA
                    bufferIndex = 0
                }
            }
            Companion.State.DATA -> {
                if (data == DATA_STUFF) {
                    state = Companion.State.XOR
                } else if (data == START_BYTE) {
                    bufferIndex = 0
                } else {
                    buffer[bufferIndex++] = data
                }
            }
            Companion.State.XOR -> {
                buffer[bufferIndex++] = data xor STUFF_MASK
                state = Companion.State.DATA
            }
        }

        if (bufferIndex == PACKET_SIZE) {
            Log.d("TAG", "MSzd2")
            state = Companion.State.IDLE
            val byteBuffer = ByteBuffer.wrap(buffer.foldIndexed(ByteArray(buffer.size)) { i, a, v ->
                a.apply {
                    set(
                        i,
                        v.toByte()
                    )
                }
            }).order(ByteOrder.LITTLE_ENDIAN)
            val sensorType = byteBuffer.get()
            val packetType = byteBuffer.get()
            if (packetType.toInt() == DATA_START) {
                val dataType = byteBuffer.short
                val rawData = byteBuffer.int
                when (dataType.toInt()) {
                    FUEL_SENSOR -> {
                        Log.d(TAG, "Fuel: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                FUEL,
                                rawData
                            )
                        )
                    }
                    GPS_SENSOR -> {
                        Log.d(TAG, "GPS: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                GPS,
                                rawData
                            )
                        )
                    }
                    VFAS_SENSOR -> {
                        Log.d(TAG, "VBAT: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                VBAT,
                                rawData
                            )
                        )
                    }
                    CELL_SENSOR -> {
                        Log.d(TAG, "Cell voltage: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                CELL_VOLTAGE,
                                rawData
                            )
                        )
                    }
                    CURRENT_SENSOR -> {
                        Log.d(TAG, "Current: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                CURRENT,
                                rawData
                            )
                        )
                    }
                    HEADING_SENSOR -> {
                        Log.d(TAG, "Heading: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                HEADING,
                                rawData
                            )
                        )
                    }
                    RSSI_SENSOR -> {
                        Log.d(TAG, "RSSI: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                RSSI,
                                rawData
                            )
                        )
                    }
                    FLYMODE_SENSOR -> {
                        Log.d(TAG, "Fly mode: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                FLYMODE,
                                rawData
                            )
                        )
                    }
                    GPS_STATE_SENSOR -> {
                        Log.d(TAG, "GPS State: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                GPS_STATE,
                                rawData
                            )
                        )
                    }
                    VSPEED_SENSOR -> {
                        Log.d(TAG, "VSpeed: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                VSPEED,
                                rawData
                            )
                        )
                    }
                    GALT_SENSOR -> {
                        Log.d(TAG, "GAlt: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                GALT,
                                rawData
                            )
                        )
                    }
                    GSPEED_SENSOR -> {
                        Log.d(TAG, "GSpeed: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                GSPEED,
                                rawData
                            )
                        )
                    }
                    DISTANCE_SENSOR -> {
                        Log.d(TAG, "Distance: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                DISTANCE,
                                rawData
                            )
                        )
                    }
                    ALT_SENSOR -> {
                        Log.d(TAG, "Altitutde: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                ALTITUDE,
                                rawData
                            )
                        )
                    }
                    PITCH_SENSOR -> {
                        Log.d(TAG, "Pitch: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                PITCH,
                                rawData
                            )
                        )
                    }
                    ROLL_SENSOR -> {
                        Log.d(TAG, "Roll: $rawData")
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(
                                ROLL,
                                rawData
                            )
                        )
                    }
                    AIRSPEED_SENSOR -> {
                        dataDecoder.decodeData(
                            Protocol.Companion.TelemetryData(ASPEED, rawData)
                        )
                    }
                    else -> {
                        Log.d(TAG, "Unknown packet" + buffer.contentToString())
                    }
                }
            }
        }
    }
}