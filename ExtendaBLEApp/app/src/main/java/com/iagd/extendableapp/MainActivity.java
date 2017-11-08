package com.iagd.extendableapp;

import android.os.Build;
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

import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE;


public class MainActivity extends AppCompatActivity {

    enum DeviceType {
        CENTRAL,
        PERIPHERAL,
        BEAN_CENTRAL
    }

    private static DeviceType activityConfiguration = DeviceType.CENTRAL;

    private static String peripheralLogTag = "PeripheralManager";
    private static String centralLogTag = "CentralManager";

    private static final String testValueString = "Hello this is a faily long string to check how many bytes lets make this a lot longer even longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer an longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer and longer XXXXXXXXXXXXXXXX";
    private static final String dataServiceUUIDString               = "3C215EBB-D3EF-4D7E-8E00-A700DFD6E9EF";
    private static final String dataServiceCharacteristicUUID       = "830FEB83-C879-4B14-92E0-DF8CCDDD8D8F";

    private static final String beanScratchServiceUUIDKey           = "a495ff20-c5b1-4b44-b512-1370f02d74de";
    private static final String beanScratchCharacteristic1UUIDKey   = "a495ff21-c5b1-4b44-b512-1370f02d74de";
    private static final String beanScratchCharacteristic2UUIDKey   = "a495ff22-c5b1-4b44-b512-1370f02d74de";
    private static final String beanScratchCharacteristic3UUIDKey   = "a495ff23-c5b1-4b44-b512-1370f02d74de";
    private static final String beanScratchCharacteristic4UUIDKey   = "a495ff24-c5b1-4b44-b512-1370f02d74de";
    private static final String beanScratchCharacteristic5UUIDKey   = "a495ff25-c5b1-4b44-b512-1370f02d74de";

    private EBCentralManager centralManager;
    private EBPeripheralManager peripheralManager;

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissionsAndScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        switch (activityConfiguration) {
            case CENTRAL:
                centralManager.close();
                break;
            case PERIPHERAL:
                peripheralManager.close();
                break;
            case BEAN_CENTRAL:
                centralManager.close();
                break;
        }

        Log.d("MainActivity", "destroy");


