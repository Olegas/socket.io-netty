package com.ibdknox.socket_io_netty;

import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpMethod.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

import java.util.List;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.websocketx.*;
import org.jboss.netty.util.CharsetUtil;

class WebSocketServerHandler extends SimpleChannelUpstreamHandler {

    private static final long HEARTBEAT_RATE = 10000;
    private static final String SOCKETIO_PREFIX = "/socket.io/1";
    private static final String WEBSOCKET_PATH = "/websocket";
    private static final String POLLING_PATH = "/xhr-polling";
    private static final String FLASHSOCKET_PATH = "/flashsocket";
    private static final String HANDSHAKE_PATH_V1 = "/";
    
    private WebSocketServerHandshaker handshaker;

    private INSIOHandler handler;
    private Timer heartbeatTimer;
    private HeartbeatTask heartbeatTask;
    ConcurrentHashMap<String, INSIOClient> clients;
    //ConcurrentHashMap<String, PollingIOClient> pollingClients;


    WebSocketServerHandler(INSIOHandler handler) {
        super();
        this.clients = new ConcurrentHashMap<String, INSIOClient>(20000, 0.75f, 2);
        //this.pollingClients = new ConcurrentHashMap<String, PollingIOClient>(20000, 0.75f, 2);
        this.handler = handler;
        this.heartbeatTimer = new Timer();
        heartbeatTask = new HeartbeatTask(this);
        heartbeatTimer.schedule(heartbeatTask, 1000, HEARTBEAT_RATE);
    }


    private String getUniqueID() {
        return UUID.randomUUID().toString();
    }

    private INSIOClient getClientByCTX(ChannelHandlerContext ctx) {
        String attachment = (String)ctx.getAttachment();
        if(attachment != null)
            return clients.get(attachment);
        else
            return null;
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, org.jboss.netty.channel.ChannelStateEvent e) throws Exception {
        INSIOClient client = getClientByCTX(ctx);
        if(client != null) {
            disconnect(client);
        }
    }

    void disconnect(INSIOClient client) {
        client.disconnect();
        clients.remove(client.getSessionID());
        handler.onDisconnect(client);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof HttpRequest) {
            handleHttpRequest(ctx, (HttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    private void handleHttpRequest(final ChannelHandlerContext ctx, HttpRequest req) throws Exception {

        String reqURI = req.getUri();
        String stage;
        
        // TODO need to close connection?
        if(!reqURI.startsWith(SOCKETIO_PREFIX))
            return;

        stage = reqURI.substring(SOCKETIO_PREFIX.length());
        int hasParam = stage.indexOf("?");
        if(hasParam > 0)
            stage = stage.substring(0, hasParam);

        if(HANDSHAKE_PATH_V1.equals(stage)) {
            StringBuilder b = new StringBuilder();
            b.append(getUniqueID()).append(":")
             .append(HEARTBEAT_RATE / 1000).append(":")
             // TODO remove magic number
             .append(25).append(":");

            for(SocketIOProtocol proto : SocketIOProtocol.values()) {
                if(proto.isEnabled())
                    b.append(proto.toString()).append(",");
            }
            
            b.deleteCharAt(b.length() - 1); // Remove last colon

            setKeepAlive(req, false);
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContent(ChannelBuffers.copiedBuffer(b.toString(), CharsetUtil.US_ASCII));
            sendHttpResponse(ctx, req, response);
            return;
        }

        final String ID = extractID(stage);
        // This is a poller client
        if(stage.startsWith(POLLING_PATH)) {
            PollingIOClient client = (PollingIOClient) this.clients.get(ID);

            if(client == null) {
                //new client
                client = connectPoller(ID, ctx);
                client.reconnect(ctx, req); // TODO poller client always has different context
                return;
            }

            if(req.getMethod() == GET) {
                heartbeatTask.notifyAlive(client);
                client.reconnect(ctx, req);
            } else {
                //we got a message
                handleMessages(client, SocketIOPacketParser.parse(req.getContent().toString(CharsetUtil.UTF_8)));

                //make sure the connection is closed once we send a response
                setKeepAlive(req, false);

                //send a response that allows for cross domain access
                HttpResponse resp = new DefaultHttpResponse(HTTP_1_1, OK);
                resp.addHeader("Access-Control-Allow-Origin", "*");
                sendHttpResponse(ctx, req, resp);
            }
            return;
        }

        if(stage.startsWith(WEBSOCKET_PATH) || stage.startsWith(FLASHSOCKET_PATH)) {

            // Not handled by poller - this is a websocket client
            WebSocketServerHandshakerFactory wsFactory =
                    new WebSocketServerHandshakerFactory(
                            this.getWebSocketLocation(req, ID),
                            null,
                            false);
            this.handshaker = wsFactory.newHandshaker(req);
            if (this.handshaker == null) {
                wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel());
            } else {
                this.handshaker.handshake(ctx.getChannel(), req).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        connectSocket(ID, ctx);
                    }
                });
            }
        } else {
            // Send an error page otherwise.
            sendHttpResponse(
                    ctx, req, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
        }
    }

