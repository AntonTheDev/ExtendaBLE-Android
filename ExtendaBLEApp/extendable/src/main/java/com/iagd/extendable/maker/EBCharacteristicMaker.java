package com.iagd.extendable.maker;

import android.bluetooth.BluetoothGattCharacteristic;

import com.iagd.extendable.result.ExtendaBLEResultCallback;

import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;

/**
 * Created by Anton on 4/4/17.
 */

public class EBCharacteristicMaker {

    private String mUuid;
    private ExtendaBLEResultCallback mUpdateCallback;
    private Boolean mChunkingEnabled = false;

    private int[] mProperties = new int[]{ PROPERTY_READ, PROPERTY_WRITE};
    private int[] mPermissions = new int[]{ PERMISSION_READ, PERMISSION_WRITE};

    public BluetoothGattCharacteristic constructedCharacteristic() {
        UUID chracteristicUUID = UUID.fromString(mUuid);

        int properties = 0;
        int permissions = 0;

        for (Integer property : mProperties) properties = properties | property;
        for (Integer permission : mPermissions) permissions = permissions | permission;

        BluetoothGattCharacteristic newCharacteristic = new BluetoothGattCharacteristic(chracteristicUUID, properties, permissions);
        return newCharacteristic;
    }

    public EBCharacteristicMaker setUpdateCallback(ExtendaBLEResultCallback updateCallback) {
        mUpdateCallback = updateCallback;
        return this;
    }

    public EBCharacteristicMaker setChunkingEnabled(Boolean chunkingEnabled) {
        this.mChunkingEnabled = chunkingEnabled;
        return this;
    }

    public EBCharacteristicMaker setUuid(String Uuid) {
        mUuid = Uuid;
        return this;
    }

    public EBCharacteristicMaker setProperties(int[] properties) {
        mProperties = properties;
        return this;
    }

    public EBCharacteristicMaker setPermissions(int[] permissions) {
        mPermissions = permissions;
        return this;
    }

    public String getUuid() {
        return mUuid;
    }

    public ExtendaBLEResultCallback getUpdateCallback() {
        return mUpdateCallback;
    }

    public Boolean getChunkingEnabled() {
        return this.mChunkingEnabled;
    }
}
