package com.iagd.extendable.manager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.AsyncTask;
import android.os.ParcelUuid;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.iagd.extendable.result.ExtendaBLEResultCallback;
import com.iagd.extendable.transaction.EBData;
import com.iagd.extendable.transaction.EBTransaction;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_FIRST_MATCH;

public class EBCentralManager {

    private static final String mtuServiceCharacteristicUUID = "37CD1740-6822-4D85-9AAF-C2378FDC4329";
    private static String logTag = "CentralManager";

    private Context applicationContext;

    private BluetoothManager bluetoothManager;
    private BluetoothLeScanner mBTScanner;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mConnectedGatt = null;
    private short mtuSize = 20;

    public ArrayList<UUID> registeredServiceUUIDS = new ArrayList<>();
    public ArrayList<UUID> chunkedChracteristicUUIDS = new ArrayList<>();
    public HashMap<UUID , ExtendaBLEResultCallback> updateCallbacks = new HashMap<>();

    private HashMap<BluetoothGatt , ArrayList<BluetoothGattCharacteristic>> connectedCharacteristics = new HashMap<>();
    private HashMap<BluetoothGatt , ArrayList<EBTransaction>> activeReadTransactions = new HashMap<>();
    private HashMap<BluetoothGatt , ArrayList<EBTransaction>> activeWriteTransactions = new HashMap<>();

    private Callable peripheralConnectionChange;

    /**
     * Start the scanner by passing it the application context at the start
     */

    public void startScanningInApplicationContext(Context context) {
        this.applicationContext = context;

        bluetoothManager = (BluetoothManager) this.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBTScanner = mBluetoothAdapter.getBluetoothLeScanner();
        startScanning();
    }

    public void stopScanning() {
        Log.d(logTag, "Stop Scanning");
        AsyncTask.execute(() -> mBTScanner.stopScan(leScanCallback));
    }

    private void startScanning() {
        AsyncTask.execute(() -> {
            Log.d(logTag, "Configured Scan Settings / Filters");

            List<ScanFilter> filters = new ArrayList<>();

            for (UUID uuid : registeredServiceUUIDS){
                ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(uuid)).build();
                filters.add(filter);
            }

            ScanSettings settings = new ScanSettings.Builder().setCallbackType(CALLBACK_TYPE_FIRST_MATCH).setReportDelay((long) 0.33).build();
            mBTScanner.startScan(filters, settings, leScanCallback);
        });
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(logTag, "Scan Results Triggered, Connecting to : " + result.getDevice().getName());
            mConnectedGatt = result.getDevice().connectGatt(applicationContext, false, leGattCallback);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d(logTag, "Scan Failed ErrorCode: " + errorCode);

