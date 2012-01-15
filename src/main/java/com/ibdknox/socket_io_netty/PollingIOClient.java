package com.ibdknox.socket_io_netty;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.util.CharsetUtil;

import java.util.LinkedList;
import java.util.List;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public class PollingIOClient extends GenericIOClient {

    private final List<SocketIOPacket> queue = new LinkedList<SocketIOPacket>();
    private HttpRequest req;
    private boolean connected;

    public PollingIOClient(ChannelHandlerContext ctx, String uID) {
        super(ctx, uID);
    }

    public void reconnect(ChannelHandlerContext ctx, HttpRequest req) {
        this.ctx = ctx;
        this.req = req;
        this.connected = true;
        sendCollectedPayload();
    }

    private void sendCollectedPayload() {
        if(!connected || queue.isEmpty()) return;
        //TODO: is this necessary to synchronize?
        synchronized(queue) {
            sendUnencoded(SocketIOPacketSerializer.serialize(queue));
            queue.clear();
        }
    }

    @Override
    public void sendPacket(SocketIOPacket packet) {
        queue.add(packet);
        sendCollectedPayload();
    }

    @Override
    public void sendPackets(List<SocketIOPacket> packets) {
        queue.addAll(packets);
        sendCollectedPayload();
    }

    @Override
    public void sendUnencoded(String message) {
        if(!this.open) return;

        HttpResponse res = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);

        res.addHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
        res.addHeader("Access-Control-Allow-Origin", "*");
        res.addHeader("Access-Control-Allow-Credentials", "true");
        res.addHeader(CONNECTION, "keep-alive");

        res.setContent(ChannelBuffers.copiedBuffer(message, CharsetUtil.UTF_8));
        setContentLength(res, res.getContent().readableBytes());

        // Send the response and close the connection if necessary.
        Channel chan = ctx.getChannel();
        if(chan.isOpen()) {
            ChannelFuture f = chan.write(res);
            if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
                f.addListener(ChannelFutureListener.CLOSE);
            }
        }

        this.connected = false;
    }

    @Override
    public void keepAlive() {
        if(connected) {
            sendUnencoded("");
        }
    }

}
