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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.iagd.extendable.result.ExtendaBLEResultCallback;
import com.iagd.extendable.transaction.EBTransaction;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_FIRST_MATCH;

public class EBCentralManager {

    private static final String mtuServiceCharacteristicUUID = "37CD1740-6822-4D85-9AAF-C2378FDC4329";
    private static final String logTag = "CentralManager";

    private Context applicationContext;

    private BluetoothManager bluetoothManager;
    private BluetoothLeScanner mBTScanner;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mConnectedGatt = null;
    private short mtuSize = 20;

    public ArrayList<UUID> registeredServiceUUIDS = new ArrayList<>();
    public ArrayList<UUID> registeredChracteristicUUIDS = new ArrayList<>();
    public ArrayList<UUID> chunkedChracteristicUUIDS = new ArrayList<>();
    public HashMap<UUID , ExtendaBLEResultCallback> updateCallbacks = new HashMap<>();
    public String peripheralName;

    private HashMap<BluetoothGatt , ArrayList<BluetoothGattCharacteristic>> connectedCharacteristics = new HashMap<>();

    private HashMap<BluetoothGatt , ArrayList<EBTransaction>> activeReadTransactions = new HashMap<>();
    private HashMap<BluetoothGatt , ArrayList<EBTransaction>> activeWriteTransactions = new HashMap<>();

    private ArrayList<Runnable> pendingReadTransactions = new ArrayList<>();

    private Boolean adapterWasReset = false;

    private Boolean getAdapterWasReset() {
        return adapterWasReset;
    }

    private void setAdapterWasReset(Boolean adapterWasReset) {
        this.adapterWasReset = adapterWasReset;
    }

    private Callable peripheralConnectionChange;

    public void setApplicationContext(Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Start the scanner by passing it the application context at the start
     */

    public EBCentralManager startScanning() {

        bluetoothManager = (BluetoothManager) this.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }


        if (!mBluetoothAdapter.isEnabled()) {
            setAdapterWasReset(true);
            resetAdapter();
        } else {
            startScanningTask();
        }

        return this;
    }

