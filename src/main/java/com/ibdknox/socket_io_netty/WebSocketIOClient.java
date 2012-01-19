package com.ibdknox.socket_io_netty;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;


public class WebSocketIOClient extends GenericIOClient {

    WebSocketIOClient(ChannelHandlerContext ctx, String uID) {
        super(ctx, uID);
    }

    @Override
    public boolean sendUnencoded(String message) {
        if(!this.open) return false;

        Channel chan = ctx.getChannel();
        if(chan.isOpen()) {
            chan.write(new TextWebSocketFrame(message));
            return true;
        } else {
            this.disconnect();
            return false;
        }
    }

    @Override
    public void keepAlive() {
        sendPacket(SocketIOPacket.HEARTBEAT);
    }

}