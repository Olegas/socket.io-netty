package com.ibdknox.socket_io_netty;

public enum SocketIOPacketType {

    DISCONNECT(0), CONNECT(1), HEARTBEAT(2), MESSAGE(3), JSON(4), EVENT(5), ACK(6), ERROR(7), NOOP(8);

    private static final String[] names = new String[] {
            "DISCONNECT",
            "CONNECT",
            "HEARTBEAT",
            "MESSAGE",
            "JSON",
            "EVENT",
            "ACK",
            "ERROR",
            "NOOP"
    };
    
    private int type;

    static SocketIOPacketType byNumber(int number) {
        if(number < 0 || number > names.length)
            throw new IllegalArgumentException("Wrong type number");
        return SocketIOPacketType.valueOf(names[number]);
    }
    
    SocketIOPacketType(int type) {
        this.type = type;   
    }
    
    int getTypeCode() {
        return type;
    }


}
