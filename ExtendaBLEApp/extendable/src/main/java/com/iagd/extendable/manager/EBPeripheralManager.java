package com.iagd.extendable.manager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import com.iagd.extendable.result.ExtendaBLEResultCallback;
import com.iagd.extendable.transaction.EBData;
import com.iagd.extendable.transaction.EBTransaction;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


public class EBPeripheralManager {
    private static final String mtuServiceUUIDString = "F80A41CA-8B71-47BE-8A92-E05BB5F1F862";
    private static final String mtuServiceCharacteristicUUID = "37CD1740-6822-4D85-9AAF-C2378FDC4329";

    private static String logTag = "PeripheralManager";

    private BluetoothManager bluetoothManager;
    private BluetoothLeAdvertiser mBLEAdvertiser;
    private BluetoothGattServer mGattServer;

    public ArrayList<BluetoothGattService> registeredServices = new ArrayList<>();
    public ArrayList<UUID> chunkedChracteristicUUIDS = new ArrayList<>();

    private HashMap<String, Short> mtuValues = new HashMap<>();
    public HashMap<UUID , ExtendaBLEResultCallback> updateCallbacks = new HashMap<>();

    private HashMap<BluetoothDevice , ArrayList<EBTransaction>> activeReadTransations = new HashMap<>();
    private HashMap<BluetoothDevice , ArrayList<EBTransaction>> activeWriteTransations = new HashMap<>();

    private Context applicationContext;

    /**
     * Start the scanner by passing it the application context at the start
     */
    public void startAdvertisingInApplicationContext(Context context) {
        this.applicationContext = context;

        bluetoothManager = (BluetoothManager) this.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();
        mBLEAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        mGattServer = bluetoothManager.openGattServer(this.applicationContext, gattServerCallback);

        for (BluetoothGattService service : registeredServices) {
            mGattServer.addService(service);
        }

        startAdvertising();
    }

    public void close() {
        stopAdvertising();
        mGattServer.close();

    }

    private void startAdvertising() {
        Log.d(logTag, "Started Advertising");

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable( true )
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName( true )
                .addServiceUuid( new ParcelUuid(registeredServices.get(0).getUuid()))
                .build();

        mBLEAdvertiser.startAdvertising(settings, data, LEAdvertisingCallback);
    }

    public void stopAdvertising() {
        Log.d(logTag, "Stopped Advertising");
        mBLEAdvertiser.stopAdvertising(LEAdvertisingCallback);
    }

    private BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.d(logTag, "Connected to Device" + device.getName()  + " w/ State : " + newState);
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Log.d(logTag, "Service Added to Peripheral " + service.getUuid()  + " w/ Status : " + status);
            super.onServiceAdded(status, service);

