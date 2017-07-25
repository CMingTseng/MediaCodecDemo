package com.wolfcstech.mediacodecdemo;

/**
 * Created by hanpfei0306 on 17-7-25.
 */

public class Message {
    public static final int VideoRoleProducer = 0;
    public static final int VideoRoleConsumer = 1;

    public static final int MessageTypeDeviceRegistraction = 0;
    public static final int MessageTypeVideoServerInfo = 1;
    public static final int MessageTypeVideoRoleRegistraction = 2;

    public static class MessageHdr{
        int message_type;
        int message_data_length;
    }
}