    private void stopScanning() {
        Log.d(logTag, "Stop Scanning");
        AsyncTask.execute(() -> mBTScanner.stopScan(leScanCallback));
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(logTag, "Scan Results Triggered, Connecting to : " + result.getDevice().getName());

            mConnectedGatt = result.getDevice().connectGatt(applicationContext, false, leGattCallback);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d(logTag, "Scan Failed / Resetting with ErrorCode: " + errorCode);
            super.onScanFailed(errorCode);
            setAdapterWasReset(false);
            resetAdapter();
        }
    };

    private void resetAdapter() {

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        applicationContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (action != null && action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                    switch (state) {
                        case BluetoothAdapter.STATE_OFF:
                            setAdapterWasReset(true);
                            mBluetoothAdapter.enable();
                            break;
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            break;
                        case BluetoothAdapter.STATE_ON:

                            if (getAdapterWasReset()) {
                                startScanningTask();
                            } else {
                                mBluetoothAdapter.disable();
                            }
                            break;
                        case BluetoothAdapter.STATE_TURNING_ON:
                            break;
                    }
                }
            }
        }, filter);

        if (mBluetoothAdapter.isEnabled()) {
            setAdapterWasReset(false);
            mBluetoothAdapter.disable();
        } else {
            setAdapterWasReset(true);
            mBluetoothAdapter.enable();
        }
    }

    private void startScanningTask() {

        AsyncTask.execute(() -> {
            Log.d(logTag, "Configured Scan Settings / Filters");

            List<ScanFilter> filters = new ArrayList<>();

            if (peripheralName != null) {
                ScanFilter filter = new ScanFilter.Builder().setDeviceName(peripheralName).build();
                filters.add(filter);
            }

            for (UUID uuid : registeredServiceUUIDS){
                ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(uuid)).build();
                filters.add(filter);
            }


            ScanSettings settings = null;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                settings = new ScanSettings.Builder().setCallbackType(CALLBACK_TYPE_FIRST_MATCH).setReportDelay((long) 0.33).build();
            }

            mBluetoothAdapter = bluetoothManager.getAdapter();
            mBTScanner = mBluetoothAdapter.getBluetoothLeScanner();

            mBTScanner.startScan(filters, settings, leScanCallback);
        });
    }

    public void close() {
        if (mConnectedGatt == null) {
            return;
        }
        mConnectedGatt.close();
        mConnectedGatt = null;
    }


    /**
     * Handle Service Discovery
     */

    private void handleServicesDiscovered(BluetoothGatt gatt, int status) {
        Log.d(logTag, "Services Discovered For : " + gatt.getDevice().getName() +  " w/ Status : " + status);

        for (BluetoothGattService service : gatt.getServices()) {
            Log.d(logTag, "Services Discovered For : " + gatt.getDevice().getName() +  " w/ UUID : " + service.getUuid());

            if (registeredServiceUUIDS.contains(service.getUuid())) {
                Log.d(logTag, "Services matched registered UUID(s) " + service.getUuid());
                Log.d(logTag, "Characteristics : ");

                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    Log.d(logTag, "       - " + characteristic.getUuid());

                  //  connectedCharacteristics.putIfAbsent(gatt, new ArrayList<>());
                    if (!connectedCharacteristics.containsKey(gatt)) {
                        connectedCharacteristics.put(gatt, new ArrayList<>());
                    }

                    connectedCharacteristics.get(gatt).add(characteristic);

                    configureMTUNotificationIfFound(gatt, characteristic);
                }
            }
        }
        if (connectedCharacteristics.get(gatt).size() == registeredChracteristicUUIDS.size()) {
            triggerConnectionChangedCallback();
        }
    }

    private void configureMTUNotificationIfFound(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

        if (characteristic.getUuid().toString().toUpperCase().equals(mtuServiceCharacteristicUUID.toUpperCase())) {
            Log.d(logTag, "Requested MTU 500");
             gatt.requestMtu(500);
        }
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
                mConnectedGatt.discoverServices();
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
            handleReadTransactionResponse(gatt, characteristic);
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
     *  Write logic is as follows, whenever a write transaction is initiated
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

                if (characteristic.getUuid().toString().toUpperCase().equals(characteristicUUID.toUpperCase())) {

                    if (!activeWriteTransactions.containsKey(gatt)) {
                        activeWriteTransactions.put(gatt, new ArrayList<>());
                    }

                    ArrayList<EBTransaction> transactions = new ArrayList<>();

                    for (EBTransaction transaction : activeWriteTransactions.get(gatt)) {
                        if (transaction.getCharacteristic().getUuid().equals(characteristic.getUuid())) {
                            transactions.add(transaction);
                        }
                    }

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

            ArrayList<EBTransaction> transactions = new ArrayList<>();

            for (EBTransaction transaction : activeWriteTransactions.get(fromGatt)) {
                if (transaction.getCharacteristic().getUuid().equals(characteristic.getUuid())) {
                    transactions.add(transaction);
                }
            }

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
                    } else {
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

        Log.d(logTag, "Bootstrap Read Transaction for " + characteristicUUID);

        for (BluetoothGatt gatt : connectedCharacteristics.keySet()) {
            for (BluetoothGattCharacteristic characteristic : connectedCharacteristics.get(gatt)) {
                if (characteristic.getUuid().toString().toUpperCase().equals(characteristicUUID.toUpperCase())) {

                    if (!activeReadTransactions.containsKey(gatt)) {
                        activeReadTransactions.put(gatt, new ArrayList<>());
                    }

                    ArrayList<EBTransaction> transactions = new ArrayList<>();

                    for (EBTransaction transaction : activeReadTransactions.get(gatt)) {
                        if (transaction.getCharacteristic().getUuid().equals(characteristic.getUuid())) {
                            transactions.add(transaction);
                        }
                    }

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

                    scheduleReadOnCharacteristic(gatt, characteristic);
                    return;
                }
            }
        }
    }

    private void handleReadTransactionResponse(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

        Log.d(logTag, "Received Response for " + characteristic.getUuid().toString());

        if (activeReadTransactions.get(gatt) != null) {

            ArrayList<EBTransaction> transactions = new ArrayList<>();

            for (EBTransaction transaction : activeReadTransactions.get(gatt)) {
                if (transaction.getCharacteristic().getUuid().equals(characteristic.getUuid())) {
                    transactions.add(transaction);
                }
            }

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
                        triggerNextReadOperationIfNeeded();
                    } else {
                        Log.d(logTag, "Read Packet Received " + transaction.getActiveResponseCount() + " / " + transaction.getTotalPacketCount());
                        gatt.readCharacteristic(characteristic);
                    }
                }
            }
        }
    }

    private void scheduleReadOnCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

        if (pendingReadTransactions.size() > 0) {
            pendingReadTransactions.add(() -> gatt.readCharacteristic(characteristic));
        } else {
            pendingReadTransactions.add(() -> gatt.readCharacteristic(characteristic));

            pendingReadTransactions.get(0).run();
        }
    }

    private void triggerNextReadOperationIfNeeded() {

        pendingReadTransactions.remove(0);

        if (pendingReadTransactions.size() > 0) {
            pendingReadTransactions.get(0).run();
        }
    }
}
