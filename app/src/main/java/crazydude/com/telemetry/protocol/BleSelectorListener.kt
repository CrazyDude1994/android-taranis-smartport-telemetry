package crazydude.com.telemetry.protocol

import android.bluetooth.BluetoothGattCharacteristic

interface BleSelectorListener {

    fun onCharacteristicsDiscovered(characteristics: List<BluetoothGattCharacteristic>)
}