package com.iagd.extendable.transaction;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Anton on 4/6/17.
 */

public class EBData {

    private byte[] dataBytes;
    private short mtuSize;

    public EBData(byte[] bytes) {
        dataBytes = bytes;
    }

    public EBData(byte byteValue, short mtuSize) {
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES);
        buffer.putLong(byteValue);
        this.dataBytes =  buffer.array();
        this.mtuSize = mtuSize;
    }

    public EBData(short shortValue, short mtuSize) {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        buffer.putLong(shortValue);
        this.dataBytes =  buffer.array();
        this.mtuSize = mtuSize;
    }

    public EBData(int intValue, short mtuSize) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putLong(intValue);
        this.dataBytes =  buffer.array();
        this.mtuSize = mtuSize;
    }

    public EBData(long longValue, short mtuSize) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(longValue);
        this.dataBytes =  buffer.array();
        this.mtuSize = mtuSize;
    }

    public EBData(String stringValue, short mtuSize) {
        this.dataBytes = stringValue.getBytes(StandardCharsets.UTF_8);
        this.mtuSize = mtuSize;
    }

    public ArrayList<byte[]> chunckedArray() {

        int pos = 0;
        int remaining;

        ArrayList<byte[]> dataPackets = new ArrayList<>();


        int packetSize = mtuSize - 4;
        int length = dataBytes.length;
        int offset = 0;
        short totalPackets  = (short) ((dataBytes.length / packetSize) + (dataBytes.length % packetSize > 0 ? 1 : 0));
        short currentCount = 0;


        while (offset < length) {
            currentCount += 1;

            int currentPacketSize = ((length - offset) > packetSize) ? packetSize : (length - offset);

            //int currentPacketSize = Math.min((length - offset), packetSize);
            byte[] block = Arrays.copyOfRange(dataBytes, offset, offset + currentPacketSize);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(out);

            System.out.println(currentCount + " / " + totalPackets);

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
        byte byteValue = ByteBuffer.wrap(dataBytes).order(ByteOrder.BIG_ENDIAN).get(index);
        return byteValue;
    }

    public short getShortAtIndex(int index) {
        short shortValue = ByteBuffer.wrap(dataBytes).order(ByteOrder.BIG_ENDIAN).getShort(index);
        return shortValue;
    }

    public int getIntAtIndex(int index) {
        int intValue = ByteBuffer.wrap(dataBytes).order(ByteOrder.BIG_ENDIAN).getInt(index);
        return intValue;
    }

    public long getLongAtIndex(int index) {
        long longValue = ByteBuffer.wrap(dataBytes).order(ByteOrder.BIG_ENDIAN).getLong(index);
        return longValue;
    }

    public String getString() {
        return new String(dataBytes, StandardCharsets.UTF_8);
    }
}
