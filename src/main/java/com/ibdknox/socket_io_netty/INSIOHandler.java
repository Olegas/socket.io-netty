package com.ibdknox.socket_io_netty;

public interface INSIOHandler {
    void onConnect(INSIOClient ws);
    void onMessage(INSIOClient ws, SocketIOPacket message);
    void onDisconnect(INSIOClient ws);
    void onShutdown();
}
