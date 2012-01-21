package com.ibdknox.socket_io_netty;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;


public class SocketIOPacketParserTest {
    @Test
    public void testParseSinglePacket() throws Exception {
        List<SocketIOPacket> packet = SocketIOPacketParser.parse("5:::{\"name\":\"xxx\",\"args\":[{\"yyy\":1}]}");
        
        Assert.assertEquals(1, packet.size());
        Assert.assertEquals(SocketIOPacketType.EVENT, packet.get(0).getType());
        Assert.assertEquals("{\"name\":\"xxx\",\"args\":[{\"yyy\":1}]}", packet.get(0).getData());
    }
    
    @Test
    public void testParseMultiplePackets() {
        List<SocketIOPacket> packets = SocketIOPacketParser.parse("�47�5:::{\"name\":\"news1\",\"args\":[{\"hello\":\"world\"}]}�48�3:::{\"name\":\"news22\",\"args\":[{\"hello\":\"world\"}]}");
        
        Assert.assertEquals(2, packets.size());
        Assert.assertEquals(SocketIOPacketType.EVENT, packets.get(0).getType());
        Assert.assertEquals(SocketIOPacketType.MESSAGE, packets.get(1).getType());

        Assert.assertEquals("{\"name\":\"news1\",\"args\":[{\"hello\":\"world\"}]}", packets.get(0).getData());
        Assert.assertEquals("{\"name\":\"news22\",\"args\":[{\"hello\":\"world\"}]}", packets.get(1).getData());
    }
}
