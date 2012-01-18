package com.ibdknox.socket_io_netty;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.websocket.DefaultWebSocketFrame;


public class WebSocketIOClient extends GenericIOClient {

    public WebSocketIOClient(ChannelHandlerContext ctx, String uID) {
        super(ctx, uID);
    }

    @Override
    public boolean sendUnencoded(String message) {
        if(!this.open) return false;

        Channel chan = ctx.getChannel();
        if(chan.isOpen()) {
            chan.write(new DefaultWebSocketFrame(message));
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