package crazydude.com.telemetry.protocol

import android.util.Log
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import crazydude.com.telemetry.protocol.decoder.FrskyPassthroughDataDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder


class FrskySportPassthroughProtocol : Protocol {

    constructor(dataListener: DataDecoder.Listener) : super(FrskyPassthroughDataDecoder(dataListener))
    constructor(dataDecoder: DataDecoder) : super(dataDecoder)

    private var state: State = Companion.State.IDLE
    private var bufferIndex: Int = 0
    private var buffer: IntArray = IntArray(PACKET_SIZE)

    var ct  = CharArray(5)
    var  p_ct = CharArray(5)
    var  fr_text = CharArray (80)
    var  fr_severity: UInt = 0u
var ct_dups = 0
    var eot = false


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

    //@ExperimentalStdlibApi
    fun ProcessFrsky() {
        // Do the sensor packets according to fr_payload type. We are expecting Mavlink Passthrough only
        val fr_payloadType = Unpack_uint16 (3)
        val fr_payload = Unpack_uint32(5)
        Log.d(TAG, "MSz payloadtype=" + fr_payloadType)
        Log.d(TAG, "MSz payload=" + fr_payload)
        //   Serial.print(" fr_payloadType=");
        //    Serial.println(fr_payloadType, HEX);
        when(fr_payloadType.toInt()) {
            0x800 ->                      // Latitude and Longitude
            {
                Log.d(TAG, "MSz 800 latitude longitude")
            }
            /*
            case 0x5000:                         // Text Message
                   ct[0] = pBuff[8];
                   ct[1] = pBuff[7];
                   ct[2] = pBuff[6];
                   ct[3] = pBuff[5];
                   ct[4] = 0;  // terminate string

                   if (ct[0] == 0 || ct[1] == 0 || ct[2] == 0 || ct[3] == 0)
                     fr_severity = (bit32Extract(fr_payload,15,1) * 4) + (bit32Extract(fr_payload,23,1) * 2) + (bit32Extract(fr_payload,30,1) * 1);

                   if (strcmp(ct,p_ct)==0){     //  If this one = previous one it's a duplicate
                     ct_dups++;
                     break;
                     }

                   // This is the last (3rd) duplicate of (usually) 4 chunks

                   for ( int i=0; i<5 ;i++ ) {  // Copy current 4 byte chunk to previous
                     p_ct[i]=ct[i];
                    }

                   eot=false;

                   if (ct[1] >= 0x80) { // It should always be this one, first of last 3 bytes
                 //    b1 = ct[1];
                     ct[1]=0;
                     ct[2]=0;
                     ct[3]=0;
                     eot=true;
                   }
                   Serial.print("TXT=");
                   Serial.print(ct);
                   Serial.print(" ");
                   strcat(fr_text, ct);  // Concatenate ct onto the back of fr_text
                   ct_dups=0;

                   if (!eot) break; // Drop through when end-of-text found

                   Serial.print("Frsky 5000: Severity=");
                   Serial.print(fr_severity);
                   Serial.print(" Msg : ");
                   Serial.println(fr_text);

                   ST_5000_flag = true;  // Trigger Status Text encode in EncodeMavlink()

                   break;
             */
            0x5000 -> {
                ct[0] = pBuff[8].toChar()
                ct[1] = pBuff[7].toChar()
                ct[2] = pBuff[6].toChar()
                ct[3] = pBuff[5].toChar()
                ct[4] = '\u0000'  // terminate string

                Log.d(TAG, "MSz 5000 p_txt=" + ct[0] + "@" + ct[1] + "@" + ct[2] + "@" + ct[3]
                        + "@" + ct[4] +
                        "/" + pBuff[5].toChar())

                if (ct[0] == 0.toChar() || ct[1] == 0.toChar()
                    || ct[2] == 0.toChar()|| ct[3] == 0.toChar())
                    fr_severity = (bit32Extract(fr_payload.toUInt(),15u,1u) * 4u)                + (bit32Extract(fr_payload.toUInt(),23u,1u) * 2u)                + (bit32Extract(fr_payload.toUInt(),30u,1u) * 1u);

                if (ct contentEquals  p_ct){     //  If this one = previous one it's a duplicate
                    ct_dups++
                    Log.d(TAG, "MSz 5000 duplicate!")
                    return
                }
                for ( i in 0..4 ) {  // Copy current 4 byte chunk to previous
                    p_ct[i]=ct[i]
                }



                eot=false

                if (ct[1] >= 0x80.toChar()) { // It should always be this one, first of last 3 bytes
                    //    b1 = ct[1];
                    ct[1]=0.toChar()
                    ct[2]=0.toChar()
                    ct[3]=0.toChar()
                    eot=true
                }
                Log.d(TAG, "MSz 5000 txt=" + ct.joinToString { "" })


                //strcat(fr_text, ct);  // Concatenate ct onto the back of fr_text
                ct.copyInto(fr_text)
                //ct.con


                ct_dups=0;

                if (!eot)
                    return // Drop through when end-of-text found

                Log.d(TAG, "MSz 5000 Severity=" + fr_severity)
                //fr_text.concatToString()
                Log.d(TAG, "MSz 5000 Msg=" + fr_text.joinToString { "" })
            }

             0x5001 -> {                    // AP Status 2 Hz
                 Log.d(TAG, "MSz 5001 flight mode!")
//            fr_flight_mode = bit32Extract(fr_payload,0,5);
                 val fr_flight_mode = bit32Extract(fr_payload.toUInt(), 0U, 5U);
                 Log.d(TAG, "flight mode=" + fr_flight_mode)
//            fr_simple = bit32Extract(fr_payload,5,2);
//                fr_land_complete = bit32Extract(fr_payload,7,1);
//            fr_armed = bit32Extract(fr_payload,8,1);
//                fr_bat_fs = bit32Extract(fr_payload,9,1);
//            fr_ekf_fs = bit32Extract(fr_payload,10,2);
             }

            0x5005 -> {                    // Vert and Horiz Velocity and Yaw angle (Heading) 2 Hz
//                fr_vx = bit32Extract(fr_payload, 1, 7) * (10^bit32Extract(fr_payload, 0, 1));
//                if (bit32Extract(fr_payload, 8, 1) == 1)
//                    fr_vx *= -1;
//                fr_vy = bit32Extract(fr_payload, 10, 7) * (10^bit32Extract(fr_payload, 9, 1));
//                fr_yaw = bit32Extract(fr_payload, 17, 11) * 0.2;
                val fr_yaw = bit32Extract(fr_payload.toUInt(), 17u, 11u).toDouble() * 0.2
                Log.d(TAG, "heading=" + fr_yaw)
            }

            0x5003 -> {


                val fr_bat_volts = bit32Extract(fr_payload.toUInt(),0u,9u)
                Log.d(TAG, "MSz 5003 voltage = ${fr_bat_volts}")
            }

                0x5006-> {
                 Log.d(TAG, "MSz 5006 pitch!")
                 val fr_pitch = bit32Extract(fr_payload.toUInt(), 11u, 10u)
                 val fr_pitch_float = (fr_pitch - 450U).toFloat() * 0.2
                 val fr_roll = bit32Extract(fr_payload.toUInt(), 0u, 11u)
                 val fr_roll_float = (fr_roll - 900U).toFloat() * 0.2     //  -- roll [0,1800] ==> [-180,180]
                 Log.d(TAG, "MSz 5006 pitch = ${fr_pitch_float} roll=${fr_roll_float}")
             }
            0x5003-> Log.d(TAG, "MSz 5003 bateria!")

        }
    }