            if (status == 133) {
                Log.d(logTag, "Unable to Add Service " + service.getUuid()  + " w/ Restarting Peripheral");
                close();
                startAdvertisingInApplicationContext(applicationContext);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            handleReadRequest(device, requestId, offset,characteristic);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            handleWriteRequest(device, requestId,characteristic, offset, value);
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            Log.i(logTag, "onDescriptorReadRequest, uuid: " + descriptor.getUuid());
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            Log.i(logTag, "onDescriptorWriteRequest, uuid: " + descriptor + responseNeeded);

            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            short mtuValue = mtuValues.get(device.getAddress());

            BluetoothGattCharacteristic characteristic =  mGattServer.getService(UUID.fromString(mtuServiceUUIDString)).getCharacteristic(UUID.fromString(mtuServiceCharacteristicUUID));
            characteristic.setValue(new EBData(mtuValue, mtuValue).getData());
            mGattServer.notifyCharacteristicChanged(device, characteristic, true);
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            Log.i(logTag, "onExecuteWrite, uuid: " + device.getName());
            super.onExecuteWrite(device, requestId, execute);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            Log.d(logTag, "onNotificationSent" + device.getName());
            super.onNotificationSent(device, status);
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            int mtuValue = mtu - 3;
            Log.d(logTag, "MTU Changed " + mtuValue);
            mtuValues.put(device.getAddress(), (short) mtuValue);
        }
    };

    private AdvertiseCallback LEAdvertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d(logTag, "Advertising Started Successfully");
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.d(logTag, "Advertising onStartFailure: " + errorCode );
            super.onStartFailure(errorCode);
        }
    };

    private void handleReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {

        if (offset != 0) {
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null);
            return;
        }

        byte[] data =  characteristic.getValue();

        if (characteristic.getValue() == null) {
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH, offset, null);
            return;
        }

        activeReadTransations.putIfAbsent(device, new ArrayList<>());
        List<EBTransaction> transactions = activeReadTransations.get(device).stream().filter(p -> p.getCharacteristic().getUuid().equals(characteristic.getUuid())).collect(Collectors.toList());

        EBTransaction transaction;

        if (transactions.size() == 0) {
            Log.d(logTag, "Peripheral Read Started for " + characteristic.getUuid());

            EBTransaction.TransactionType transactionType = EBTransaction.TransactionType.READ;
            EBTransaction.TransactionDirection transactionDirection =  EBTransaction.TransactionDirection.PERIPHERAL_TO_CENTRAL;

            if (chunkedChracteristicUUIDS.contains(characteristic.getUuid())) {
                transactionType =  EBTransaction.TransactionType.READ_CHUNKABLE;
            }

            short mtuValue = mtuValues.get(device.getAddress());

            transaction = new EBTransaction(transactionType, transactionDirection, mtuValue);
            transaction.setCharacteristic(characteristic);
            transaction.setData(data, mtuValue);

            ExtendaBLEResultCallback updateCallback = updateCallbacks.get(characteristic.getUuid());

            if (updateCallback != null) {
                transaction.setCompletionCallback(updateCallback);
            }

            activeReadTransations.get(device).add(transaction);
        } else {
            transaction = transactions.get(0);
        }

        if (transaction != null) {
            transaction.processTransaction();
            byte[] packet = transaction.nextPacket();
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, packet);

            if (transaction.isComplete()) {
                Log.d(logTag, "Peripheral Read Packet " + transaction.getActiveResponseCount() + " / " + transaction.getTotalPacketCount());
                Log.d(logTag, "Peripheral Read Complete");

                int index = activeReadTransations.get(device).indexOf(transactions.get(0));
                activeReadTransations.get(device).remove(index);
            } else {
                Log.d(logTag, "Peripheral Read Packet " + transaction.getActiveResponseCount() + " / " + transaction.getTotalPacketCount());
            }
        }
    }

    private void handleWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, int offset, byte[] value) {

        if (offset != 0) {
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null);
            return;
        }

        if (value == null) {
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH, offset, null);
            return;
        }

        activeWriteTransations.putIfAbsent(device, new ArrayList<>());
        List<EBTransaction> transactions = activeWriteTransations.get(device).stream().filter(p -> p.getCharacteristic().getUuid().equals(characteristic.getUuid())).collect(Collectors.toList());

        EBTransaction transaction;

        if (transactions.size() == 0) {
            Log.d(logTag, "Peripheral Write Started for " + characteristic.getUuid());

            EBTransaction.TransactionType transactionType = EBTransaction.TransactionType.WRITE;
            EBTransaction.TransactionDirection transactionDirection =  EBTransaction.TransactionDirection.PERIPHERAL_TO_CENTRAL;

            if (chunkedChracteristicUUIDS.contains(characteristic.getUuid())) {
                transactionType =  EBTransaction.TransactionType.WRITE_CHUNKABLE;
            }

            short mtuValue = mtuValues.get(device.getAddress());

            transaction = new EBTransaction(transactionType, transactionDirection, mtuValue);
            transaction.setCharacteristic(characteristic);

            ExtendaBLEResultCallback updateCallback = updateCallbacks.get(characteristic.getUuid());

            if (updateCallback != null) {
                transaction.setCompletionCallback(updateCallback);
            }

            activeWriteTransations.get(device).add(transaction);
        } else {
            transaction = transactions.get(0);
        }

        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);

        transaction.appendPacket(value);
        transaction.processTransaction();
        Log.d(logTag, "Peripheral Write Packet " + transaction.getActiveResponseCount() + " / " + transaction.getTotalPacketCount());

        if (transaction.isComplete()) {
            characteristic.setValue(transaction.getData());

            int index = activeWriteTransations.get(device).indexOf(transactions.get(0));

            transaction.getCompletionCallback().setResult(transaction.getData());
            transaction.getCompletionCallback().call();

            activeWriteTransations.get(device).remove(index);
            Log.d(logTag, "Peripheral Write Complete");
        }
    }

    private boolean clearCacheAndDiscover(BluetoothGatt gatt){
        try {
            BluetoothGatt localBluetoothGatt = gatt;
            Method localMethod = localBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
            if (localMethod != null) {
                boolean bool = (Boolean) localMethod.invoke(localBluetoothGatt, new Object[0]);
                startAdvertising();
                return bool;
            }
        }
        catch (Exception localException) {
            Log.e(logTag, "An exception occured while refreshing device");
        }
        return false;
    }
}