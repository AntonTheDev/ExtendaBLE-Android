package com.iagd.extendableapp;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;


import com.iagd.extendable.ExtendaBLE;
import com.iagd.extendable.manager.EBCentralManager;
import com.iagd.extendable.manager.EBPeripheralManager;
import com.iagd.extendable.result.ExtendaBLEResultCallback;

import java.util.concurrent.Callable;

import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattDescriptor.PERMISSION_WRITE;

public class MainActivity extends AppCompatActivity {

    private static String peripheralLogTag = "PeripheralManager";
    private static String centralLogTag = "CentralManager";

    private static final String testValueString = "Hello this is a faily long string to check how many bytes lets make this a lot longer even longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer XXXXXXXXXXXXXXXX";
    private EBCentralManager centralManager;

    private EBPeripheralManager peripheralManager;

    private static final String dataServiceUUIDString = "3C215EBB-D3EF-4D7E-8E00-A700DFD6E9EF";
    private static final String dataServiceCharacteristicUUID = "830FEB83-C879-4B14-92E0-DF8CCDDD8D8F";

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermissionsAndScan();
    }

    public void startManager() {
        startPeripheralManager();
       // startCentrallManager();
    }

    public void startCentrallManager() {

        centralManager = ExtendaBLE.newCentralManager(manager -> {

            manager.addService(dataServiceUUIDString, service -> {
                service.addCharacteristic(dataServiceCharacteristicUUID, characteristic -> {

                    characteristic.setProperties(new int[]{ PROPERTY_READ, PROPERTY_WRITE, PROPERTY_NOTIFY });
                    characteristic.setPermissions(new int[]{ PERMISSION_READ, PERMISSION_WRITE });
                    characteristic.setChunkingEnabled(true);

                    characteristic.setUpdateCallback(new ExtendaBLEResultCallback() {
                        @Override
                        public Void call()  {
                            Log.d(centralLogTag, "Central Data Updated" + result);
                            return null;
                        }
                    });
                });
            });
        });

        centralManager.setPeripheralConnectionCallback(new Callable() {
            @Override
            public Object call() throws Exception {
                performWrite();
                return null;
            }
        });

        centralManager.startScanningInApplicationContext(getApplicationContext());
    }

    public void startPeripheralManager() {

        peripheralManager = ExtendaBLE.newPeripheralManager(manager -> {

            manager.addService(dataServiceUUIDString, service -> {
                service.addCharacteristic(dataServiceCharacteristicUUID, characteristic -> {

                    characteristic.setProperties(new int[]{ PROPERTY_READ, PROPERTY_WRITE, PROPERTY_NOTIFY });
                    characteristic.setPermissions(new int[]{ PERMISSION_READ, PERMISSION_WRITE });
                    characteristic.setChunkingEnabled(true);

                    characteristic.setUpdateCallback(new ExtendaBLEResultCallback() {
                        @Override
                        public Void call()  {
                            System.out.println( result.getString());
                            if (result.getString().equals(testValueString)) {
                                Log.d(peripheralLogTag, "Reconstructed Values Did Match");
                            } else {
                                Log.d(peripheralLogTag, "Reconstructed Values Did Not Match");
                            }

                            Log.d(peripheralLogTag, "Peripheral Received Value" + result.getString());
                            return null;
                        }
                    });
                });
            });
        });

        peripheralManager.startAdvertisingInApplicationContext(getApplicationContext());
    }

    private void performWrite() {
        centralManager.write(dataServiceCharacteristicUUID, testValueString, new ExtendaBLEResultCallback() {
            @Override
            public Void call()  {
                System.out.println("Write Complete");
                performRead();
                return null;
            }
        });
    }

    private void performRead() {
        centralManager.read(dataServiceCharacteristicUUID, new ExtendaBLEResultCallback() {
            @Override
            public Void call()  {
                if (result != null) {
                    String resultString = result.getString();

                    if (resultString.equals(testValueString)) {
                        Log.d("CentralManager", "Read Values Match");
                    }

                    Log.d("Centra Manager", "Read Complete " + resultString);
                }
                return null;
            }
        });
    }

    public void checkPermissionsAndScan() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        } else {
            startManager();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("coarse location permission granted");
                    startManager();
                }  else {
                    System.out.println("coarse location permission not granted");
                }
                return;
            }
        }
    }
}
