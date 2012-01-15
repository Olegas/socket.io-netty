package com.ibdknox.socket_io_netty;

import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Values.*;
import static org.jboss.netty.handler.codec.http.HttpMethod.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

import java.security.MessageDigest;
import java.util.List;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameDecoder;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameEncoder;
import org.jboss.netty.util.CharsetUtil;

public class WebSocketServerHandler extends SimpleChannelUpstreamHandler {

    private static final long HEARTBEAT_RATE = 10000;
    private static final String SOCKETIO_PREFIX = "/socket.io/1";
    private static final String WEBSOCKET_PATH = "/websocket";
    private static final String POLLING_PATH = "/xhr-polling";
    private static final String FLASHSOCKET_PATH = "/flashsocket";
    private static final String HANDSHAKE_PATH_V1 = "/";
    // TODO use as array
    private static final String SUPPORTED_PROTOCOLS = "xhr-polling";

    private INSIOHandler handler;
    private Timer heartbeatTimer;
    private HeartbeatTask heartbeatTask;
    ConcurrentHashMap<String, INSIOClient> clients;
    //ConcurrentHashMap<String, PollingIOClient> pollingClients;


    public WebSocketServerHandler(INSIOHandler handler) {
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
        return clients.get(ctx.getAttachment());
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

    private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req) throws Exception {

        String reqURI = req.getUri();
        String stage;
        
        // TODO need to close connection?
        if(!reqURI.startsWith(SOCKETIO_PREFIX))
            return;

        stage = reqURI.substring(SOCKETIO_PREFIX.length());
        int hasParam = stage.indexOf("?");
        if(hasParam > 0)
            stage = stage.substring(0, hasParam);

        // TODO remove supported protocols hardcoding
        // TODO add flash support detection (is flash policy server runned?)
        if(HANDSHAKE_PATH_V1.equals(stage)) {
            StringBuilder b = new StringBuilder();
            b.append(getUniqueID()).append(":")
             .append(HEARTBEAT_RATE / 1000).append(":")
             .append(25).append(":")
             .append(SUPPORTED_PROTOCOLS);

            setKeepAlive(req, false);
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContent(ChannelBuffers.copiedBuffer(b.toString(), CharsetUtil.US_ASCII));
            sendHttpResponse(ctx, req, response);
            return;
        }

        String ID = extractID(stage);
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

        // Serve the WebSocket handshake request.
        String location = "";
        if(stage.startsWith(WEBSOCKET_PATH)) {
            location = getWebSocketLocation(req, ID);
        } else if(stage.startsWith(FLASHSOCKET_PATH)) {
            location = getFlashSocketLocation(req, ID);
        }
        String connectionHeader = req.getHeader(CONNECTION);
        if (location != "" && connectionHeader != null && connectionHeader.contains(Values.UPGRADE)
                && WEBSOCKET.equalsIgnoreCase(req.getHeader(Names.UPGRADE))) {

            // Create the WebSocket handshake response.
            HttpResponse res = new DefaultHttpResponse(
                    HTTP_1_1,
                    new HttpResponseStatus(101, "Web Socket Protocol Handshake"));
            res.addHeader(Names.UPGRADE, WEBSOCKET);
            res.addHeader(CONNECTION, Values.UPGRADE);

            // Fill in the headers and contents depending on handshake method.
            if (req.containsHeader(SEC_WEBSOCKET_KEY1) &&
                    req.containsHeader(SEC_WEBSOCKET_KEY2)) {
                // New handshake method with a challenge:
                res.addHeader(SEC_WEBSOCKET_ORIGIN, req.getHeader(ORIGIN));
                res.addHeader(SEC_WEBSOCKET_LOCATION, getWebSocketLocation(req, ID));
                String protocol = req.getHeader(SEC_WEBSOCKET_PROTOCOL);
                if (protocol != null) {
                    res.addHeader(SEC_WEBSOCKET_PROTOCOL, protocol);
                }

                // Calculate the answer of the challenge.
                String key1 = req.getHeader(SEC_WEBSOCKET_KEY1);
                String key2 = req.getHeader(SEC_WEBSOCKET_KEY2);
                int a = (int) (Long.parseLong(key1.replaceAll("[^0-9]", "")) / key1.replaceAll("[^ ]", "").length());
                int b = (int) (Long.parseLong(key2.replaceAll("[^0-9]", "")) / key2.replaceAll("[^ ]", "").length());
                long c = req.getContent().readLong();
                ChannelBuffer input = ChannelBuffers.buffer(16);
                input.writeInt(a);
                input.writeInt(b);
                input.writeLong(c);
                ChannelBuffer output = ChannelBuffers.wrappedBuffer(
                        MessageDigest.getInstance("MD5").digest(input.array()));
                res.setContent(output);
            } else {
                // Old handshake method with no challenge:
                String origin = req.getHeader(ORIGIN);
                if(origin != null)
                    res.addHeader(WEBSOCKET_ORIGIN, origin);
                res.addHeader(WEBSOCKET_LOCATION, getWebSocketLocation(req, ID));
                String protocol = req.getHeader(WEBSOCKET_PROTOCOL);
                if (protocol != null) {
                    res.addHeader(WEBSOCKET_PROTOCOL, protocol);
                }
            }

            // Upgrade the connection and send the handshake response.
            ChannelPipeline p = ctx.getChannel().getPipeline();

            p.remove("aggregator");
            p.replace("decoder", "wsdecoder", new WebSocketFrameDecoder());

            ctx.getChannel().write(res);

            p.replace("encoder", "wsencoder", new WebSocketFrameEncoder());

            connectSocket(ID, ctx);
            return;
        }

        // Send an error page otherwise.
        sendHttpResponse(
                ctx, req, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
    }

    private PollingIOClient connectPoller(String uID, ChannelHandlerContext ctx) {
        PollingIOClient client = new PollingIOClient(ctx, uID);
        connectClient(uID, client);
        return client;
    }

    private void connectSocket(String uID, ChannelHandlerContext ctx) {
        connectClient(uID, new WebSocketIOClient(ctx, uID));
    }
    
    private void connectClient(String uID, INSIOClient client) {
        client.sendPacket(SocketIOPacket.CONNECT);
        clients.put(uID, client);
        handler.onConnect(client);
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        INSIOClient client = getClientByCTX(ctx);
        handleMessages(client, SocketIOPacketParser.parse(frame.getTextData()));
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
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        throws Exception {
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
