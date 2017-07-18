package com.wolfcstech.mediacodecdemo;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by hanpfei0306 on 17-7-14.
 */

public class StreamReceiver {
    private static final String TAG = "StreamReceiver";

    private static final int MAX_UDP_PACKET_SIZE = 65536;

    private volatile boolean mStop = false;

    private byte[] mRecvBuf = new byte[MAX_UDP_PACKET_SIZE];

    private StreamDataReceivedListener mDataReceivedListener;

    public void setDataReceivedListener(StreamDataReceivedListener dataReceivedListener) {
        mDataReceivedListener = dataReceivedListener;
    }

    public void stop() {
        mStop = true;
    }

    public void requestStreamData(String serverAddr, int port) {
        DatagramSocket client = null;
        try {
            client = new DatagramSocket();
            client.setSoTimeout(5000);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        String sendStr = "Hello! I'm Client";
        byte[] sendBuf = sendStr.getBytes();
        InetAddress addr = null;
        try {
            addr = InetAddress.getByName(serverAddr);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, addr, port);
        try {
            client.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }

        DatagramPacket recvPacket = new DatagramPacket(mRecvBuf, mRecvBuf.length);
        while (!mStop) {
            try {
                client.receive(recvPacket);
                if (mDataReceivedListener != null) {
                    int index = getIndex(recvPacket.getData(), recvPacket.getOffset());
                    mDataReceivedListener.onStreamDataReceived(index, recvPacket.getData(),
                            recvPacket.getOffset() + 4, recvPacket.getLength() - 4);
                }
            } catch (SocketTimeoutException ste) {
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        String recvStr = new String(recvPacket.getData(), 0, recvPacket.getLength());
//        Log.i(TAG, "收到:" + recvStr);
        client.close();
    }

    private int getIndex(byte[] data, int offset) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, 4);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.asIntBuffer();
        int index = bb.getInt();

        return index;
    }

    public interface StreamDataReceivedListener {
        void onStreamDataReceived(int index, byte[]data, int offset, int size);
    }
}
