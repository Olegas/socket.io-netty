package com.ibdknox.socket_io_netty;

public enum SocketIOProtocol {

    WEBSOCKET("websocket"), XHRPOLLING("xhr-polling");

    private final String name;

    SocketIOProtocol(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
    
}
