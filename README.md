# ExtendaBLE-Android

[![License](https://img.shields.io/badge/license-MIT-343434.svg)](/LICENSE.md)

![alt tag](/Documentation/extendable_header.png?raw=true)

## Introduction

**ExtendaBLE** provides a very flexible syntax for defining centrals and peripherals with ease. Following a blocks based builder approach you can easily create centrals, peripherals, associated services, characteristics, and define callbacks to listen for characteristic changes accordingly.

One of the unique features of **ExtendaBLE** is that it allows to bypass the limitations of the MTU size in communicating between devices. The library negotiates a common MTU size, and allows breaks down the data to be sent between devices into packets, which are then reconstructed by the receiving entity.

An iOS/OSX/tvOS library with support for packet based communication between Android and iOS/tvOs/OSX can be found here..  [ExtendaBLE](https://github.com/AntonTheDev/ExtendaBLE)

**NOTE: This is a work in Progress / Currently works with iOS/OSX/tvOS framework, looking for help to make a standalone library**

## Features

- [x] Blocks Syntax for Building Centrals and Peripherals
- [x] Callbacks for responding to, read and write, characteristic changes
- [x] Packet Based Payload transfer using negotiated MTU sizes
- [x] Characteristic Update Callbacks
- [x] Streamlined parsing for characteristic read operations

## Installation

* **Requirements** : Android Version TBD
* [Installation Instructions](/Documentation/installation.md)
* [Release Notes](/Documentation/release_notes.md)

## Communication

- If you **found a bug**, or **have a feature request**, open an issue.
- If you **need help** or a **general question**, use [Stack Overflow](http://stackoverflow.com/questions/tagged/extenda-ble). (tag 'extenda-ble')
- If you **want to contribute**, review the [Contribution Guidelines](/Documentation/CONTRIBUTING.md), and submit a pull request.

## Basic Setup

In configuring BLE the first step is to configure a unique UUID for the shared a for the service(s) and characteristic(s) to intercommunicate between the peripheral & central.

For the purposes of documentation, the following constants will be shared across the configuration examples

```java
private static final String dataServiceUUIDString               = "3C215EBB-D3EF-4D7E-8E00-A700DFD6E9EF";
private static final String dataServiceCharacteristicUUID       = "830FEB83-C879-4B14-92E0-DF8CCDDD8D8F";

```

If you are not familiar with how BLE works, please review the [Core Bluetooth Programming Guide](https://developer.apple.com/library/content/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/AboutCoreBluetooth/Introduction.html) before continuing.

### Peripheral Manager

In it's simplest form, the following is an example of how to configure peripheral using a simple blocks based syntax.

```java
peripheral = ExtendaBLE.newPeripheralManager(getApplicationContext(), manager -> {

    manager.addService(dataServiceUUIDString, service -> {

        service.addCharacteristic(dataServiceCharacteristicUUID, characteristic -> {
              characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE).setPermissions(PERMISSION_READ|PERMISSION_WRITE);
        });
    });
});
```

#### Begin Advertising

To start advertising services and their respective characteristics, just call on ``startAdvertising()`` on the peripheral created in the prior section.

```java
peripheral.startAdvertising();
```

#### Responding to Updates

If you would like to respond to characteristic updates on the peripheral when a central updates a value, define an ``onUpdate  { (data, error) in }`` per characteristic accordingly. When the Central finishes updating the value, the callback will be triggered.

```java
peripheral = ExtendaBLE.newPeripheralManager(getApplicationContext(), manager -> {

    manager.addService(dataServiceUUIDString, service -> {

        service.addCharacteristic(dataServiceCharacteristicUUID, characteristic -> {
              characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE).setPermissions(PERMISSION_READ|PERMISSION_WRITE);

              characteristic.setUpdateCallback(new ExtendaBLEResultCallback() {
                  @Override
                  public Void call()  {
                      /* Called whenever the value is updated by the CENTRAL */
                      Log.d(peripheralLogTag, "Reconstructed Value Did Not Match" + result.getString());

                      return null;
                  }
              });
        });
    });
});
```

#### Notifying Central

If you would like the peripheral to retain a connection for a specific characteristic, and notify the connected central manager when the value is updated, when configuring the properties, ensure to include the ``PROPERTY_NOTIFY`` CBCharacteristicProperty in the definition as follows.

```java
peripheral = ExtendaBLE.newPeripheralManager(getApplicationContext(), manager -> {

    manager.addService(dataServiceUUIDString, service -> {
        service.addCharacteristic(dataServiceCharacteristicUUID, characteristic -> {
              characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE|PROPERTY_NOTIFY).setPermissions(PERMISSION_READ|PERMISSION_WRITE);
        });
    });
});
```

### Central Manager

In it's simplest form, the following is an example of how to configure central manager using a simple blocks based syntax.

```java
central = ExtendaBLE.newCentralManager(getApplicationContext(), manager -> {

    manager.addService(dataServiceUUIDString, service -> {
        service.addCharacteristic(dataServiceCharacteristicUUID, characteristic -> {
            characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE).setPermissions(PERMISSION_READ | PERMISSION_WRITE);
        });
    });
});
```

#### Begin Scanning

Start scanning for peripheral(s) defined with the services, and their respective characteristics, just call on ``startScanning()`` on the central created in the prior section. The central will auto connect to the peripheral when found.

```java
central.startScanning();
```

#### Respond to Successful Connection

To perform a Read/Write upon connecting to a peripheral, define a callback as follows to be notified of the successful connection.

```java
central = ExtendaBLE.newCentralManager(getApplicationContext(), manager -> {

    manager.addService(dataServiceUUIDString, service -> {
        service.addCharacteristic(dataServiceCharacteristicUUID, characteristic -> {
            characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE).setPermissions(PERMISSION_READ | PERMISSION_WRITE);
        });
    });

}).setPeripheralConnectionCallback(() -> {
    /* Perform Read Transaction upon connecting */
    return null;
});
```

#### Responding to Update Notification

If you would like to retain a connection for a specific characteristic, and be notified by the peripheral when the value is updated, when configuring the properties, ensure to include the ``PROPERTY_NOTIFY`` CBCharacteristicProperty in the definition as follows, and create a call back to respond to the change.

```java

central = ExtendaBLE.newCentralManager(getApplicationContext(), manager -> {

    manager.addService(dataServiceUUIDString, service -> {
        service.addCharacteristic(dataServiceCharacteristicUUID, characteristic -> {
            characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE).setPermissions(PERMISSION_READ | PERMISSION_WRITE);

            characteristic.setUpdateCallback(new ExtendaBLEResultCallback() {
                @Override
                public Void call()  {
                    /* Called whenever the value is updated by the Peripheral */
                    Log.d(peripheralLogTag, "Reconstructed Value Did Not Match" + result.getString());

                    return null;
                }
            });
        });
    });
});
```

#### Perform Write

To perform a write for a specific characteristic for a connected peripheral, call the ``write(..)`` on the central, and with the content to write, and the characteristicUUID to write to. The callback will be triggered once the write is complete.  

```java
centralManager.write(dataServiceCharacteristicUUID, testValueString, new ExtendaBLEResultCallback() {
    @Override
    public Void call()  {
        /* Do something upon successful write operation */
        return null;
    }
});
```

#### Perform Read

To perform a read for a specific characteristic for a connected peripheral, call the ``read(..)`` on the central, and with the characteristicUUID to read. The callback will be triggered once the read is complete with a ``Result`` instance to extract the data for the response.

```java

central.read(dataServiceCharacteristicUUID, new ExtendaBLEResultCallback() {
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
```

### Packet Based Communication

BLE has a limitation as to how much data can be sent between devices relative to the MTU size. To enabled the ability for the central and peripheral to communicate characteristic data greater in size than this limitation, **ExtendaBLE** provides the ability to use packets to breakup and rebuild the data when communicating between devices.

To enable the ability to send data greater than the MTU limitation of BLE, set the ``packetsEnabled`` to true on both the peripheral and the central. This will ensure that when communications occurs, the data is broken up into individual packets which will be sent across and rebuilt once the operation is complete.  

```java
peripheral = ExtendaBLE.newPeripheralManager(getApplicationContext(), manager -> {

    manager.addService(dataServiceUUIDString, service -> {
        service.addCharacteristic(dataServiceCharacteristicUUID, characteristic -> {
              characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE);
              characteristic.setPermissions(PERMISSION_READ|PERMISSION_WRITE);
              characteristic.setPacketsEnabled(true);
        });
    });
});

central = ExtendaBLE.newCentralManager(getApplicationContext(), manager -> {

    manager.addService(dataServiceUUIDString, service -> {
        service.addCharacteristic(dataServiceCharacteristicUUID, characteristic -> {
            characteristic.setProperties(PROPERTY_READ|PROPERTY_WRITE);
            characteristic.setPermissions(PERMISSION_READ | PERMISSION_WRITE);
            characteristic.setPacketsEnabled(true);
        });
    });
});
```
