package com.ibdknox.socket_io_netty;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;

import java.util.List;

public abstract class GenericIOClient implements INSIOClient {

    protected ChannelHandlerContext ctx;
    protected String uID;
    protected boolean open = false;

    public GenericIOClient(ChannelHandlerContext ctx, String uID) {
        this.ctx = ctx;
        this.uID = uID;
        this.open = true;
    }

    @Override
    public void sendPacket(SocketIOPacket packet) {
        sendUnencoded(SocketIOPacketSerializer.serialize(packet));
    }

    @Override
    public void sendPackets(List<SocketIOPacket> packets) {
        if(packets.size() == 0)
            return;
        if(packets.size() == 1) {
            sendPacket(packets.get(0));
            return;
        }
        sendUnencoded(SocketIOPacketSerializer.serialize(packets));
    }

    abstract public void keepAlive();



    @Override
    public ChannelHandlerContext getCTX() {
        return ctx;
    }

    @Override
    public String getSessionID() {
        return uID;
    }

    @Override
    public void disconnect() {
        // TODO need to send DISCONNECT?
        Channel chan = ctx.getChannel();
        if(chan.isOpen()) {
           chan.close();
        }
        this.open = false;
    }

    abstract boolean sendUnencoded(String message);
}
