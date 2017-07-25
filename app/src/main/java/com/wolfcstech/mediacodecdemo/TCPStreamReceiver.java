package com.wolfcstech.mediacodecdemo;

import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by hanpfei0306 on 17-7-18.
 */

public class TCPStreamReceiver {
    private static final String TAG = "TCPStreamReceiver";

    private static final int MAX_UDP_PACKET_SIZE = 128 * 1024;

    private volatile boolean mStop = false;

    private byte[] mRecvBuf = new byte[MAX_UDP_PACKET_SIZE];
    private byte[] mTmpSwapBuf = new byte[MAX_UDP_PACKET_SIZE];

    private StreamReceivedListener mDataReceivedListener;

    private long mTotalRecvBytes = 0;

    public void setDataReceivedListener(StreamReceivedListener dataReceivedListener) {
        mDataReceivedListener = dataReceivedListener;
    }

    public void stop() {
        mStop = true;
    }

    private void connectAndRequestVideo(SocketAddress socketAddress) throws IOException {
        Socket client = null;
        try {
            client = new Socket();
            client.setSoTimeout(0);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        client.connect(socketAddress);

        Controlmsg.VideoRoleRegistration.Builder videoRoleRegistrationBuild = Controlmsg.VideoRoleRegistration.newBuilder();
        videoRoleRegistrationBuild.setRole(Message.VideoRoleConsumer);
        videoRoleRegistrationBuild.setVideoStreamId("1234567890");
        Controlmsg.VideoRoleRegistration videoRoleRegistration = videoRoleRegistrationBuild.build();

        Message.MessageHdr msgheader = new Message.MessageHdr();
        msgheader.message_type = Message.MessageTypeVideoRoleRegistraction;
        msgheader.message_data_length = videoRoleRegistration.getSerializedSize();

        ByteBuffer sendbuffer = ByteBuffer.allocate(16);
        sendbuffer.order(ByteOrder.BIG_ENDIAN);
        sendbuffer.putInt(msgheader.message_type);
        sendbuffer.putInt(msgheader.message_data_length);

        sendbuffer.flip();
        byte[] sendBuf = new byte[8];
        sendbuffer.get(sendBuf);

        OutputStream outputStream = null;
        InputStream inputStream = null;

        outputStream = client.getOutputStream();
        outputStream.write(sendBuf);

        sendBuf = videoRoleRegistration.toByteArray();
        outputStream.write(sendBuf);

        inputStream = client.getInputStream();

        boolean remainBytes = false;
        int bytesRead = 0;
        try {
            while (!mStop) {
                int readcount = 0;
                if (!remainBytes) {
                    readcount = inputStream.read(mRecvBuf, bytesRead, MAX_UDP_PACKET_SIZE - bytesRead);
                    bytesRead = readcount;
                }

                int bytesToRead = getFrameLength(mRecvBuf, 0) + 4;
                if (bytesToRead < 0 || bytesToRead > MAX_UDP_PACKET_SIZE) {
                    break;
                }
                Log.i(TAG, "bytesToRead " + bytesToRead);

                while (bytesRead < bytesToRead) {
                    readcount = inputStream.read(mRecvBuf, bytesRead, bytesToRead - bytesRead);
                    bytesRead += readcount;
                }

                mTotalRecvBytes += bytesToRead - 4;
                Log.i(TAG, "bytesRead " + bytesRead + " mTotalRecvBytes " + mTotalRecvBytes);
                if (mDataReceivedListener != null) {
                    ByteBuffer byteBuffer = ByteBuffer.wrap(mRecvBuf, 4, bytesToRead - 4);
                    mDataReceivedListener.onDataReceived(byteBuffer);

                    if (bytesRead > bytesToRead) {
                        System.arraycopy(mRecvBuf, bytesToRead, mTmpSwapBuf, 0, bytesRead - bytesToRead);
                        System.arraycopy(mTmpSwapBuf, 0, mRecvBuf, 0, bytesRead - bytesToRead);

                        bytesRead = bytesRead - bytesToRead;
                        remainBytes = true;
                    } else {
                        bytesRead = 0;
                        remainBytes = false;
                    }
                }
            }
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void requestVideoData(String videoServerAddr, int serverPort) {
        try {
            InetAddress addr = InetAddress.getByName(videoServerAddr);
            final SocketAddress socketAddress = new InetSocketAddress(addr, serverPort);
            new Thread() {
                @Override
                public void run() {
                    while (!mStop) {
                        try {
                            mTotalRecvBytes = 0;
                            connectAndRequestVideo(socketAddress);
                        } catch (IOException e) {
                            e.printStackTrace();
                            if (!recover(e)) {
                                mStop = true;
                            }
                        }
                    }
                }
            }.start();
        } catch (UnknownHostException e) {
        }
    }

    private void connectAndRequestStream(SocketAddress socketAddress) throws IOException {
        Socket client = null;
        try {
            client = new Socket();
            client.setSoTimeout(0);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        client.connect(socketAddress);

        Message.MessageHdr msgheader = new Message.MessageHdr();
        msgheader.message_type = Message.MessageTypeDeviceRegistraction;
        msgheader.message_data_length = 0;

        ByteBuffer sendbuffer = ByteBuffer.allocate(16);
        sendbuffer.order(ByteOrder.BIG_ENDIAN);
        sendbuffer.putInt(msgheader.message_type);
        sendbuffer.putInt(msgheader.message_data_length);

        sendbuffer.flip();
        byte[] sendBuf = new byte[8];
        sendbuffer.get(sendBuf);

        OutputStream outputStream = null;
        InputStream inputStream = null;

        outputStream = client.getOutputStream();
        outputStream.write(sendBuf);

        inputStream = client.getInputStream();

        byte[] msgRecvBuf = new byte[4096];

        try {
            while (!mStop) {
                int readcount = inputStream.read(msgRecvBuf, 0, 8);
                ByteBuffer bb = ByteBuffer.wrap(msgRecvBuf, 0, 8);
                bb.order(ByteOrder.BIG_ENDIAN);

                msgheader.message_type = bb.getInt();
                msgheader.message_data_length = bb.getInt();

                if (msgheader.message_type == Message.MessageTypeVideoServerInfo) {
                    readcount = inputStream.read(msgRecvBuf, 0, msgheader.message_data_length);
                    ByteArrayInputStream bais = new ByteArrayInputStream(msgRecvBuf, 0, msgheader.message_data_length);
                    Controlmsg.VideoServerInfo videoServerInfo = Controlmsg.VideoServerInfo.parseFrom(bais);
                    String videoServerAddr = videoServerInfo.getServeraddr();
                    int serverPort = videoServerInfo.getServerport();
                    requestVideoData(videoServerAddr, serverPort);
                }
            }
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private boolean recover(IOException e) {
        boolean recover = false;
        Throwable cause = e.getCause();
        if (cause instanceof ErrnoException) {
            ErrnoException errnoException = (ErrnoException) cause;
            if (errnoException.errno == OsConstants.ECONNRESET) {
                recover = true;
            }
        }
        return recover;
    }

    public void requestStreamData(String serverAddr, int port) {
        try {
            InetAddress addr = InetAddress.getByName(serverAddr);
            SocketAddress socketAddress = new InetSocketAddress(addr, port);
            while (!mStop) {
                try {
                    mTotalRecvBytes = 0;
                    connectAndRequestStream(socketAddress);
                } catch (IOException e) {
                    e.printStackTrace();
                    if (!recover(e)) {
                        mStop = true;
                    }
                }
            }
        } catch (UnknownHostException e) {
        }
    }

    private int getFrameLength(byte[] data, int offset) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, 4);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.asIntBuffer();
        int frameLength = bb.getInt();

        return frameLength;
    }

    public interface StreamReceivedListener {
        void onDataReceived(ByteBuffer byteBuffer);
    }

}
