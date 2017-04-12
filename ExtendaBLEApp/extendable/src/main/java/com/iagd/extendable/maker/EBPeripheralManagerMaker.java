package com.iagd.extendable.maker;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import com.iagd.extendable.maker.lambdas.ServiceMakerOperation;
import com.iagd.extendable.manager.EBCentralManager;
import com.iagd.extendable.manager.EBPeripheralManager;

import java.util.ArrayList;
import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattDescriptor.PERMISSION_WRITE;

/**
 * Created by Anton on 4/10/17.
 */

public class EBPeripheralManagerMaker {

    private static final String mtuServiceUUIDString = "F80A41CA-8B71-47BE-8A92-E05BB5F1F862";
    private static final String mtuServiceCharacteristicUUID = "37CD1740-6822-4D85-9AAF-C2378FDC4329";

    public ArrayList<EBServiceMaker> services = new ArrayList<EBServiceMaker>();

    public EBPeripheralManager addService(String serviceUUID, ServiceMakerOperation maker) {
        EBServiceMaker serviceMaker =  new EBServiceMaker();
        serviceMaker.setUUID(serviceUUID);
        maker.addService(serviceMaker);
        services.add(serviceMaker);
        return constructedManager();
    }

    public EBPeripheralManager constructedManager() {

        EBPeripheralManager newManager = new EBPeripheralManager();

        for (EBServiceMaker service : services) {
            for (UUID key : service.callbacks.keySet()) {
                newManager.updateCallbacks.put(key, service.callbacks.get(key));
            }

            for (EBCharacteristicMaker maker : service.characteristics) {
                if (maker.getChunkingEnabled()) {
                    newManager.chunkedChracteristicUUIDS.add(UUID.fromString(maker.getUuid()));
                }
            }

            newManager.registeredServices.add(service.constructedService());
            newManager.chunkedChracteristicUUIDS.addAll(service.chunkedUUIDs);
        }

        if (newManager.chunkedChracteristicUUIDS.size() > 0) {

            UUID serviceUUID = UUID.fromString(mtuServiceUUIDString);
            UUID characteristicUUID = UUID.fromString(mtuServiceCharacteristicUUID);

            BluetoothGattService newService = new BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

            BluetoothGattCharacteristic newCharacteristic = new BluetoothGattCharacteristic(characteristicUUID,
                    PROPERTY_READ|PROPERTY_WRITE|PROPERTY_NOTIFY,
                    PERMISSION_READ | PERMISSION_WRITE);

            BluetoothGattDescriptor gD = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"), BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
            newCharacteristic.addDescriptor(gD);

            newService.addCharacteristic(newCharacteristic);
            newManager.registeredServices.add(newService);
        }

        return newManager;
    }
}