        super.onDestroy();
    }

    public void startManager() {
        switch (activityConfiguration) {
            case CENTRAL:
                startCentrallManager();
                break;
            case PERIPHERAL:
                startPeripheralManager();
                break;
            case BEAN_CENTRAL:
                startBeanCentralManager();
                break;
        }
    }

    public void startCentrallManager() {

        centralManager = ExtendaBLE.newCentralManager(getApplicationContext(), manager -> {

            manager.addService(dataServiceUUIDString, service -> {

                service.addCharacteristic(dataServiceCharacteristicUUID, characteristic -> {

                    characteristic.setProperties(PROPERTY_READ | PROPERTY_WRITE | PROPERTY_NOTIFY);
                    characteristic.setPermissions(PERMISSION_READ | PERMISSION_WRITE);
                    characteristic.setPacketsEnabled(true);

                    characteristic.setUpdateCallback(new ExtendaBLEResultCallback() {
                        @Override
                        public Void call() {
                            Log.d(centralLogTag, "Central Data Updated" + result);
                            return null;
                        }
                    });
                });
            });

        }).setPeripheralConnectionCallback(() -> {

            performWrite();
            return null;

        }).startScanning();

    }

    public void startPeripheralManager() {

        peripheralManager = ExtendaBLE.newPeripheralManager(getApplicationContext(), manager -> {

            manager.addService(dataServiceUUIDString, service -> {
                service.addCharacteristic(dataServiceCharacteristicUUID, characteristic -> {

                    characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE|PROPERTY_NOTIFY).setPermissions(PERMISSION_READ|PERMISSION_WRITE);
                    characteristic.setPacketsEnabled(true);

                    characteristic.setUpdateCallback(new ExtendaBLEResultCallback() {
                        @Override
                        public Void call()  {

                            if (result.getString().equals(testValueString)) {
                                Log.d(peripheralLogTag, "Reconstructed Value Matched");
                            } else {
                                Log.d(peripheralLogTag, "Reconstructed Value Did Not Match" + result.getString());
                            }

                            return null;
                        }
                    });
                });
            });
        });

        peripheralManager.startAdvertising();
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

                    Log.d("CentralManager", "Read Complete " + resultString);
                }
                return null;
            }
        });
    }

    public void startBeanCentralManager() {

        centralManager = ExtendaBLE.newCentralManager(getApplicationContext(), manager -> {
            manager.setPeripheralName("Bean");
            manager.addService(beanScratchServiceUUIDKey, service -> {

                service.addCharacteristic(beanScratchCharacteristic1UUIDKey, characteristic -> {

                    characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE|PROPERTY_NOTIFY);
                    characteristic.setPermissions(PERMISSION_READ|PERMISSION_WRITE);
                    characteristic.setUpdateCallback(new ExtendaBLEResultCallback() {
                        @Override
                        public Void call()  {
                            Log.d(centralLogTag, "Central Data for beanScratchCharacteristic1UUIDKey Updated" + result);
                            return null;
                        }
                    });
                }).addCharacteristic(beanScratchCharacteristic2UUIDKey, characteristic -> {

                    characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE|PROPERTY_NOTIFY);
                    characteristic.setPermissions(PERMISSION_READ|PERMISSION_WRITE);
                    characteristic.setUpdateCallback(new ExtendaBLEResultCallback() {
                        @Override
                        public Void call()  {
                            Log.d(centralLogTag, "Central Data for beanScratchCharacteristic2UUIDKey Updated" + result);
                            return null;
                        }
                    });
                }).addCharacteristic(beanScratchCharacteristic3UUIDKey, characteristic -> {

                    characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE|PROPERTY_NOTIFY);
                    characteristic.setPermissions(PERMISSION_READ|PERMISSION_WRITE);
                    characteristic.setUpdateCallback(new ExtendaBLEResultCallback() {
                        @Override
                        public Void call()  {
                            Log.d(centralLogTag, "Central Data for beanScratchCharacteristic3UUIDKey Updated" + result);
                            return null;
                        }
                    });
                }).addCharacteristic(beanScratchCharacteristic4UUIDKey, characteristic -> {

                    characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE|PROPERTY_NOTIFY);
                    characteristic.setPermissions(PERMISSION_READ|PERMISSION_WRITE);
                    characteristic.setUpdateCallback(new ExtendaBLEResultCallback() {
                        @Override
                        public Void call()  {
                            Log.d(centralLogTag, "Central Data for beanScratchCharacteristic4UUIDKey Updated" + result);
                            return null;
                        }
                    });
                }).addCharacteristic(beanScratchCharacteristic5UUIDKey, characteristic -> {

                    characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE|PROPERTY_NOTIFY);
                    characteristic.setPermissions(PERMISSION_READ|PERMISSION_WRITE);
                    characteristic.setUpdateCallback(new ExtendaBLEResultCallback() {
                        @Override
                        public Void call()  {
                            Log.d(centralLogTag, "Central Data for beanScratchCharacteristic5UUIDKey Updated" + result);
                            return null;
                        }
                    });
                });
            });

        }).setPeripheralConnectionCallback(() -> {

            performBeanRead();
            return null;

        }).startScanning();
    }

    private void performBeanRead() {

        centralManager.read(beanScratchCharacteristic1UUIDKey, new ExtendaBLEResultCallback() {
            @Override
            public Void call()  {
                if (result != null) {
                    byte value = result.getByteAtIndex(0);
                    Log.d(centralLogTag, "Central Read beanScratchCharacteristic1UUIDKey w/ value " + value);
                }
                return null;
            }
        });

        centralManager.read(beanScratchCharacteristic2UUIDKey, new ExtendaBLEResultCallback() {
            @Override
            public Void call()  {
                if (result != null) {
                    byte value = result.getByteAtIndex(0);
                    Log.d(centralLogTag, "Central Read beanScratchCharacteristic2UUIDKey w/ value " + value);
                }
                return null;
            }
        });

        centralManager.read(beanScratchCharacteristic3UUIDKey, new ExtendaBLEResultCallback() {
            @Override
            public Void call()  {
                if (result != null) {
                    byte value = result.getByteAtIndex(0);
                    Log.d(centralLogTag, "Central Read beanScratchCharacteristic3UUIDKey w/ value " + value);
                }
                return null;
            }
        });

        centralManager.read(beanScratchCharacteristic4UUIDKey, new ExtendaBLEResultCallback() {
            @Override
            public Void call()  {
                if (result != null) {
                    byte value = result.getByteAtIndex(0);
                    Log.d(centralLogTag, "Central Read beanScratchCharacteristic4UUIDKey w/ value " + value);
                }
                return null;
            }
        });

        centralManager.read(beanScratchCharacteristic5UUIDKey, new ExtendaBLEResultCallback() {
            @Override
            public Void call()  {
                if (result != null) {
                    byte value = result.getByteAtIndex(0);
                    Log.d(centralLogTag, "Central Read beanScratchCharacteristic5UUIDKey w/ value " + value);
                }
                return null;
            }
        });
    }

    public void checkPermissionsAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
            } else {
                startManager();
            }
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
            }
        }
    }
}
