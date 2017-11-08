package com.iagd.extendable.maker;

import android.bluetooth.BluetoothGattCharacteristic;

import com.iagd.extendable.result.ExtendaBLEResultCallback;

import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattDescriptor.PERMISSION_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;

public class EBCharacteristicMaker {

    private String mUuid;
    private ExtendaBLEResultCallback mUpdateCallback;
    private Boolean mPacketsEnabled = false;

    private int mProperties = PROPERTY_READ | PROPERTY_WRITE;
    private int mPermissions = PERMISSION_READ | PERMISSION_WRITE;

    public BluetoothGattCharacteristic constructedCharacteristic() {
        UUID chracteristicUUID = UUID.fromString(mUuid);
        return new BluetoothGattCharacteristic(chracteristicUUID, mProperties, mPermissions);
    }

    public EBCharacteristicMaker setUpdateCallback(ExtendaBLEResultCallback updateCallback) {
        mUpdateCallback = updateCallback;
        return this;
    }

    public EBCharacteristicMaker setPacketsEnabled(Boolean packetsEnabled) {
        this.mPacketsEnabled = packetsEnabled;
        return this;
    }

    public EBCharacteristicMaker setUuid(String Uuid) {
        mUuid = Uuid;
        return this;
    }

    public EBCharacteristicMaker setProperties(int properties) {
        mProperties = properties;
        return this;
    }

    public EBCharacteristicMaker setPermissions(int permissions) {
        mPermissions = permissions;
        return this;
    }

    public String getUuid() {
        return mUuid;
    }

    public ExtendaBLEResultCallback getUpdateCallback() {
        return mUpdateCallback;
    }

    public Boolean getPacketsEnabled() {
        return this.mPacketsEnabled;
    }
}