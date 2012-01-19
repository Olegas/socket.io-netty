package com.ibdknox.socket_io_netty;

import java.util.List;


class SocketIOPacketSerializer {
    
    public static final String BOUNDARY = "\ufffd";

    // �46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}

    static String serialize(List<SocketIOPacket> packets) {
        if(packets.size() == 0)
            return "";

        if(packets.size() == 1)
            return serialize(packets.get(0));

        StringBuilder builder = new StringBuilder();
        for(SocketIOPacket packet : packets) {
            String s = serialize(packet);
            builder.append(BOUNDARY).append(s.length()).append(BOUNDARY).append(s);
        }
        return builder.toString();
    }

    static String serialize(SocketIOPacket packet) {
        return packet.toString();
    }

}
