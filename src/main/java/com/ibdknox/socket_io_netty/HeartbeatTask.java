package com.ibdknox.socket_io_netty;

import java.util.HashMap;
import java.util.TimerTask;

public class HeartbeatTask extends TimerTask {

    private WebSocketServerHandler server;
    private int heartbeatNum = 0;
    private HashMap<String, Integer> hearbeatRate = new HashMap<String, Integer>();

    public HeartbeatTask(WebSocketServerHandler server) {
        this.server = server;
    }
    
    void notifyAlive(INSIOClient client) {
        String id = client.getSessionID();
        Integer rate = hearbeatRate.get(id);
        if(rate == null)
            rate = 0;
        hearbeatRate.put(client.getSessionID(), ++rate);
    }

    // TODO implement
    boolean isAlive(INSIOClient client) {
        /*@Override
    public boolean isAlive(int beat) {
        if(!this.open) return false;

        int lastBeat = beat - 1;
        if(this.beat == 0 || this.beat > beat) {
            this.beat = beat;
        } else if(this.beat < lastBeat) {
            //we're 2 beats behind..
            return false;
        }
        return true;
    }*/
        return true;
    }

    @Override
    public void run() {
        if(server.clients.isEmpty()) return;

        heartbeatNum++;
        for(INSIOClient client : server.clients.values()) {
            if(isAlive(client))
                ((GenericIOClient)client).keepAlive();
            else {
                hearbeatRate.remove(client.getSessionID());
                server.disconnect(client);
            }
        }
    }
}
