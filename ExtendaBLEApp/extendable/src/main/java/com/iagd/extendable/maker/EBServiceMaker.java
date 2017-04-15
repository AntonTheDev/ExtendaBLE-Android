package com.iagd.extendable.maker;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import com.iagd.extendable.result.ExtendaBLEResultCallback;
import com.iagd.extendable.maker.lambdas.CharacteristicMakerOperation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class EBServiceMaker {

    private String mServiceUUID;
    private Boolean mPrimary = true;

    public ArrayList<EBCharacteristicMaker> characteristics = new ArrayList<>();
    public HashMap<UUID , ExtendaBLEResultCallback> callbacks = new HashMap<>();
    public ArrayList<UUID> chunkedUUIDs = new ArrayList<>();

    public EBServiceMaker setUUID(String Uuid) {
        mServiceUUID = Uuid;
        return this;
    }

    public EBServiceMaker setPrimary(Boolean primary) {
        mPrimary = primary;
        return this;
    }

    public Boolean getPrimary() {
        return mPrimary;
    }

    public UUID getServiceUUID() {
        return UUID.fromString(mServiceUUID);
    }

    public EBServiceMaker addCharacteristic(String chracteristicUUID, CharacteristicMakerOperation maker) {

        EBCharacteristicMaker characteristicMaker =  new EBCharacteristicMaker();
        characteristicMaker.setUuid(chracteristicUUID);
        characteristics.add(characteristicMaker);
        maker.addCharacteristic(characteristicMaker);
        return this;
    }

    public BluetoothGattService constructedService() {

        UUID serviceUUID = UUID.fromString(mServiceUUID);

        int isPrimary = BluetoothGattService.SERVICE_TYPE_PRIMARY;

        if (!mPrimary) {
            isPrimary = BluetoothGattService.SERVICE_TYPE_SECONDARY;
        }

        BluetoothGattService newService = new BluetoothGattService(serviceUUID, isPrimary);

        for (EBCharacteristicMaker characteristic : characteristics) {

            BluetoothGattCharacteristic gattCharacteristic = characteristic.constructedCharacteristic();
            newService.addCharacteristic(gattCharacteristic);

            if (characteristic.getUpdateCallback() != null) {
                callbacks.put(gattCharacteristic.getUuid(), characteristic.getUpdateCallback());
            }

            if (characteristic.getChunkingEnabled()) {
                chunkedUUIDs.add(gattCharacteristic.getUuid());
            }
        }

        return newService;
    }
}
