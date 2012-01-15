package com.ibdknox.socket_io_netty;

import java.util.List;


public class SocketIOPacketSerializer {
    
    public static final String BOUNDARY = "\ufffd";

    // �46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}

    public static String serialize(List<SocketIOPacket> packets) {
        StringBuilder builder = new StringBuilder();
        for(SocketIOPacket packet : packets) {
            String s = serialize(packet);
            builder.append(BOUNDARY).append(s.length()).append(BOUNDARY).append(s);
        }
        return builder.toString();
    }

    public static String serialize(SocketIOPacket packet) {
        return packet.toString();
    }

}
