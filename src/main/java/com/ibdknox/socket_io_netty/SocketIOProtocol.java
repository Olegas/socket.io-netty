package com.ibdknox.socket_io_netty;

public enum SocketIOProtocol {

    FLASHSOCKET("flashsocket", true), WEBSOCKET("websocket", true), XHRPOLLING("xhr-polling", true);

    private final String name;
    private boolean enabled;

    SocketIOProtocol(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return name;
    }
    
}
