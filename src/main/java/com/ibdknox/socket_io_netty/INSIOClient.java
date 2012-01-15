package com.ibdknox.socket_io_netty;

import org.jboss.netty.channel.ChannelHandlerContext;

import java.util.List;

public interface INSIOClient {

    void sendPacket(SocketIOPacket packet);
    void sendPackets(List<SocketIOPacket> packets);
    /*boolean isAlive(int beat);
    void heartbeat();*/
    void disconnect();
    String getSessionID();
    ChannelHandlerContext getCTX();
}
