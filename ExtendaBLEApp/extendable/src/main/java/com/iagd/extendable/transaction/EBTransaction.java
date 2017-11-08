package com.iagd.extendable.transaction;

import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import com.iagd.extendable.result.ExtendaBLEResultCallback;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

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

    private byte[] data;

    private TransactionType type;

    private short mtuSize = 20;
    private int totalPackets = 0;

    private ExtendaBLEResultCallback completionCallback;

    private int activeResponseCount = 0;
    private ArrayList<byte[]> dataPackets = new ArrayList<>();

    public EBTransaction(TransactionType type, TransactionDirection direction, short mtuSize) {
        this.type = type;
        this.mtuSize = mtuSize;

        if (this.type == TransactionType.READ || this.type == TransactionType.WRITE) {
            totalPackets = 1;
        }
    }

    public ArrayList<byte[]> getDataPackets() {
        return this.dataPackets;
    }

    public int getActiveResponseCount() {
        return activeResponseCount;
    }

    public int getTotalPacketCount() {
        return totalPackets;
    }

    public void setData(byte[] value, short mtuSize) {

        if (isPacketBased()) {
            this.mtuSize = mtuSize;

            EBData request = new EBData(value, mtuSize);

                dataPackets = request.chunckedArray();
                totalPackets = dataPackets.size();

        } else {
            if (data != null) {
                dataPackets.add(data);
            }
        }
    }

    public void setData(Object value) {

        if (isPacketBased()) {

            EBData request = new EBData(value, mtuSize);

            dataPackets = request.chunckedArray();
            totalPackets = dataPackets.size();

        } else {
            if (data != null) {
                dataPackets.add(data);
            }
        }
    }

    public byte[] getData() {
        if (isPacketBased()) {
            EBData data = new EBData(dataPackets);
            return data.getData();
        } else if (dataPackets.size() == 1) {
            return dataPackets.get(0);
        }

        return null;
    }

    public void processTransaction() {
        activeResponseCount = activeResponseCount + 1;
    }

    public byte[] nextPacket() {
        return dataPackets.get(activeResponseCount - 1);
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
            totalPackets = ByteBuffer.wrap(dataPacket).order(ByteOrder.BIG_ENDIAN).getShort(2);
        }

        dataPackets.add(dataPacket);
    }

    public Boolean isComplete() {
        String logTag = "Transaction";
        Log.d(logTag, "IS COMPLETE : " + activeResponseCount + " / " + totalPackets);
        return totalPackets == activeResponseCount;
    }

    private Boolean isPacketBased() {
        return (type == TransactionType.READ_CHUNKABLE || type == TransactionType.WRITE_CHUNKABLE);
    }
}