            super.onScanFailed(errorCode);
        }
    };

    /**
     * Handle Service Discovery
     */

    private void handleServicesDiscovered(BluetoothGatt gatt, int status) {
        Log.d(logTag, "Services Discovered For : " + gatt.getDevice().getName() +  " w/ Status : " + status);

        for (BluetoothGattService service : gatt.getServices()) {

            if (registeredServiceUUIDS.contains(service.getUuid())) {
                Log.d(logTag, "Services matched registered UUID(s) " + service.getUuid());
                Log.d(logTag, "Characteristics : ");

                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    Log.d(logTag, "       - " + characteristic.getUuid());

                    connectedCharacteristics.putIfAbsent(gatt, new ArrayList<>());
                    connectedCharacteristics.get(gatt).add(characteristic);

                    configureMTUNotificationIfFound(gatt, characteristic);
                }
            }

            if (chunkedChracteristicUUIDS.size() == 0) {
                triggerConnectionChangedCallback();
            }
        }
    }

    private void configureMTUNotificationIfFound(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

        if (characteristic.getUuid().toString().toUpperCase().equals(mtuServiceCharacteristicUUID.toUpperCase())) {
            Log.d(logTag, "Requested MTU 500");
            configureMTUNotification(gatt);
            gatt.requestMtu(500);
        }
    }

    private void configureMTUNotification(BluetoothGatt gatt) {
/*
        connectedCharacteristics.get(gatt).stream().filter(gattCharacteristic ->
                gattCharacteristic.getUuid().toString().toUpperCase().equals(mtuServiceCharacteristicUUID.toUpperCase()))
                .forEach(gattCharacteristic -> {

                    BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptors().get(0);

                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);

                        if (gatt.setCharacteristicNotification(gattCharacteristic, true)) {
                            Log.d(logTag, "Success Registered Notifications for " + gattCharacteristic.getUuid().toString());
                        } else {
                            Log.d(logTag, "Failed Notifications Registration for " + gattCharacteristic.getUuid().toString());
                        }
                    }
                });
                */
    }

    /**
     * Connected Peripheral Callback
     */

    private BluetoothGattCallback leGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            Log.d(logTag, "Connection Changed For " + gatt.getDevice().getAddress() + "to State " + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                stopScanning();
                Log.d(logTag, "Connected to GATT : " + gatt.getDevice().getAddress());
                Log.d(logTag, "Starting Service Discovery");
                clearCacheAndDiscover(mConnectedGatt);
               // mConnectedGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(logTag, "Disconnected from GATT : " + gatt.getDevice().getAddress());

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            handleServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            handleReadTransationResponse(gatt, characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            handleWriteTransationResponse(characteristic, gatt);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);

            mtuSize = (short) (mtu - 3);
            Log.d(logTag, "MTU Size Received : " + mtuSize);
            triggerConnectionChangedCallback();
        }
    };


    /**
     *  Connection callback logic, whenever the peripheral with the registered services
     *  is connected to the central this call back is called if set.
     *
     *  In the case that a service is considered as a chunked service, there is an extra
     *  step that needs to happened, where the central will ping the peripheral
     *  and will be notified with the optimal MTU size in response.
     */

    public EBCentralManager setPeripheralConnectionCallback(Callable peripheralConnectionChange) {
        this.peripheralConnectionChange = peripheralConnectionChange;
        return this;
    }

    private void triggerConnectionChangedCallback() {
        if (peripheralConnectionChange != null) {
            try {
                peripheralConnectionChange.call();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *  Write logic is as follows, wheneverver a write transaction is initiated
     *  a new instance of EBTransaction is created to track the send packets
     *  vs the successful responses from the peripheral.
     *
     *  Once the number of packets sent matched the number of responses,
     *  the update callback is triggered as declared in the call.
     */

    public void write(String characteristicUUID, Object data, ExtendaBLEResultCallback updateCallback) {

        Log.d(logTag, "Write Started for " + characteristicUUID);

        for (BluetoothGatt gatt : connectedCharacteristics.keySet()) {
            for (BluetoothGattCharacteristic characteristic : connectedCharacteristics.get(gatt)) {

                if (characteristic.getUuid().toString().toUpperCase().equals(characteristicUUID)) {
                    activeWriteTransactions.putIfAbsent(gatt, new ArrayList<>());

                    List<EBTransaction> transactions = activeWriteTransactions.get(gatt)
                            .stream().filter(p -> p.getCharacteristic().getUuid().equals(characteristic.getUuid()))
                            .collect(Collectors.toList());

                    EBTransaction transaction = null;

                    if (transactions.size() == 0) {
                        transaction = newWriteTransaction(data, characteristic, updateCallback);
                        activeWriteTransactions.get(gatt).add(transaction);
                    }

                    if (transaction != null) {
                        characteristic.setValue(transaction.getDataPackets().get(0));
                        transaction.processTransaction();
                        gatt.writeCharacteristic(characteristic);
                    }
                }
            }
        }
    }

    private void handleWriteTransationResponse(BluetoothGattCharacteristic characteristic, BluetoothGatt fromGatt) {
        if (activeWriteTransactions.get(fromGatt) != null) {

            List<EBTransaction> transactions = activeWriteTransactions.get(fromGatt).stream().filter(p -> p.getCharacteristic().getUuid().equals(characteristic.getUuid())).collect(Collectors.toList());

            if (transactions.size() != 0) {
                int index = activeWriteTransactions.get(fromGatt).indexOf(transactions.get(0));
                EBTransaction transaction =  activeWriteTransactions.get(fromGatt).get(index);

                if (transaction != null) {

                    if (transaction.isComplete()) {
                        Log.d(logTag, "Write Packet Send " + transaction.getActiveResponseCount() + " / " + transaction.getTotalPacketCount());
                        Log.d(logTag, "Write Complete");

                        transaction.getCompletionCallback().setResult(characteristic.getValue());
                        transaction.getCompletionCallback().call();
                        activeWriteTransactions.get(fromGatt).remove(index);
                    }  else {
                        Log.d(logTag, "Write Packet Send " + transaction.getActiveResponseCount() + " / " + transaction.getTotalPacketCount());

                        transaction.processTransaction();
                        byte[] packet = transaction.nextPacket();
                        transaction.getCharacteristic().setValue(packet);
                        fromGatt.writeCharacteristic(characteristic);
                    }
                }
            }
        }
    }

    private EBTransaction newWriteTransaction(Object data,
                                              BluetoothGattCharacteristic characteristic,
                                              ExtendaBLEResultCallback updateCallback) {

        EBTransaction.TransactionType transactionType = EBTransaction.TransactionType.WRITE;
        EBTransaction.TransactionDirection transactionDirection =  EBTransaction.TransactionDirection.CENTRAL_TO_PERIPHERAL;

        if (chunkedChracteristicUUIDS.contains(characteristic.getUuid())) {
            transactionType =  EBTransaction.TransactionType.WRITE_CHUNKABLE;
        }

        EBTransaction transaction = new EBTransaction(transactionType, transactionDirection, mtuSize);
        transaction.setCharacteristic(characteristic);
        transaction.setCompletionCallback(updateCallback);
        transaction.setData(data);
        return transaction;
    }


    /**
     *  Read logic is as follows....
     */

    public void read(String characteristicUUID, ExtendaBLEResultCallback updateCallback) {

        Log.d(logTag, "Read Started for " + characteristicUUID);

        for (BluetoothGatt gatt : connectedCharacteristics.keySet()) {
            for (BluetoothGattCharacteristic characteristic : connectedCharacteristics.get(gatt)) {

                if (characteristic.getUuid().toString().toUpperCase().equals(characteristicUUID)) {

                    activeReadTransactions.putIfAbsent(gatt, new ArrayList<>());

                    List<EBTransaction> transactions = activeReadTransactions.get(gatt).stream().filter(p -> p.getCharacteristic().getUuid().equals(characteristic.getUuid())).collect(Collectors.toList());

                    if (transactions.size() == 0) {
                        EBTransaction.TransactionType transactionType = EBTransaction.TransactionType.READ;
                        EBTransaction.TransactionDirection transactionDirection =  EBTransaction.TransactionDirection.CENTRAL_TO_PERIPHERAL;

                        if (chunkedChracteristicUUIDS.contains(characteristic.getUuid())) {
                            transactionType =  EBTransaction.TransactionType.READ_CHUNKABLE;
                        }

                        EBTransaction transaction = new EBTransaction(transactionType, transactionDirection, mtuSize);
                        transaction.setCharacteristic(characteristic);
                        transaction.setCompletionCallback(updateCallback);
                        activeReadTransactions.get(gatt).add(transaction);
                    }

                    gatt.readCharacteristic(characteristic);
                    return;
                }
            }
        }
    }

    private void handleReadTransationResponse(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (activeReadTransactions.get(gatt) != null) {
            List<EBTransaction> transactions = activeReadTransactions.get(gatt).stream()
                    .filter(p -> p.getCharacteristic().getUuid().equals(characteristic.getUuid()))
                    .collect(Collectors.toList());

            if (transactions.get(0) != null) {
                int index = activeReadTransactions.get(gatt).indexOf(transactions.get(0));
                EBTransaction transaction =  activeReadTransactions.get(gatt).get(index);

                if (transaction != null) {
                    transaction.appendPacket(characteristic.getValue());
                    transaction.processTransaction();

                    if (transaction.isComplete()) {
                        Log.d(logTag, "Read Packet Received " + transaction.getActiveResponseCount() + " / " + transaction.getTotalPacketCount());
                        transaction.getCompletionCallback().setResult(transaction.getData());
                        transaction.getCompletionCallback().call();
                        activeReadTransactions.get(gatt).remove(index);
                    } else {
                        Log.d(logTag, "Read Packet Received " + transaction.getActiveResponseCount() + " / " + transaction.getTotalPacketCount());
                        gatt.readCharacteristic(characteristic);
                    }
                }
            }
        }
    }

    private boolean clearCacheAndDiscover(BluetoothGatt gatt){
        try {
            BluetoothGatt localBluetoothGatt = gatt;
            Method localMethod = localBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
            if (localMethod != null) {
                boolean bool = ((Boolean) localMethod.invoke(localBluetoothGatt, new Object[0])).booleanValue();
                mConnectedGatt.discoverServices();
                return bool;
            }
        }
        catch (Exception localException) {
            Log.e(logTag, "An exception occured while refreshing device");
        }
        return false;
    }
}
