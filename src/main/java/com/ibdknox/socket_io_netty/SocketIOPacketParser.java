package com.ibdknox.socket_io_netty;

import static com.ibdknox.socket_io_netty.SocketIOPacketSerializer.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class SocketIOPacketParser {
    // Multi-packet
    // �46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}�46�5:::{"name":"news","args":[{"hello":"world"}]}

    // Single packet
    // 5:::{"name":"xxx","args":[{"yyy":1}]}
    
    private static Pattern parserPattern = Pattern.compile("([^:]+):([0-9]+)?(\\+)?:([^:]+)?:?([\\s\\S]*)?");

    public static List<SocketIOPacket> parse(String packet) {

        if(packet.startsWith(BOUNDARY)) {
            List<SocketIOPacket> rv = new LinkedList<SocketIOPacket>();
            
            do {
                int packLen, nextBoundary;

                nextBoundary = packet.indexOf(BOUNDARY, 1);
                packLen = Integer.valueOf(packet.substring(1, nextBoundary));
                rv.add(parseSingle(packet.substring(nextBoundary + 1, nextBoundary + 1 + packLen)));
                packet = packet.substring(nextBoundary + 1 + packLen);
            } while (packet.startsWith(BOUNDARY));

            return rv;
        }

        SocketIOPacket p = parseSingle(packet);
        if(p != null) {
            List<SocketIOPacket> retval = new ArrayList<SocketIOPacket>();
            retval.add(p);
        
            return retval;
        }
        else
            return Collections.emptyList();
    }
    
    private static SocketIOPacket parseSingle(String single) {
        Matcher pieces = parserPattern.matcher(single);
        if(!pieces.matches())
            return null;

        String data = pieces.group(5);
        SocketIOPacketType type = SocketIOPacketType.byNumber(
                Integer.valueOf(
                        pieces.group(1)));

        SocketIOPacket.Builder sioPacketBuilder = new SocketIOPacket.Builder(type);

        switch (type) {
            case ERROR:
            case MESSAGE:
            case ACK:
            case EVENT:
            case JSON:
                sioPacketBuilder.setData(data);
                break;

            case DISCONNECT:
            case CONNECT:
            case HEARTBEAT:
                break;
        }

        return sioPacketBuilder.build();
    }
    
}
