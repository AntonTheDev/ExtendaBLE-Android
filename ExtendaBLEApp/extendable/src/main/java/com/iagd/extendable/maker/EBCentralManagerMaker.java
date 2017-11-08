package com.iagd.extendable.maker;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import com.iagd.extendable.maker.lambdas.ServiceMakerOperation;
import com.iagd.extendable.manager.EBCentralManager;

import java.util.ArrayList;
import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattDescriptor.PERMISSION_WRITE;

public class EBCentralManagerMaker {

    private static final String mtuServiceUUIDString = "F80A41CA-8B71-47BE-8A92-E05BB5F1F862";
    private static final String mtuServiceCharacteristicUUID = "37CD1740-6822-4D85-9AAF-C2378FDC4329";

    private ArrayList<EBServiceMaker> services = new ArrayList<>();
    private String peripheralName;

    public EBCentralManager addService(String serviceUUID, ServiceMakerOperation maker) {
        EBServiceMaker serviceMaker =  new EBServiceMaker();
        serviceMaker.setUUID(serviceUUID);
        maker.addService(serviceMaker);
        services.add(serviceMaker);
        return constructedManager();
    }

    public EBCentralManagerMaker setPeripheralName(String peripheralName) {
        this.peripheralName = peripheralName;
        return this;
    }

    public EBCentralManager constructedManager() {

        EBCentralManager newManager = new EBCentralManager();
        newManager.peripheralName = peripheralName;

        for (EBServiceMaker service : services) {
            for (UUID key : service.callbacks.keySet()) {
                newManager.updateCallbacks.put(key, service.callbacks.get(key));
            }

            for (EBCharacteristicMaker maker : service.characteristics) {
                newManager.registeredChracteristicUUIDS.add(UUID.fromString(maker.getUuid()));
                if (maker.getPacketsEnabled()) {
                    newManager.chunkedChracteristicUUIDS.add(UUID.fromString(maker.getUuid()));
                }
            }


            newManager.registeredServiceUUIDS.add(service.getServiceUUID());
            newManager.chunkedChracteristicUUIDS.addAll(service.chunkedUUIDs);
        }

        if (newManager.chunkedChracteristicUUIDS.size() > 0) {

            UUID serviceUUID = UUID.fromString(mtuServiceUUIDString);
            UUID characteristicUUID = UUID.fromString(mtuServiceCharacteristicUUID);

            BluetoothGattService newService = new BluetoothGattService(serviceUUID,  BluetoothGattService.SERVICE_TYPE_PRIMARY);

            BluetoothGattCharacteristic newCharacteristic = new BluetoothGattCharacteristic(characteristicUUID,
                    PROPERTY_READ|PROPERTY_WRITE|PROPERTY_NOTIFY,
                    PERMISSION_READ | PERMISSION_WRITE);

            BluetoothGattDescriptor gD = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"), BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
            newCharacteristic.addDescriptor(gD);

            newService.addCharacteristic(newCharacteristic);
            newManager.registeredServiceUUIDS.add(serviceUUID);
        }

        return newManager;
    }
}
