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
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.iagd.extendable.result.ExtendaBLEResultCallback;
import com.iagd.extendable.transaction.EBData;
import com.iagd.extendable.transaction.EBTransaction;

public class EBCentralManager {

    private static final String mtuServiceUUIDString = "F80A41CA-8B71-47BE-8A92-E05BB5F1F862";
    private static final String mtuServiceCharacteristicUUID = "37CD1740-6822-4D85-9AAF-C2378FDC4329";
    private static final String dataServiceUUIDString = "3C215EBB-D3EF-4D7E-8E00-A700DFD6E9EF";

    private static String logTag = "CentralManager";

    private Context applicationContext;

    private BluetoothManager bluetoothManager;
    private BluetoothLeScanner mBTScanner;
    private BluetoothGatt mConnectedGatt;
    private short mtuSize = 20;

    public ArrayList<UUID> registeredServiceUUIDS = new ArrayList<>();
    public ArrayList<UUID> chunkedChracteristicUUIDS = new ArrayList<>();

    public HashMap<UUID , ExtendaBLEResultCallback> updateCallbacks = new HashMap<>();
    private HashMap<BluetoothGatt , ArrayList<BluetoothGattCharacteristic>> connectedCharacteristics = new HashMap<>();

    private HashMap<BluetoothGatt , ArrayList<EBTransaction>> activeReadTransations = new HashMap<>();
    private HashMap<BluetoothGatt , ArrayList<EBTransaction>> activeWriteTransations = new HashMap<>();

    private Callable peripheralConnectionChange;


    /**
     * Start the scanner by passing it the application context at the start
     */

    public void startScanningInApplicationContext(Context context) {
        this.applicationContext = context;

        bluetoothManager = (BluetoothManager) this.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();
        mBTScanner = mBluetoothAdapter.getBluetoothLeScanner();

        startScanning();
    }

    public void stopScanning() {
        Log.d(logTag, "Stop Scan");
        AsyncTask.execute(() -> mBTScanner.stopScan(leScanCallback));
    }