    fun Unpack_uint32 (posn : Int) : ULong {

        //  The number starts at byte "posn" of the received packet and is four bytes long.
        //  GPS payload fields are little-endian, i.e they need an end-to-end byte swap

        val  b1 = pBuff[posn+3]
        val  b2 = pBuff[posn+2]
        val  b3 = pBuff[posn+1]
        val  b4 = pBuff[posn]

        val highWord : ULong = (b1.toULong() shl 8) or b2.toULong()
        val lowWord  = (b3.toULong() shl 8) or b4.toULong()

        // Now combine the four bytes into an unsigned 32bit integer

        val myvar: ULong = (highWord shl 16) or lowWord
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
        val r1 = dword and createMask(displ,(displ+lth-1u).toUByte())
        var rr = r1 shr displ.toInt()
        //  Serial.print(" Result=");
        // Serial.println(r);
        return rr
    }

    fun createMask( lo: UByte, hi: UByte) : UInt {
        var r: UInt = 0u
        for (i in lo..hi)
            r = r or (1u shl i.toInt())
//  Serial.print(" Mask 0x=");
//  Serial.println(r, HEX);
        return r;
    }


    //fun Add_Crc (uint8_t byte) {
    var packetSize = 70
     var pBuff = ByteArray(70)
var counter = 0
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
        if(counter <= 8){
        //for (i=3; i<=8; i++) {

            Log.d(TAG, "MSz counter=" + counter + "adding to pBuff:" + c)
            pBuff[counter]=c
            counter ++;
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
            Log.d("TAG", "CRC_BAD")
//   Serial.println("CRC Bad");
        }

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
            counter = 0
        }
        Log.d("TAG", "MSzsynchronized!")
        // Candidate found

        if(counter == 0) {
            pBuff[0] = chr.toByte()            // Start-Stop character 0x7E
            pBuff[1] = data.toByte()     // Sensor-ID
            Log.d("TAG", "0, setting counter to 1!")
            counter = 1
            return
        }


        chr=data                 // Start-Stop or Data-Frame Header

        if (chr==0x10 && counter == 1) {                // If data frame header
            Log.d("TAG", "MSz data frame header!")
            pBuff[2] = chr.toByte()
            //licznik++
            counter = 3
            return
        }
        if(counter > 1 && counter <= 10) {
            val wynik = ParseFrsky(data.toByte())
            Log.d(TAG, "MSz result=" + wynik + " counter= " + counter)
            if(wynik.first && wynik.second){
                Log.d(TAG, "MSz processing!")
                ProcessFrsky()
            }

            if(counter==9) {
                counter++
                return
            }
            if(counter==10) {
                counter++
                return
            }
        }
            //boolean goodPacket=ParseFrsky();
            //if (goodPacket) ProcessFrsky();
            //  DisplayTheBuffer(10);
            //chr=NextChar();   //  Should be the next Start-Stop



        if (counter >= 11){
            Log.d(TAG, "MSz counter==" + counter)
            if(chr!=0x7E){
                Log.d(TAG, "MSz FT again true, end of frame")
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