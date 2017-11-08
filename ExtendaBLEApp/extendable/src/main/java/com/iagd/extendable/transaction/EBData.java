package com.iagd.extendable.transaction;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class EBData {

    private static String logTag = "TransactionResult";

    private byte[] dataBytes;
    private short mtuSize;

    private ArrayList<byte[]> dataPackets;

    public EBData(ArrayList<byte[]> dataPackets) {
        this.dataPackets = dataPackets;
        this.dataBytes = reconstructedData();
    }

    public EBData(Object value, short mtuSize) {
        this.mtuSize = mtuSize;
      //  EBData request = null;

        if (value.getClass().equals(String.class)) {
            configure((String) value, mtuSize);
        } else if (value.getClass().equals(Byte.class)) {
            configure((byte) value, mtuSize);
        } else if (value.getClass().equals(Short.class)) {
            configure((short) value, mtuSize);
        } else if (value.getClass().equals(Integer.class)) {
            configure((int) value, mtuSize);
        } else if (value.getClass().equals(Long.class)) {
            configure((long) value, mtuSize);
        }

       // if (request != null) {
       //     dataPackets = request.chunckedArray();
       // }
    }

    public EBData(byte[] bytes) {
        dataBytes = bytes;
    }

    public EBData(byte[] bytes, short mtuSize) {
        dataBytes = bytes;
        this.mtuSize = mtuSize;
    }

    public byte[] getData() {
        return dataBytes;
    }

    private void configure(byte byteValue, short mtuSize) {
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES);
        buffer.put(byteValue);
        this.dataBytes =  buffer.array();
        this.mtuSize = mtuSize;
    }

    private void configure(short shortValue, short mtuSize) {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        buffer.putShort(shortValue);
        this.dataBytes =  buffer.array();
        this.mtuSize = mtuSize;
    }

    private void configure(int intValue, short mtuSize) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(intValue);
        this.dataBytes =  buffer.array();
        this.mtuSize = mtuSize;
    }

    private void configure(long longValue, short mtuSize) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(longValue);
        this.dataBytes =  buffer.array();
        this.mtuSize = mtuSize;
    }

    private void configure(String stringValue, short mtuSize) {
        this.dataBytes = stringValue.getBytes(StandardCharsets.UTF_8);
        this.mtuSize = mtuSize;
    }

    private byte[] reconstructedData() {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(out);

        for (byte[] packet : dataPackets) {

            byte[] packetBytes = Arrays.copyOfRange(packet, 4, packet.length);
            try {
                dout.write(packetBytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return out.toByteArray();
    }

    public ArrayList<byte[]> chunckedArray() {

        ArrayList<byte[]> dataPackets = new ArrayList<>();

        int packetSize = mtuSize - 4;
        int length = dataBytes.length;

        Log.e(logTag, "Data Length:" + length);
        int offset = 0;
        short totalPackets  = (short) ((dataBytes.length / packetSize) + (dataBytes.length % packetSize > 0 ? 1 : 0));
        short currentCount = 0;

        while (offset < length) {
            currentCount += 1;

            int currentPacketSize = ((length - offset) > packetSize) ? packetSize : (length - offset);
            byte[] block = Arrays.copyOfRange(dataBytes, offset, offset + currentPacketSize);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(out);

            Log.d(logTag, currentCount + " / " + totalPackets);

            try {
                dout.writeShort(currentCount);
                dout.writeShort(totalPackets);
                dout.write(block);
            } catch (IOException e) {
                e.printStackTrace();
            }

            byte[] packet = out.toByteArray();

            dataPackets.add(packet);
            offset += currentPacketSize;
        }

        return dataPackets;
    }

    public byte getByteAtIndex(int index) {
        try {
            return ByteBuffer.wrap(dataBytes).order(ByteOrder.BIG_ENDIAN).get(index);
        } catch(IndexOutOfBoundsException e) {
            Log.e(logTag, "getByteAtIndex Exception Thrown :" + e);
        }

        return 0;
    }

    public short getShortAtIndex(int index) {
        try {
            return ByteBuffer.wrap(dataBytes).order(ByteOrder.BIG_ENDIAN).getShort(index);
        } catch(IndexOutOfBoundsException e) {
            Log.e(logTag, "getShortAtIndex Exception Thrown :" + e);
        }

        return 0;
    }

    public int getIntAtIndex(int index) {
        try {
            return ByteBuffer.wrap(dataBytes).order(ByteOrder.BIG_ENDIAN).getInt(index);
        } catch(IndexOutOfBoundsException e) {
            Log.e(logTag, "getIntAtIndex Exception Thrown :" + e);
        }

        return 0;
    }

    public long getLongAtIndex(int index) {
        try {
            return ByteBuffer.wrap(dataBytes).order(ByteOrder.BIG_ENDIAN).getLong(index);
        } catch(IndexOutOfBoundsException e) {
            Log.e(logTag, "getLongAtIndex Exception Thrown :" + e);
        }

        return 0;
    }

    public String getString() {
        return  new String(dataBytes, StandardCharsets.UTF_8);
    }
}