    private PollingIOClient connectPoller(String uID, ChannelHandlerContext ctx) {
        PollingIOClient client = new PollingIOClient(ctx, uID);
        connectClient(uID, client);
        return client;
    }

    private void connectSocket(String uID, ChannelHandlerContext ctx) {
        ctx.setAttachment(uID);
        connectClient(uID, new WebSocketIOClient(ctx, uID));
    }
    
    private void connectClient(String uID, INSIOClient client) {
        client.sendPacket(SocketIOPacket.CONNECT);
        clients.put(uID, client);
        heartbeatTask.notifyAlive(client);
        handler.onConnect(client);
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // Check for closing frame
        if (frame instanceof CloseWebSocketFrame) {
            this.handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
            return;
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.getChannel().write(new PongWebSocketFrame(frame.getBinaryData()));
            return;
        } else if (!(frame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(
                    String.format("%s frame types not supported", frame.getClass().getName()));
        }

        // Send the uppercase string back.
        String request = ((TextWebSocketFrame) frame).getText();
        INSIOClient client = getClientByCTX(ctx);
        handleMessages(client, SocketIOPacketParser.parse(request));
    }

    private void handleMessages(INSIOClient client, List<SocketIOPacket> messages) {
        for(SocketIOPacket message : messages)
            handleMessage(client, message);
    }

    private void handleMessage(INSIOClient client, SocketIOPacket message) {
        if(message.getType() == SocketIOPacketType.HEARTBEAT) {
            heartbeatTask.notifyAlive(client);
        } else {
            handler.onMessage(client, message);
        }
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        // Generate an error page if response status code is not OK (200).
        if (res.getStatus().getCode() != 200) {
            res.setContent(
                    ChannelBuffers.copiedBuffer(
                        res.getStatus().toString(), CharsetUtil.UTF_8));
            setContentLength(res, res.getContent().readableBytes());
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.getChannel().write(res);
        if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    void prepShutDown() {
        this.heartbeatTimer.cancel();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        e.getCause().printStackTrace();
        e.getChannel().close();
    }
    
    private String extractID(String stageUri) {
        String [] parts = stageUri.split("/");
        if(parts.length >= 2)
            return parts[2];
        else
            return "";
    }

    private String getWebSocketLocation(HttpRequest req, String ID) {
        return "ws://" + 
                req.getHeader(HttpHeaders.Names.HOST) + 
                SOCKETIO_PREFIX + 
                WEBSOCKET_PATH + 
                "/" + 
                ID;
    }

    private String getFlashSocketLocation(HttpRequest req, String ID) {
        return "ws://" + req.getHeader(HttpHeaders.Names.HOST) + SOCKETIO_PREFIX + FLASHSOCKET_PATH + "/" + ID;
    }
}
