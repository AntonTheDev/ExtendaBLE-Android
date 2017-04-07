package com.iagd.extendable.transaction;

import android.bluetooth.BluetoothGattCharacteristic;

import com.iagd.extendable.result.ExtendaBLEResultCallback;

import java.util.ArrayList;

/**
 * Created by Anton on 4/5/17.
 */

public class EBTransaction {

    public enum TransactionDirection {
        CENTRAL_TO_PERIPHERAL,
        PERIPHERAL_TO_CENTRAL
    }

    public enum TransactionType {
        READ,
        READ_CHUNKABLE,
        WRITE,
        WRITE_CHUNKABLE
    }

    private BluetoothGattCharacteristic characteristic;

    private Object value;
    private byte[] data;

    private TransactionType type;
    private TransactionDirection direction;

    private short mtuSize = 20;
    private int totalPackets = 0;

    private ExtendaBLEResultCallback completionCallback;

    private int activeResponseCount = 1;
    private ArrayList<byte[]> dataPackets = new ArrayList<byte[]>();

    public EBTransaction(TransactionType type, TransactionDirection direction, short mtuSize) {
        this.type = type;
        this.direction = direction;
        this.mtuSize = mtuSize;
    }

    public ArrayList<byte[]> getDataPackets() {
        return this.dataPackets;
    }

    public void setData(Object value) {
        this.value = value;

        if (isChunkable()) {

            EBData request = null;

            if (value.getClass().equals(String.class)) {
                request = new EBData((String) value, mtuSize);
            } else if (value.getClass().equals(Byte.class)) {
                request = new EBData((byte) value, mtuSize);
            } else if (value.getClass().equals(Short.class)) {
                request = new EBData((short) value, mtuSize);
            } else if (value.getClass().equals(Integer.class)) {
                request = new EBData((int) value, mtuSize);
            } else if (value.getClass().equals(Long.class)) {
                request = new EBData((long) value, mtuSize);
            }

            if (request != null) {
                dataPackets = request.chunckedArray();
                totalPackets = dataPackets.size();
            }

        } else {
            if (data != null) {
                dataPackets.add(data);
            }
        }
    }

    public byte[] getData() {
        if (isChunkable()) {
            /* reconstruct Data Here */
        } else if (dataPackets.size() == 1) {
            return dataPackets.get(0);
        }

        return null;
    }

    public void receivedReceipt() {
        activeResponseCount = activeResponseCount + 1;
    }

    public byte[] nextPacket() {
        return dataPackets.get(activeResponseCount - 1);
    }

    public void sentReceipt() {
        activeResponseCount = activeResponseCount + 1;
    }

    public void setCompletionCallback(ExtendaBLEResultCallback callback) {
        this.completionCallback = callback;
    }

    public ExtendaBLEResultCallback getCompletionCallback() {
        return this.completionCallback;
    }

    public void setCharacteristic(BluetoothGattCharacteristic characteristic) {
        this.characteristic = characteristic;
    }

    public BluetoothGattCharacteristic getCharacteristic() {
       return this.characteristic;
    }

    public void appendPacket(byte[] dataPacket) {

        if(type == TransactionType.READ_CHUNKABLE || type == TransactionType.WRITE_CHUNKABLE) {
            /* read and set total packets here */
            // totalPackets = dataPacket.totalPackets
        }

        dataPackets.add(dataPacket);
    }

    public Boolean isComplete() {
        System.out.println( "SEND : " + activeResponseCount + " / " + totalPackets);
        if (isChunkable()) {
            return totalPackets == activeResponseCount;
        }
        return (activeResponseCount == 1);
    }

    private Boolean isChunkable() {
        return (type == TransactionType.READ_CHUNKABLE || type == TransactionType.WRITE_CHUNKABLE);
    }
}
