package com.ibdknox.socket_io_netty;


public class SocketIOPacket {
    
    public static final SocketIOPacket NOOP = new SocketIOPacket(SocketIOPacketType.NOOP);
    public static final SocketIOPacket CONNECT = new SocketIOPacket(SocketIOPacketType.CONNECT);
    public static final SocketIOPacket HEARTBEAT = new SocketIOPacket(SocketIOPacketType.HEARTBEAT);

    private SocketIOPacketType type;
    private String data = "";

    SocketIOPacket(SocketIOPacketType type) {
        this.type = type;
    }

    public SocketIOPacketType getType() {
        return type;
    }
    
    public String getData() {
        return data;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(type.getType()).append(":::").append(data).toString();
    }

    
    public static class Builder {

        private SocketIOPacket packet;

        public Builder(SocketIOPacketType type) {
            packet = new SocketIOPacket(type);
        }
        
        public Builder setData(String event) {
            this.packet.data = event;
            return this;
        }
        
        public SocketIOPacket build() {
            return packet;
        }
        
    }

}