    private void startScanning() {
        AsyncTask.execute(() -> {
            Log.d(logTag, "Configured Scan Settings / Filters");

            List<ScanFilter> filters = new ArrayList<>();
            ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UUID.fromString(mtuServiceUUIDString))).build();
            ScanFilter filter2 = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UUID.fromString(dataServiceUUIDString))).build();
            filters.add(filter);
            filters.add(filter2);

           // registeredServiceUUIDS.stream().map(uuid ->
             //       new ScanFilter.Builder().setServiceUuid(new ParcelUuid(uuid)).build()).collect(Collectors.toList());

            ScanSettings settings = new ScanSettings.Builder().build();
           // mBTScanner.startScan(leScanCallback);
            mBTScanner.startScan(filters, settings, leScanCallback);
        });
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(logTag, "Scan Results Triggered, Connection to :" + result.getDevice().getName());
            mConnectedGatt = result.getDevice().connectGatt(applicationContext, false, leGattCallback);
            stopScanning();



         }
    };


    /**
     * Handle Service Discovery
     */

    private void handleServicesDiscovered(BluetoothGatt gatt, int status) {
        Log.d(logTag, "Services Discovered for : " + gatt.getDevice().getName());
        Log.d(logTag, "Service Discovery status : " + status);

        for (BluetoothGattService service : gatt.getServices()) {

            if (registeredServiceUUIDS.contains(service.getUuid())) {
                Log.d(logTag, "Services matched registered UUIDs" + service.getUuid());
                Log.d(logTag, "Characteristics : ");

                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();

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
            BluetoothGattDescriptor descriptor = characteristic.getDescriptors().get(0);

            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);

                if (gatt.setCharacteristicNotification(characteristic, true)) {
                    Log.d(logTag, "Success Registered Notifications for " + characteristic.getUuid().toString());
                } else {
                    Log.d(logTag, "Failed Notifications Registration for " + characteristic.getUuid().toString());
                }
            }
        }
    }


    /**
     * Connected Peripheral Callback
     */

    private BluetoothGattCallback leGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            Log.d(logTag, "Connection State Changed for " + gatt.getDevice().getName());

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(logTag, "Connected to GATT : " + gatt.getDevice().getName());
                Log.d(logTag, "Starting Service Discovery");
                mConnectedGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(logTag, "Disconnected from GATT : " + gatt.getDevice().getName());
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
            handleReadTransationResponse(characteristic, gatt);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(logTag, "Write Response for" + characteristic.getUuid().toString());
            handleWriteTransationResponse(characteristic, gatt);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            if (characteristic.getUuid().toString().toUpperCase().equals("37CD1740-6822-4D85-9AAF-C2378FDC4329")) {
                EBData response = new EBData(characteristic.getValue());
                mtuSize =  response.getByteAtIndex(0);
                triggerConnectionChangedCallback();
            }
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

    public void write(String characteristicUUID, Object value, ExtendaBLEResultCallback updateCallback) {

        Log.d(logTag, "Write Started for " + characteristicUUID);

        for (BluetoothGatt gatt : connectedCharacteristics.keySet()) {
            for (BluetoothGattCharacteristic characteristic : connectedCharacteristics.get(gatt)) {
                Log.d(logTag, "Write for " + characteristicUUID + "Against List Item" + characteristic.getUuid().toString());

                if (characteristic.getUuid().toString().toUpperCase().equals(characteristicUUID)) {

                    Log.d(logTag, characteristic.getUuid().toString() + " - " +  characteristicUUID);

                    activeWriteTransations.putIfAbsent(gatt, new ArrayList<>());
                    List<EBTransaction> transactions = activeWriteTransations.get(gatt).stream().filter(p -> p.getCharacteristic().getUuid().equals(characteristic.getUuid())).collect(Collectors.toList());
                    EBTransaction transaction = null;

                    if (transactions.size() == 0) {
                        transaction = newWriteTransaction(characteristic, updateCallback);
                        transaction.setData(value);
                        activeWriteTransations.get(gatt).add(transaction);
                    }

                    if (transaction != null) if (transaction.getDataPackets().size() > 0) {
                        Log.d(logTag, "Initial Write Triggered");
                        characteristic.setValue(transaction.getDataPackets().get(0));
                        gatt.writeCharacteristic(characteristic);
                    }
                }
            }
        }
    }

    private void handleWriteTransationResponse(BluetoothGattCharacteristic characteristic, BluetoothGatt fromGatt) {
        if (activeWriteTransations.get(fromGatt) != null) {

            List<EBTransaction> transactions = activeWriteTransations.get(fromGatt).stream().filter(p -> p.getCharacteristic().getUuid().equals(characteristic.getUuid())).collect(Collectors.toList());

            if (transactions.size() != 0) {
                int index = activeWriteTransations.get(fromGatt).indexOf(transactions.get(0));
                EBTransaction transaction =  activeWriteTransations.get(fromGatt).get(index);

                if (transaction != null) {

                    transaction.receivedReceipt();

                    if (transaction.isComplete()) {

                        byte[] packet = transaction.nextPacket();
                        transaction.getCharacteristic().setValue(packet);
                        fromGatt.writeCharacteristic(characteristic);

                        transaction.getCompletionCallback().setResult(characteristic.getValue());
                        transaction.getCompletionCallback().call();
                        activeWriteTransations.get(fromGatt).remove(index);
                    }  else {
                        Log.d(logTag, "Write Next Packet Triggered");
                        byte[] packet = transaction.nextPacket();
                        transaction.getCharacteristic().setValue(packet);
                        fromGatt.writeCharacteristic(characteristic);
                    }
                }
            }
        }
    }

    private EBTransaction newWriteTransaction(BluetoothGattCharacteristic characteristic,
                                              ExtendaBLEResultCallback updateCallback) {

        EBTransaction.TransactionType transactionType = EBTransaction.TransactionType.WRITE;
        EBTransaction.TransactionDirection transactionDirection =  EBTransaction.TransactionDirection.CENTRAL_TO_PERIPHERAL;

        if (chunkedChracteristicUUIDS.contains(characteristic.getUuid())) {
            transactionType =  EBTransaction.TransactionType.WRITE_CHUNKABLE;
        }

        EBTransaction transaction = new EBTransaction(transactionType, transactionDirection, mtuSize);
        transaction.setCharacteristic(characteristic);
        transaction.setCompletionCallback(updateCallback);
        return transaction;
    }


    /**
     *  Read logic is as follows....
     */

    public void read(String charateristicUUID, ExtendaBLEResultCallback updateCallback) {

        for (BluetoothGatt gatt : connectedCharacteristics.keySet()) {
            for (BluetoothGattCharacteristic characteristic : connectedCharacteristics.get(gatt)) {

                if (characteristic.getUuid().toString().toUpperCase().equals(charateristicUUID)) {

                    activeReadTransations.putIfAbsent(gatt, new ArrayList<>());

                    List<EBTransaction> transactions = activeReadTransations.get(gatt).stream().filter(p -> p.getCharacteristic().getUuid().equals(characteristic.getUuid())).collect(Collectors.toList());

                    if (transactions.size() == 0) {
                        EBTransaction.TransactionType transactionType = EBTransaction.TransactionType.READ;
                        EBTransaction.TransactionDirection transactionDirection =  EBTransaction.TransactionDirection.CENTRAL_TO_PERIPHERAL;

                        if (chunkedChracteristicUUIDS.contains(characteristic.getUuid())) {
                            transactionType =  EBTransaction.TransactionType.READ_CHUNKABLE;
                        }


                        EBTransaction transaction = new EBTransaction(transactionType, transactionDirection, mtuSize);
                        transaction.setCharacteristic(characteristic);
                        transaction.setCompletionCallback(updateCallback);
                        activeReadTransations.get(gatt).add(transaction);
                    }
                }

                gatt.readCharacteristic(characteristic);
            }
        }
    }

    private void handleReadTransationResponse(BluetoothGattCharacteristic characteristic, BluetoothGatt fromGatt) {
        if (activeReadTransations.get(fromGatt) != null) {
            List<EBTransaction> transactions = activeReadTransations.get(fromGatt).stream().filter(p -> p.getCharacteristic().getUuid().equals(characteristic.getUuid())).collect(Collectors.toList());

            if (transactions.get(0) != null) {
                int index = activeReadTransations.get(fromGatt).indexOf(transactions.get(0));
                EBTransaction transaction =  activeReadTransations.get(fromGatt).get(index);

                if (transaction != null) {
                    transaction.receivedReceipt();
                    if (transaction.isComplete()) {
                        transaction.getCompletionCallback().setResult(characteristic.getValue());
                        transaction.getCompletionCallback().call();
                        activeReadTransations.get(fromGatt).remove(index);
                    }
                }
            }
        }
    }
}
